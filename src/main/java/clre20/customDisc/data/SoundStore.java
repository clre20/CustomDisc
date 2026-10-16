package clre20.customDisc.data;

import clre20.customDisc.CustomDisc;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** 讀寫 plugins/CustomDisc/data/&lt;類型&gt;/&lt;name&gt;.yml。 */
public final class SoundStore {

    private final CustomDisc plugin;
    private final Map<SoundType, Map<String, CustomSound>> sounds = new EnumMap<>(SoundType.class);

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
        for (SoundType type : SoundType.values()) {
            Map<String, CustomSound> map = sounds.get(type);
            map.clear();
            File dir = folder(type);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                plugin.getLogger().warning("無法建立資料夾：" + dir);
                continue;
            }
            File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - 4);
                if (!CustomSound.NAME_PATTERN.matcher(name).matches()) {
                    plugin.getLogger().warning("略過名稱不合法的檔案：" + file.getName() + "（只能用 a-z、0-9、_）");
                    continue;
                }
                try {
                    map.put(name, read(type, name, YamlConfiguration.loadConfiguration(file)));
                } catch (Exception e) {
                    plugin.getLogger().warning("讀取 " + file.getName() + " 失敗：" + e.getMessage());
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

    /** 儲存（revision 會加 1，已發出去的物品靠它判斷要不要更新）。 */
    public void save(CustomSound s) throws IOException {
        CustomSound old = get(s.type(), s.name());
        s.revision((old == null ? s.revision() : Math.max(old.revision(), s.revision())) + 1);

        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "CustomDisc " + s.type().displayName() + "：" + s.name(),
                "可以用 /cd edit " + s.type().id() + " " + s.name() + " 在遊戲裡編輯"));
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

        File dir = folder(s.type());
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("無法建立資料夾 " + dir);
        }
        y.save(new File(dir, s.name() + ".yml"));
        sounds.get(s.type()).put(s.name(), s.copy());
    }

    public boolean delete(SoundType type, String name) {
        CustomSound removed = sounds.get(type).remove(name);
        File file = new File(folder(type), name + ".yml");
        boolean deleted = file.exists() && file.delete();
        return removed != null || deleted;
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
