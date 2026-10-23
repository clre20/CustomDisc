package clre20.customDisc.data;

import clre20.customDisc.CustomDisc;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 讀寫 plugins/CustomDisc/data/&lt;類型&gt;/&lt;name&gt;.yml。 */
public final class SoundStore {

    private final CustomDisc plugin;
    private final Map<SoundType, Map<String, CustomSound>> sounds = new EnumMap<>(SoundType.class);
    /** 寫檔用的單一背景執行緒（依序寫入，不會同時開很多執行緒）。 */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CustomDisc-DataWriter");
        t.setDaemon(true);
        return t;
    });

    public SoundStore(CustomDisc plugin) {
        this.plugin = plugin;
        for (SoundType type : SoundType.values()) {
            sounds.put(type, new TreeMap<>());
        }
    }

    public File folder(SoundType type) {
        return new File(new File(plugin.getDataFolder(), "data"), type.id());
    }

    public void load() {
        // 先等還沒寫完的檔案，避免讀到舊資料
        try {
            writer.submit(() -> { }).get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        for (SoundType type : SoundType.values()) {
            Map<String, CustomSound> map = sounds.get(type);
            map.clear();
            File dir = folder(type);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                plugin.getLogger().warning(plugin.messages().console("folder-failed", "path", dir.getPath()));
                continue;
            }
            File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - 4);
                if (!CustomSound.NAME_PATTERN.matcher(name).matches()) {
                    plugin.getLogger().warning(plugin.messages().console("data-invalid-name", "file", file.getName()));
                    continue;
                }
                try {
                    map.put(name, read(type, name, YamlConfiguration.loadConfiguration(file)));
                } catch (Exception e) {
                    plugin.getLogger().warning(plugin.messages().console("data-read-failed", "file", file.getName(),
                            "reason", e.getMessage()));
                }
            }
        }
    }

    private CustomSound read(SoundType type, String name, YamlConfiguration y) {
        CustomSound s = new CustomSound(type, name);
        s.appearance(y.getString("appearance", type == SoundType.DISC ? "MUSIC_DISC_CAT" : "PONDER"));
        s.displayName(y.getString("display-name", name));
        s.lore(y.getStringList("lore"));
        s.soundFile(y.getString("sound-file", ""));
        s.length(y.getDouble("length", 0));
        s.volume(y.getInt("volume", 100));
        s.range(y.getInt("range", type == SoundType.DISC ? 64 : 256));
        s.revision(y.getInt("revision", 1));
        if (type == SoundType.DISC) {
            s.loop(y.getBoolean("loop", false));
            Object comp = y.get("comparator");
            if (comp instanceof Number n) {
                s.comparator(n.intValue());
            } else if (comp instanceof String str && str.matches("\\d+")) {
                s.comparator(Integer.parseInt(str));
            } else {
                s.comparator(null);
            }
        } else {
            s.cooldown(y.getInt("cooldown", 7));
            if (s.cooldown() < s.minCooldown()) {
                s.cooldown(s.minCooldown());
            }
        }
        return s;
    }

    /**
     * 儲存（revision 會加 1，已發出去的物品靠它判斷要不要更新）。
     * 記憶體裡的資料立即更新；寫檔交給單一背景執行緒依序處理，不佔用主執行緒。
     */
    public void save(CustomSound s) {
        CustomSound old = get(s.type(), s.name());
        s.revision((old == null ? s.revision() : Math.max(old.revision(), s.revision())) + 1);
        sounds.get(s.type()).put(s.name(), s.copy());

        String text = toYaml(s).saveToString();
        File dir = folder(s.type());
        File file = new File(dir, s.name() + ".yml");
        writer.execute(() -> {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException(plugin.messages().console("folder-failed", "path", dir.getPath()));
                }
                Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning(plugin.messages().console("data-save-failed", "file", file.getName(),
                        "reason", e.getMessage()));
            }
        });
    }

    private YamlConfiguration toYaml(CustomSound s) {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("CustomDisc " + s.type().id() + ": " + s.name(),
                "/cd edit " + s.type().id() + " " + s.name()));
        y.set("type", s.type().id());
        y.set("appearance", s.appearance());
        y.set("display-name", s.displayName());
        y.set("lore", s.lore());
        y.set("sound-file", s.soundFile());
        y.set("length", Math.round(s.length() * 100.0) / 100.0);
        if (s.type() == SoundType.DISC) {
            y.set("loop", s.loop());
        } else {
            y.set("cooldown", s.cooldown());
        }
        y.set("volume", s.volume());
        y.set("range", s.range());
        if (s.type() == SoundType.DISC) {
            y.set("comparator", s.comparator() == null ? "default" : s.comparator());
        }
        y.set("revision", s.revision());
        return y;
    }

    /** 刪除（記憶體立即移除，檔案在背景刪除）。 */
    public boolean delete(SoundType type, String name) {
        CustomSound removed = sounds.get(type).remove(name);
        File file = new File(folder(type), name + ".yml");
        writer.execute(() -> {
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning(plugin.messages().console("data-save-failed", "file", file.getName(),
                        "reason", "delete"));
            }
        });
        return removed != null;
    }

    /** 關服時等待還沒寫完的檔案。 */
    public void flush() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public CustomSound get(SoundType type, String name) {
        CustomSound s = sounds.get(type).get(name);
        return s;
    }

    public boolean exists(SoundType type, String name) {
        return sounds.get(type).containsKey(name);
    }

    public Collection<CustomSound> all(SoundType type) {
        return Collections.unmodifiableCollection(sounds.get(type).values());
    }

    public List<CustomSound> all() {
        List<CustomSound> list = new ArrayList<>();
        for (SoundType type : SoundType.values()) {
            list.addAll(sounds.get(type).values());
        }
        return list;
    }

    public List<String> names(SoundType type) {
        return new ArrayList<>(sounds.get(type).keySet());
    }
}
