package clre20.customDisc.pack;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.util.McVersion;
import clre20.customDisc.util.Text;
import clre20.customDisc.util.Vanilla;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 原生模式：把自訂唱片寫成資料包（jukebox_song / instrument），放進主世界的 datapacks 資料夾，
 * 伺服器下次啟動時自動載入。
 *
 * <p>啟動時先讀一次「這次開機實際載入的內容」當快照，之後判斷每張唱片能不能用原生模式：
 * 註冊表裡有它、而且長度／範圍／比較器／冷卻都和快照一樣。改過這些值的唱片在重啟前改由插件播放。</p>
 *
 * <p>資料包只會新增或更新，已刪除唱片的檔案會保留，避免世界裡還留著的舊物品讀取失敗。</p>
 */
public final class DatapackService {

    public static final String NAMESPACE = "customdisc";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private record Snapshot(double length, int range, int comparator, int cooldown) {
    }

    private final CustomDisc plugin;
    private final Map<String, Snapshot> bootSongs = new HashMap<>();
    private final Map<String, Snapshot> bootInstruments = new HashMap<>();

    public DatapackService(CustomDisc plugin) {
        this.plugin = plugin;
    }

    /** 自訂樂器（號角原生模式）要 1.21.2 以上。 */
    public static boolean instrumentsSupported() {
        return McVersion.current().atLeast(1, 21, 2);
    }

    public File folder() {
        return new File(new File(levelRoot(), "datapacks"), NAMESPACE);
    }

    /**
     * 存檔根目錄（level.dat 所在的資料夾）。
     * 26.x 起主世界資料夾變成 &lt;存檔&gt;/dimensions/minecraft/overworld，資料包卻還是放在 &lt;存檔&gt;/datapacks，
     * 所以從世界資料夾往上找 level.dat。
     */
    private static File levelRoot() {
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        File worldFolder = main != null ? main.getWorldFolder() : new File(Bukkit.getWorldContainer(), "world");
        File dir = worldFolder.getAbsoluteFile();
        for (int i = 0; i < 5 && dir != null; i++) {
            if (new File(dir, "level.dat").isFile()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        // 全新的世界第一次啟動時 level.dat 可能還沒寫出來：找路徑裡的 dimensions 資料夾
        for (File d = worldFolder.getAbsoluteFile(); d != null; d = d.getParentFile()) {
            if (d.getName().equals("dimensions") && d.getParentFile() != null) {
                return d.getParentFile();
            }
        }
        return worldFolder;
    }

    private File songDir() {
        return new File(folder(), "data/" + NAMESPACE + "/jukebox_song");
    }

    private File instrumentDir() {
        return new File(folder(), "data/" + NAMESPACE + "/instrument");
    }

    /** 啟動時讀取這次開機載入的資料包內容（必須在 write() 之前呼叫）。 */
    public void snapshotBoot() {
        bootSongs.clear();
        bootInstruments.clear();
        readDir(songDir(), bootSongs, false);
        readDir(instrumentDir(), bootInstruments, true);
    }

    private void readDir(File dir, Map<String, Snapshot> out, boolean instrument) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            String name = f.getName().substring(0, f.getName().length() - 5);
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                int range = o.getAsJsonObject("sound_event").get("range").getAsInt();
                if (instrument) {
                    out.put(name, new Snapshot(0, range, 0, (int) Math.round(o.get("use_duration").getAsDouble())));
                } else {
                    out.put(name, new Snapshot(o.get("length_in_seconds").getAsDouble(), range,
                            o.get("comparator_output").getAsInt(), 0));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("讀取資料包檔案 " + f.getName() + " 失敗：" + e.getMessage());
            }
        }
    }

    /**
     * 寫入資料包。回傳 true 代表內容有變，需要重啟伺服器才會套用。
     */
    public boolean write() {
        boolean changed = false;
        try {
            File root = folder();
            if (!root.isDirectory() && !root.mkdirs()) {
                throw new IOException("無法建立 " + root);
            }
            changed |= writeIfChanged(new File(root, "pack.mcmeta"), packMeta());

            for (CustomSound s : plugin.store().all(SoundType.DISC)) {
                changed |= writeIfChanged(new File(songDir(), s.name() + ".json"), songJson(s));
            }
            if (instrumentsSupported()) {
                for (CustomSound s : plugin.store().all(SoundType.HORN)) {
                    changed |= writeIfChanged(new File(instrumentDir(), s.name() + ".json"), instrumentJson(s));
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("寫入資料包失敗：" + e.getMessage());
        }
        return changed;
    }

    private boolean writeIfChanged(File file, JsonElement json) throws IOException {
        String text = GSON.toJson(json);
        if (file.isFile() && Files.readString(file.toPath(), StandardCharsets.UTF_8).equals(text)) {
            return false;
        }
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("無法建立 " + parent);
        }
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        return true;
    }

    private JsonObject packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "CustomDisc 自訂唱片與號角（自動產生，請勿停用）");
        // 舊版用 pack_format / supported_formats，1.21.9 以上用 min_format / max_format，全部一起寫
        pack.addProperty("pack_format", 48);
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", 48);
        supported.addProperty("max_inclusive", 9999);
        pack.add("supported_formats", supported);
        pack.addProperty("min_format", 48);
        pack.addProperty("max_format", 9999);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private JsonObject soundEvent(CustomSound s) {
        JsonObject event = new JsonObject();
        event.addProperty("sound_id", NAMESPACE + ":" + s.soundEvent());
        event.addProperty("range", (float) s.range());
        return event;
    }

    private JsonElement description(CustomSound s) {
        return GsonComponentSerializer.gson().serializeToTree(Text.color(s.displayName()));
    }

    private JsonObject songJson(CustomSound s) {
        JsonObject o = new JsonObject();
        o.add("sound_event", soundEvent(s));
        o.add("description", description(s));
        o.addProperty("length_in_seconds", roundedLength(s));
        o.addProperty("comparator_output", effectiveComparator(s));
        return o;
    }

    private JsonObject instrumentJson(CustomSound s) {
        JsonObject o = new JsonObject();
        o.add("sound_event", soundEvent(s));
        o.addProperty("use_duration", (float) s.cooldown());
        o.addProperty("range", (float) s.range());
        o.add("description", description(s));
        return o;
    }

    private static double roundedLength(CustomSound s) {
        // 至少 1 秒，避免長度 0 的唱片
        return Math.max(1.0, Math.round(s.length() * 100.0) / 100.0);
    }

    public static int effectiveComparator(CustomSound s) {
        return s.comparator() != null ? s.comparator() : Vanilla.comparator(s.appearance());
    }

    /** 這張唱片／號角現在能不能用原生模式播放。 */
    public boolean isNative(CustomSound s) {
        if (!plugin.config().nativeMode() || s == null) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(NAMESPACE, s.name());
        try {
            if (s.type() == SoundType.DISC) {
                Snapshot snap = bootSongs.get(s.name());
                return snap != null
                        && Registry.JUKEBOX_SONG.get(key) != null
                        && Math.abs(snap.length() - roundedLength(s)) < 0.01
                        && snap.range() == s.range()
                        && snap.comparator() == effectiveComparator(s);
            }
            Snapshot snap = bootInstruments.get(s.name());
            return instrumentsSupported()
                    && snap != null
                    && Registry.INSTRUMENT.get(key) != null
                    && snap.range() == s.range()
                    && snap.cooldown() == s.cooldown();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 這張唱片在重啟後能不能變成原生模式（目前不是）。 */
    public boolean nativeAfterRestart(CustomSound s) {
        if (!plugin.config().nativeMode() || isNative(s)) {
            return false;
        }
        return s.type() == SoundType.DISC || instrumentsSupported();
    }

    public static NamespacedKey key(CustomSound s) {
        return new NamespacedKey(NAMESPACE, Objects.requireNonNull(s).name());
    }
}
