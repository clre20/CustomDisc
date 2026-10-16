package clre20.customDisc.gui;

import clre20.customDisc.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 箱子 UI 的基底：每一格可以設定圖示和左鍵點擊後的動作。 */
public abstract class Menu implements InventoryHolder {

    protected final EditSession session;
    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    protected Menu(EditSession session, int rows, String title) {
        this.session = session;
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.color(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** 畫出內容。每次開啟或重新整理都會呼叫。 */
    protected abstract void build();

    public void open(Player player) {
        redraw();
        session.navigating(true);
        player.openInventory(inventory);
        session.navigating(false);
    }

    public void redraw() {
        inventory.clear();
        actions.clear();
        build();
    }

    void click(int slot, Player player) {
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }

    protected void set(int slot, ItemStack icon, Consumer<Player> action) {
        inventory.setItem(slot, icon);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    protected void fill(int from, int to) {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = from; i <= to; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, pane);
            }
        }
    }

    // ------------------------------------------------------------ 圖示

    public static ItemStack icon(Material material, String name, String... lore) {
        return icon(new ItemStack(material), name, Arrays.asList(lore));
    }

    public static ItemStack icon(Material material, String name, List<String> lore) {
        return icon(new ItemStack(material), name, lore);
    }

    public static ItemStack icon(ItemStack base, String name, List<String> lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        meta.lore(Text.itemLines(lore));
        try {
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        } catch (Throwable ignored) {
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public static List<String> lines(String... lines) {
        return new ArrayList<>(Arrays.asList(lines));
    }
}
