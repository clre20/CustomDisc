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
        super(session, 6, "選擇音檔", 3);
        this.page = page;
    }

    @Override
    protected void build() {
        CustomDisc plugin = CustomDisc.get();
        List<SoundLibrary.Entry> entries = plugin.library().entries();
        if (entries.isEmpty()) {
            set(22, icon(Material.PAPER, "&e還沒有任何音檔",
                    "&7把 .ogg 檔放進",
                    "&fplugins/CustomDisc/sounds/",
                    "&7再點下方的「重新整理」"), null);
        }
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(entries.size(), from + PER_PAGE); i++) {
            SoundLibrary.Entry e = entries.get(i);
            boolean selected = e.path().equals(session.draft().soundFile());
            ItemStack icon;
            if (!e.valid()) {
                icon = icon(Material.BARRIER, "&c" + e.path(), "&7無法使用：" + e.error());
                set(i - from, icon, null);
                continue;
            }
            List<String> lore = lines(
                    "&7長度：&f" + Editor.formatTime(e.info().seconds()),
                    "&7聲道：" + (e.info().stereo() ? "&e立體聲（不會隨距離變小聲）" : "&f單聲道"));
            if (!plugin.packs().contains(e.id())) {
                lore.add("&8新檔案，/cd reload 後才能試聽");
            }
            lore.add("");
            lore.add(selected ? "&a✔ 目前選擇" : "&e點擊選擇");
            icon = icon(selected ? Material.JUKEBOX : Material.NOTE_BLOCK, "&f" + e.path(), lore);
            set(i - from, selected ? glow(icon) : icon, p -> editor().afterSoundFile(p, session, e));
        }

        back(45, p -> editor().promptLore(p));
        set(47, icon(Material.SUNFLOWER, "&f重新整理", "&7重新讀取 sounds 資料夾"), p -> {
            plugin.library().scan();
            new SoundFileMenu(session, 0).open(p);
        });
        if (page > 0) {
            set(48, icon(Material.ARROW, "&f上一頁"), p -> new SoundFileMenu(session, page - 1).open(p));
        }
        stepIndicator(49);
        if (page < pages - 1) {
            set(50, icon(Material.ARROW, "&f下一頁"), p -> new SoundFileMenu(session, page + 1).open(p));
        }
        fill(45, 53);
    }
}
