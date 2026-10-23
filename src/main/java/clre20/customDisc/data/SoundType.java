package clre20.customDisc.data;

import java.util.Locale;

/** 自訂物品的類型：唱片或號角。 */
public enum SoundType {
    DISC("disc"),
    HORN("horn");

    private final String id;

    SoundType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** 顯示名稱（messages.yml 的 common.type-disc / type-horn）。 */
    public String displayName() {
        return clre20.customDisc.CustomDisc.get().messages().get("common.type-" + id);
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
