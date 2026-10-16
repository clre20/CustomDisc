package clre20.customDisc.gui;

import clre20.customDisc.data.CustomSound;
import clre20.customDisc.util.Vanilla;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** 比較器訊號強度：預設（沿用外觀唱片）或 0–15。 */
final class ComparatorMenu extends EditorMenu {

    ComparatorMenu(EditSession session) {
        super(session, 3, "比較器訊號強度", 6);
    }

    @Override
    protected void build() {
        CustomSound d = session.draft();
        for (int v = 0; v <= 15; v++) {
            int value = v;
            boolean selected = d.comparator() != null && d.comparator() == v;
            ItemStack item = new ItemStack(v == 0 ? Material.GUNPOWDER : Material.REDSTONE, Math.max(1, v));
            ItemStack icon = icon(item, "&f強度 " + v, lines(selected ? "&a✔ 目前選擇" : "&e點擊選擇"));
            set(v, selected ? glow(icon) : icon, p -> {
                d.comparator(value);
                editor().overview(p);
            });
        }
        int def = Vanilla.comparator(d.appearance());
        ItemStack defIcon = icon(Material.REPEATER, "&f預設（" + def + "）", lines(
                "&7沿用外觀唱片的原版強度",
                "",
                d.comparator() == null ? "&a✔ 目前選擇" : "&e點擊選擇"));
        set(17, d.comparator() == null ? glow(defIcon) : defIcon, p -> {
            d.comparator(null);
            editor().overview(p);
        });
        set(22, icon(Material.ARROW, "&f返回總覽", "&7比較器設定在重啟伺服器後生效"), p -> editor().overview(p));
        fill(0, 26);
    }
}
