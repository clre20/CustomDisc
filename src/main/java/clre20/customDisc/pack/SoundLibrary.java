package clre20.customDisc.pack;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.audio.OggInfo;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.CRC32;

/**
 * plugins/CustomDisc/sounds/ 資料夾裡的音檔清單。
 * 掃描（讀檔、解析 ogg）一律在背景執行緒做；同一時間只會有一次掃描，重複呼叫會共用結果。
 * 解析結果依「修改時間＋檔案大小」快取，檔案沒變就不會重新讀。
 */
public final class SoundLibrary {

    /** 一個音檔。path 是相對 sounds/ 的路徑（用 / 分隔），id 是資源包裡用的安全名稱。 */
    public record Entry(String path, File file, String id, OggInfo info, String error) {
        public boolean valid() {
            return info != null;
        }
    }

    private record CacheKey(long modified, long size) {
    }

    private static final String BAD_NAME = "sound-error.bad-name";

    /** 系統語系不是 UTF-8 時，中文檔名會被讀成 ? 或 U+FFFD，這種檔案打不開。 */
    private static boolean badName(String rel) {
        return rel.indexOf('�') >= 0 || rel.indexOf('?') >= 0;
    }

    private final CustomDisc plugin;
    private final Map<String, CacheKey> cacheKeys = new HashMap<>();
    private final Map<String, Entry> cache = new HashMap<>();
    private volatile List<Entry> entries = List.of();
    private CompletableFuture<Void> running;
    /** 掃描用的鎖（和 scanAsync 的鎖分開，主執行緒呼叫 scanAsync 不會被正在進行的掃描卡住）。 */
    private final Object scanLock = new Object();

    public SoundLibrary(CustomDisc plugin) {
        this.plugin = plugin;
    }

    public File folder() {
        return new File(plugin.getDataFolder(), "sounds");
    }

    /** 在背景重新掃描；已經在掃描時直接共用那一次的結果。 */
    public synchronized CompletableFuture<Void> scanAsync() {
        if (running != null && !running.isDone()) {
            return running;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        running = future;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                scan();
            } finally {
                future.complete(null);
            }
        });
        return future;
    }

    /** 立即掃描（只能在背景執行緒或啟動時呼叫）。 */
    public void scan() {
        synchronized (scanLock) {
            scanLocked();
        }
    }

    private void scanLocked() {
        File root = folder();
        if (!root.isDirectory() && !root.mkdirs()) {
            plugin.getLogger().warning(plugin.messages().console("folder-failed", "path", root.getPath()));
        }
        List<Entry> list = new ArrayList<>();
        walk(root, root, list);
        list.sort((a, b) -> a.path().compareToIgnoreCase(b.path()));
        entries = Collections.unmodifiableList(list);

        // 清掉已經不存在的檔案的快取
        Set<String> alive = new HashSet<>();
        for (Entry e : list) {
            alive.add(e.path());
        }
        cache.keySet().retainAll(alive);
        cacheKeys.keySet().retainAll(alive);
    }

    private void walk(File root, File dir, List<Entry> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                walk(root, f, out);
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                // 不用 toPath()：系統語系不是 UTF-8 時，中文檔名會讓 toPath() 直接丟出例外
                String rel = f.getPath().substring(root.getPath().length() + 1).replace(File.separatorChar, '/');
                try {
                    out.add(entry(rel, f));
                } catch (RuntimeException ex) {
                    out.add(new Entry(rel, f, idFor(rel), null, badName(rel) ? BAD_NAME : ex.getMessage()));
                }
            }
        }
    }

    private Entry entry(String rel, File f) {
        CacheKey key = new CacheKey(f.lastModified(), f.length());
        Entry cached = cache.get(rel);
        if (cached != null && key.equals(cacheKeys.get(rel))) {
            return cached;
        }
        Entry e;
        if (badName(rel)) {
            e = new Entry(rel, f, idFor(rel), null, BAD_NAME);
        } else {
            try {
                e = new Entry(rel, f, idFor(rel), OggInfo.read(f), null);
            } catch (IOException ex) {
                e = new Entry(rel, f, idFor(rel), null, ex.getMessage());
            }
        }
        cacheKeys.put(rel, key);
        cache.put(rel, e);
        return e;
    }

    /** 目前的清單（不會觸發掃描）。 */
    public List<Entry> entries() {
        return entries;
    }

    public Entry find(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        for (Entry e : entries) {
            if (e.path().equals(path)) {
                return e;
            }
        }
        return null;
    }

    /** 錯誤原因的顯示文字。 */
    public String errorText(Entry e) {
        String err = e.error() == null ? "" : e.error();
        if (err.startsWith("sound-error.")) {
            return plugin.messages().get(err);
        }
        return plugin.messages().get("sound-error.read-failed", "reason", err);
    }

    /**
     * 資源包路徑只能用 a-z 0-9 _ . -，檔名有中文或空白時改用雜湊值。
     */
    static String idFor(String rel) {
        String base = rel.toLowerCase(Locale.ROOT);
        if (base.endsWith(".ogg")) {
            base = base.substring(0, base.length() - 4);
        }
        String safe = base.replace('/', '_');
        if (safe.matches("[a-z0-9_.-]{1,48}")) {
            return safe;
        }
        CRC32 crc = new CRC32();
        crc.update(rel.getBytes(StandardCharsets.UTF_8));
        String ascii = safe.replaceAll("[^a-z0-9_]", "");
        if (ascii.length() > 24) {
            ascii = ascii.substring(0, 24);
        }
        return (ascii.isEmpty() ? "f" : ascii) + "_" + Long.toHexString(crc.getValue());
    }
}
