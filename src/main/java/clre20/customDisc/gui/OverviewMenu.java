package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.pack.SoundLibrary;
import clre20.customDisc.util.Vanilla;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MusicInstrumentMeta;

import java.util.List;

/**
 * 總覽頁（建立流程第 6 步，或 /cd edit 直接打開）。
 * <pre>
 * [ ■ ][ ■ ][ ■ ][ ■ ][ ■ ][ ■ ][ ■ ][ ■ ][ ■ ]
 * [ ■ ][外觀][名稱][描述][音檔][循環][音量][比較器][ ■ ]
 * [取消][ ■ ][ ■ ][ ■ ][試聽][ ■ ][ ■ ][ ■ ][儲存]
 * </pre>
 */
final class OverviewMenu extends EditorMenu {

    OverviewMenu(EditSession session) {
        super(session, 3, session.creating() ? "gui.overview.title-create" : "gui.overview.title-edit", 6,
                "type", session.draft().type().displayName(), "name", session.draft().name());
    }

    @Override
    protected void build() {
        CustomDisc plugin = CustomDisc.get();
        CustomSound d = session.draft();
        boolean disc = d.type() == SoundType.DISC;
        List<String> clickEdit = join(one(""), one(text("gui.click-edit")));

        // 外觀
        ItemStack look;
        String lookName;
        if (disc) {
            look = new ItemStack(Vanilla.material(d.appearance()));
            lookName = Vanilla.discName(d.appearance());
        } else {
            look = new ItemStack(Material.GOAT_HORN);
            if (look.getItemMeta() instanceof MusicInstrumentMeta meta) {
                meta.setInstrument(Vanilla.instrument(d.appearance()));
                look.setItemMeta(meta);
            }
            lookName = Vanilla.hornName(d.appearance());
        }
        set(10, icon(look, text("gui.overview.appearance-name", "name", lookName), clickEdit), p -> editor().appearance(p));

        // 名稱
        set(11, icon(Material.NAME_TAG, text("gui.overview.name-name"),
                lines("gui.overview.name-lore", "display", d.displayName())), p -> editor().promptName(p));

        // 描述
        List<String> lore = new java.util.ArrayList<>();
        if (d.lore().isEmpty()) {
            lore.add(text("common.none"));
        } else {
            for (String line : d.lore()) {
                lore.add(text("gui.overview.lore-line", "line", line));
            }
        }
        lore.addAll(lines("gui.overview.lore-footer"));
        set(12, icon(Material.WRITABLE_BOOK, text("gui.overview.lore-name"), lore), p -> editor().promptLore(p));

        // 音檔
        SoundLibrary.Entry entry = plugin.library().find(d.soundFile());
        if (entry != null && entry.valid()) {
            set(13, icon(Material.NOTE_BLOCK, text("gui.overview.sound-name", "file", entry.path()),
                    join(lines("gui.overview.sound-lore",
                            "time", Editor.formatTime(entry.info().seconds()),
                            "channels", text(entry.info().stereo() ? "gui.overview.channels-stereo" : "gui.overview.channels-mono")),
                            clickEdit)), p -> editor().soundFiles(p));
        } else {
            String reason = d.soundFile() == null || d.soundFile().isEmpty()
                    ? text("gui.overview.sound-missing-none")
                    : text("gui.overview.sound-missing-file", "file", d.soundFile());
            set(13, icon(Material.BARRIER, text("gui.overview.sound-missing-name"),
                    join(one(reason), one(""), one(text("gui.click-select")))), p -> editor().soundFiles(p));
        }

        // 循環／冷卻
        if (disc) {
            set(14, icon(d.loop() ? Material.LIME_DYE : Material.GRAY_DYE,
                    text(d.loop() ? "gui.overview.loop-on" : "gui.overview.loop-off"), clickEdit),
                    p -> editor().loopCooldown(p));
        } else {
            set(14, icon(Material.CLOCK, text("gui.overview.cooldown-name", "seconds", d.cooldown()),
                    join(lines("gui.overview.cooldown-lore", "min", d.minCooldown()), clickEdit)),
                    p -> editor().loopCooldown(p));
        }

        // 音量與範圍
        set(15, icon(Material.BELL, text("gui.overview.volume-name", "volume", d.volume(), "range", d.range()), clickEdit),
                p -> editor().volumeRange(p));

        // 比較器（只有唱片）
        if (disc) {
            String comp = d.comparator() == null
                    ? text("gui.overview.comparator-default", "value", Vanilla.comparator(d.appearance()))
                    : String.valueOf(d.comparator());
            set(16, icon(Material.COMPARATOR, text("gui.overview.comparator-name", "value", comp), clickEdit),
                    p -> editor().comparator(p));
        }

        // 下排
        set(18, icon(Material.BARRIER, text("gui.overview.cancel-name"), lines("gui.overview.cancel-lore")),
                Player::closeInventory);
        boolean previewing = session.previewEvent() != null;
        set(22, previewing
                        ? icon(Material.JUKEBOX, text("gui.overview.preview-stop-name"), List.of())
                        : icon(Material.JUKEBOX, text("gui.overview.preview-name"), lines("gui.overview.preview-lore")),
                p -> {
                    editor().togglePreview(p, session);
                    redraw();
                });
        List<String> saveLore = lines("gui.overview.save-lore");
        if (plugin.config().nativeMode()) {
            saveLore.addAll(lines("gui.overview.save-lore-native"));
        }
        set(26, icon(Material.LIME_CONCRETE, text("gui.overview.save-name"), saveLore), p -> editor().save(p, session));
        fill(0, 26);
    }
}
