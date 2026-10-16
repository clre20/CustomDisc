package clre20.customDisc.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** 文字工具：& 顏色碼（含 &#RRGGBB）轉成 Adventure Component。 */
public final class Text {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    /** 一般訊息用（保留原本的樣式）。 */
    public static Component color(String text) {
        return AMPERSAND.deserialize(text == null ? "" : text);
    }

    /** 物品名稱、描述用：預設不要斜體。 */
    public static Component item(String text) {
        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(color(text));
    }

    public static List<Component> itemLines(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(line));
        }
        return out;
    }

    /** 去掉顏色碼後的純文字。 */
    public static String plain(String text) {
        return PlainTextComponentSerializer.plainText().serialize(color(text));
    }

    /** & 顏色碼轉 §，給只接受字串的舊 API 用。 */
    public static String legacySection(String text) {
        return LegacyComponentSerializer.legacySection().serialize(color(text));
    }
}
