package clre20.customDisc.data;

import java.util.Locale;

/** 自訂物品的類型：唱片或號角。 */
public enum SoundType {
    DISC("disc", "唱片"),
    HORN("horn", "號角");

    private final String id;
    private final String displayName;

    SoundType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static SoundType fromId(String id) {
        if (id == null) {
            return null;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        for (SoundType type : values()) {
            if (type.id.equals(lower)) {
                return type;
            }
        }
        return null;
    }
}
