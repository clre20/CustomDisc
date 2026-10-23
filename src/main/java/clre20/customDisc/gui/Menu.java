package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 箱子 UI 的基底：每一格可以設定圖示和左鍵點擊後的動作。
 *
 * <p>插件簽署：Inventory 的持有者是這個類別（綁定插件實例，可用 {@link #getPlugin()} 取得），
 * 每個圖示也都寫入 PersistentDataContainer 標籤 {@code customdisc:gui}，其他插件可以藉此辨識介面來源。</p>
 */
public abstract class Menu implements InventoryHolder {

    /** 寫在每個圖示上的簽署標籤，值為介面類型名稱。 */
    public static final String SIGNATURE_KEY = "gui";

    protected final EditSession session;
    private final CustomDisc plugin;
    private final NamespacedKey signature;
    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    protected Menu(EditSession session, int rows, String title) {
        this.session = session;
        this.plugin = CustomDisc.get();
        this.signature = new NamespacedKey(plugin, SIGNATURE_KEY);
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.color(title));
    }

    /** 這個介面所屬的插件。 */
    public Plugin getPlugin() {
        return plugin;
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
        inventory.setItem(slot, sign(icon));
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    protected void fill(int from, int to) {
        ItemStack pane = sign(icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        for (int i = from; i <= to; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, pane);
            }
        }
    }

    private ItemStack sign(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(signature, PersistentDataType.STRING, getClass().getSimpleName());
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------------------------------------------------ 圖示

    /** messages.yml 的文字。 */
    protected String text(String key, Object... vars) {
        return plugin.messages().get(key, vars);
    }

    protected List<String> lines(String key, Object... vars) {
        return plugin.messages().list(key, vars);
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
            meta.addItemFlags(ItemFlag.values());
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

    /** 把多段文字接成一份描述。 */
    @SafeVarargs
    protected static List<String> join(List<String>... parts) {
        List<String> out = new ArrayList<>();
        for (List<String> p : parts) {
            out.addAll(p);
        }
        return out;
    }

    protected static List<String> one(String line) {
        List<String> out = new ArrayList<>();
        out.add(line);
        return out;
    }
}
