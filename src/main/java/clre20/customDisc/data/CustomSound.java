package clre20.customDisc.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 一張自訂唱片或一支自訂號角的資料（對應 data/&lt;類型&gt;/&lt;name&gt;.yml）。 */
public final class CustomSound {

    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]{1,32}$");

    private final SoundType type;
    private final String name;

    private String appearance;
    private String displayName;
    private List<String> lore = new ArrayList<>();
    private String soundFile;
    private double length;
    private boolean loop;
    private int volume;
    private int range;
    /** null = 沿用外觀唱片的原版值。 */
    private Integer comparator;
    private int cooldown;
    private int revision;

    public CustomSound(SoundType type, String name) {
        this.type = type;
        this.name = name;
    }

    public CustomSound copy() {
        CustomSound c = new CustomSound(type, name);
        c.appearance = appearance;
        c.displayName = displayName;
        c.lore = new ArrayList<>(lore);
        c.soundFile = soundFile;
        c.length = length;
        c.loop = loop;
        c.volume = volume;
        c.range = range;
        c.comparator = comparator;
        c.cooldown = cooldown;
        c.revision = revision;
        return c;
    }

    /** 物品上記錄的 ID，例如 disc:my_song。 */
    public String id() {
        return type.id() + ":" + name;
    }

    /** 資源包與資料包裡的聲音事件名稱（不含命名空間），例如 disc.my_song。 */
    public String soundEvent() {
        return type.id() + "." + name;
    }

    /** 冷卻最低值：音檔長度無條件進位到整秒。 */
    public int minCooldown() {
        return Math.max(1, (int) Math.ceil(length));
    }

    public SoundType type() { return type; }
    public String name() { return name; }

    public String appearance() { return appearance; }
    public void appearance(String v) { appearance = v; }

    public String displayName() { return displayName; }
    public void displayName(String v) { displayName = v; }

    public List<String> lore() { return lore; }
    public void lore(List<String> v) { lore = new ArrayList<>(v); }

    public String soundFile() { return soundFile; }
    public void soundFile(String v) { soundFile = v; }

    public double length() { return length; }
    public void length(double v) { length = v; }

    public boolean loop() { return loop; }
    public void loop(boolean v) { loop = v; }

    public int volume() { return volume; }
    public void volume(int v) { volume = Math.max(0, Math.min(100, v)); }

    public int range() { return range; }
    public void range(int v) { range = Math.max(1, Math.min(512, v)); }

    public Integer comparator() { return comparator; }
    public void comparator(Integer v) { comparator = v == null ? null : Math.max(0, Math.min(15, v)); }

    public int cooldown() { return cooldown; }
    public void cooldown(int v) { cooldown = Math.max(1, Math.min(3600, v)); }

    public int revision() { return revision; }
    public void revision(int v) { revision = v; }
}
