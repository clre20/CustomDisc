package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.pack.SoundLibrary;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 步驟 3：從 sounds 資料夾選擇音檔。 */
final class SoundFileMenu extends EditorMenu {

    private static final int PER_PAGE = 45;
    private final int page;

    SoundFileMenu(EditSession session, int page) {
        super(session, 6, "gui.sound.title", 3);
        this.page = page;
    }

    @Override
    protected void build() {
        CustomDisc plugin = CustomDisc.get();
        List<SoundLibrary.Entry> entries = plugin.library().entries();
        if (entries.isEmpty()) {
            set(22, icon(Material.PAPER, text("gui.sound.empty-name"), lines("gui.sound.empty-lore")), null);
        }
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(entries.size(), from + PER_PAGE); i++) {
            SoundLibrary.Entry e = entries.get(i);
            if (!e.valid()) {
                set(i - from, icon(Material.BARRIER, text("gui.sound.invalid-name", "file", e.path()),
                        lines("gui.sound.invalid-lore", "error", plugin.library().errorText(e))), null);
                continue;
            }
            boolean selected = e.path().equals(session.draft().soundFile());
            List<String> lore = lines("gui.sound.file-lore",
                    "time", Editor.formatTime(e.info().seconds()),
                    "channels", text(e.info().stereo() ? "gui.sound.channels-stereo" : "gui.sound.channels-mono"));
            if (!plugin.packs().contains(e.id())) {
                lore.add(text("gui.sound.new-file"));
            }
            lore.add("");
            lore.add(selectLine(selected));
            ItemStack icon = icon(selected ? Material.JUKEBOX : Material.NOTE_BLOCK,
                    text("gui.sound.file-name", "file", e.path()), lore);
            set(i - from, selected ? glow(icon) : icon, p -> editor().afterSoundFile(p, session, e));
        }

        back(45, p -> editor().promptLore(p));
        set(47, icon(Material.SUNFLOWER, text("gui.sound.refresh-name"), lines("gui.sound.refresh-lore")),
                p -> editor().soundFiles(p));
        if (page > 0) {
            set(48, icon(Material.ARROW, text("gui.previous-page"), List.of()), p -> new SoundFileMenu(session, page - 1).open(p));
        }
        stepIndicator(49);
        if (page < pages - 1) {
            set(50, icon(Material.ARROW, text("gui.next-page"), List.of()), p -> new SoundFileMenu(session, page + 1).open(p));
        }
        fill(45, 53);
    }
}
