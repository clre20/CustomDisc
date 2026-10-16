package clre20.customDisc.util;

import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 原版唱片、山羊角的資料。 */
public final class Vanilla {

    /** 原版唱片放進唱片機時的比較器強度。 */
    private static final Map<String, Integer> COMPARATOR = Map.ofEntries(
            Map.entry("MUSIC_DISC_13", 1),
            Map.entry("MUSIC_DISC_CAT", 2),
            Map.entry("MUSIC_DISC_BLOCKS", 3),
            Map.entry("MUSIC_DISC_CHIRP", 4),
            Map.entry("MUSIC_DISC_FAR", 5),
            Map.entry("MUSIC_DISC_MALL", 6),
            Map.entry("MUSIC_DISC_MELLOHI", 7),
            Map.entry("MUSIC_DISC_STAL", 8),
            Map.entry("MUSIC_DISC_STRAD", 9),
            Map.entry("MUSIC_DISC_WARD", 10),
            Map.entry("MUSIC_DISC_11", 11),
            Map.entry("MUSIC_DISC_WAIT", 12),
            Map.entry("MUSIC_DISC_PIGSTEP", 13),
            Map.entry("MUSIC_DISC_OTHERSIDE", 14),
            Map.entry("MUSIC_DISC_5", 15),
            Map.entry("MUSIC_DISC_RELIC", 14),
            Map.entry("MUSIC_DISC_PRECIPICE", 13),
            Map.entry("MUSIC_DISC_CREATOR", 12),
            Map.entry("MUSIC_DISC_CREATOR_MUSIC_BOX", 11),
            Map.entry("MUSIC_DISC_TEARS", 10),
            Map.entry("MUSIC_DISC_LAVA_CHICKEN", 9));

    /** 唱片中文名稱（UI 顯示用）。 */
    private static final Map<String, String> DISC_NAMES = Map.ofEntries(
            Map.entry("MUSIC_DISC_13", "13"),
            Map.entry("MUSIC_DISC_CAT", "cat"),
            Map.entry("MUSIC_DISC_BLOCKS", "blocks"),
            Map.entry("MUSIC_DISC_CHIRP", "chirp"),
            Map.entry("MUSIC_DISC_FAR", "far"),
            Map.entry("MUSIC_DISC_MALL", "mall"),
            Map.entry("MUSIC_DISC_MELLOHI", "mellohi"),
            Map.entry("MUSIC_DISC_STAL", "stal"),
            Map.entry("MUSIC_DISC_STRAD", "strad"),
            Map.entry("MUSIC_DISC_WARD", "ward"),
            Map.entry("MUSIC_DISC_11", "11"),
            Map.entry("MUSIC_DISC_WAIT", "wait"),
            Map.entry("MUSIC_DISC_PIGSTEP", "Pigstep"),
            Map.entry("MUSIC_DISC_OTHERSIDE", "otherside"),
            Map.entry("MUSIC_DISC_5", "5"),
            Map.entry("MUSIC_DISC_RELIC", "Relic"),
            Map.entry("MUSIC_DISC_PRECIPICE", "Precipice"),
            Map.entry("MUSIC_DISC_CREATOR", "Creator"),
            Map.entry("MUSIC_DISC_CREATOR_MUSIC_BOX", "Creator (Music Box)"),
            Map.entry("MUSIC_DISC_TEARS", "Tears"),
            Map.entry("MUSIC_DISC_LAVA_CHICKEN", "Lava Chicken"));

    /** 8 種原版山羊角。 */
    public static final List<String> HORNS = List.of(
            "PONDER", "SING", "SEEK", "FEEL", "ADMIRE", "CALL", "YEARN", "DREAM");

    private static final Map<String, String> HORN_NAMES = Map.of(
            "PONDER", "沉思（Ponder）",
            "SING", "歌唱（Sing）",
            "SEEK", "探索（Seek）",
            "FEEL", "感受（Feel）",
            "ADMIRE", "讚美（Admire）",
            "CALL", "呼喚（Call）",
            "YEARN", "渴望（Yearn）",
            "DREAM", "夢想（Dream）");

    private Vanilla() {
    }

    /** 創造模式物品欄裡的唱片順序。 */
    private static final List<String> DISC_ORDER = List.of(
            "MUSIC_DISC_13", "MUSIC_DISC_CAT", "MUSIC_DISC_BLOCKS", "MUSIC_DISC_CHIRP", "MUSIC_DISC_FAR",
            "MUSIC_DISC_MALL", "MUSIC_DISC_MELLOHI", "MUSIC_DISC_STAL", "MUSIC_DISC_STRAD", "MUSIC_DISC_WARD",
            "MUSIC_DISC_11", "MUSIC_DISC_WAIT", "MUSIC_DISC_OTHERSIDE", "MUSIC_DISC_5", "MUSIC_DISC_PIGSTEP",
            "MUSIC_DISC_RELIC", "MUSIC_DISC_PRECIPICE", "MUSIC_DISC_CREATOR", "MUSIC_DISC_CREATOR_MUSIC_BOX",
            "MUSIC_DISC_TEARS", "MUSIC_DISC_LAVA_CHICKEN");

    /** 這個伺服器版本有的所有原版唱片（新版加的唱片會自動出現在最後）。 */
    public static List<Material> discs() {
        List<Material> list = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.name().startsWith("MUSIC_DISC_") && m.isItem() && !isLegacy(m)) {
                list.add(m);
            }
        }
        list.sort((a, b) -> {
            int ia = DISC_ORDER.indexOf(a.name());
            int ib = DISC_ORDER.indexOf(b.name());
            return Integer.compare(ia < 0 ? 999 : ia, ib < 0 ? 999 : ib);
        });
        return list;
    }

    @SuppressWarnings("deprecation")
    private static boolean isLegacy(Material m) {
        return m.isLegacy();
    }

    public static boolean isDisc(Material m) {
        return m != null && m.name().startsWith("MUSIC_DISC_");
    }

    public static int comparator(String appearance) {
        return COMPARATOR.getOrDefault(appearance, 1);
    }

    public static String discName(String appearance) {
        return DISC_NAMES.getOrDefault(appearance,
                appearance.replace("MUSIC_DISC_", "").toLowerCase(Locale.ROOT));
    }

    public static String hornName(String appearance) {
        return HORN_NAMES.getOrDefault(appearance, appearance);
    }

    public static MusicInstrument instrument(String appearance) {
        MusicInstrument i = Registry.INSTRUMENT.get(
                NamespacedKey.minecraft(appearance.toLowerCase(Locale.ROOT) + "_goat_horn"));
        return i != null ? i : MusicInstrument.PONDER_GOAT_HORN;
    }

    public static Material material(String appearance) {
        Material m = Material.matchMaterial(appearance);
        return m != null && isDisc(m) ? m : Material.MUSIC_DISC_CAT;
    }

    /** 唱片機輪播能用的容器：單個箱子、陷阱箱、各種銅箱。 */
    public static boolean isPlaylistContainer(Material m) {
        if (m == Material.CHEST || m == Material.TRAPPED_CHEST) {
            return true;
        }
        return m.name().endsWith("COPPER_CHEST");
    }
}
