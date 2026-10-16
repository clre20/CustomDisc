package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.pack.SoundLibrary;
import clre20.customDisc.util.Vanilla;
import org.bukkit.Material;
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
        super(session, 3, (session.creating() ? "確認建立" : "編輯")
                + session.draft().type().displayName() + "：" + session.draft().name(), 6);
    }

    @Override
    protected void build() {
        CustomDisc plugin = CustomDisc.get();
        CustomSound d = session.draft();
        boolean disc = d.type() == SoundType.DISC;

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
        set(10, icon(look, "&f外觀：&e" + lookName, lines("", "&e點擊修改")), p -> editor().appearance(p));

        // 名稱
        set(11, icon(Material.NAME_TAG, "&f名稱", lines("&r" + d.displayName(), "", "&e點擊修改（聊天室輸入）")),
                p -> editor().promptName(p));

        // 描述
        List<String> lore = lines();
        if (d.lore().isEmpty()) {
            lore.add("&8（無）");
        } else {
            for (String line : d.lore()) {
                lore.add("&r" + line);
            }
        }
        lore.add("");
        lore.add("&e點擊修改（聊天室輸入）");
        set(12, icon(Material.WRITABLE_BOOK, "&f描述", lore), p -> editor().promptLore(p));

        // 音檔
        SoundLibrary.Entry entry = plugin.library().find(d.soundFile());
        if (entry != null && entry.valid()) {
            set(13, icon(Material.NOTE_BLOCK, "&f音檔：&e" + entry.path(), lines(
                    "&7長度：&f" + Editor.formatTime(entry.info().seconds()),
                    "&7聲道：" + (entry.info().stereo() ? "&e立體聲" : "&f單聲道"),
                    "", "&e點擊修改")), p -> editor().soundFiles(p));
        } else {
            set(13, icon(Material.BARRIER, "&c音檔：尚未選擇",
                    d.soundFile() == null || d.soundFile().isEmpty() ? "&7請選擇一個音檔" : "&7找不到 " + d.soundFile(),
                    "", "&e點擊選擇"), p -> editor().soundFiles(p));
        }

        // 循環／冷卻
        if (disc) {
            set(14, d.loop()
                    ? icon(Material.LIME_DYE, "&f循環播放：&a開", "", "&e點擊修改")
                    : icon(Material.GRAY_DYE, "&f循環播放：&7關", "", "&e點擊修改"), p -> editor().loopCooldown(p));
        } else {
            set(14, icon(Material.CLOCK, "&f冷卻：&e" + d.cooldown() + " 秒", "&7最低 " + d.minCooldown() + " 秒", "",
                    "&e點擊修改"), p -> editor().loopCooldown(p));
        }

        // 音量與範圍
        set(15, icon(Material.BELL, "&f音量 &e" + d.volume() + "% &7・ &f範圍 &e" + d.range() + " 格", "", "&e點擊修改"),
                p -> editor().volumeRange(p));

        // 比較器（只有唱片）
        if (disc) {
            String comp = d.comparator() == null
                    ? "預設（" + Vanilla.comparator(d.appearance()) + "）"
                    : String.valueOf(d.comparator());
            set(16, icon(Material.COMPARATOR, "&f比較器強度：&e" + comp, "", "&e點擊修改"), p -> editor().comparator(p));
        }

        // 下排
        set(18, icon(Material.BARRIER, "&c取消", "&7不儲存並關閉"), p -> p.closeInventory());
        boolean previewing = session.previewEvent() != null;
        set(22, previewing
                        ? icon(Material.JUKEBOX, "&c停止試聽")
                        : icon(Material.JUKEBOX, "&a試聽", "&7只播給你自己聽，不用先儲存"),
                p -> {
                    editor().togglePreview(p, session);
                    redraw();
                });
        List<String> saveLore = lines("&7儲存後所有已發出去的物品會自動更新");
        if (plugin.config().nativeMode()) {
            saveLore.add("&7新建或改了長度／範圍／比較器／冷卻時，");
            saveLore.add("&7重啟前由插件播放，重啟後改用原生模式");
        }
        set(26, icon(Material.LIME_CONCRETE, "&a儲存", saveLore), p -> editor().save(p, session));
        fill(0, 26);
    }
}
