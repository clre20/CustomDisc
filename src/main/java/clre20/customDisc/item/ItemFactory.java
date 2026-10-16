package clre20.customDisc.item;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.pack.DatapackService;
import clre20.customDisc.util.McVersion;
import clre20.customDisc.util.Text;
import clre20.customDisc.util.Vanilla;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 產生自訂唱片／號角物品。
 * 物品只記錄 ID（例如 disc:my_song）和版本簽章，名稱、描述等都從 yml 讀取，
 * 所以編輯之後，玩家手上的舊物品在下次被看到時會自動更新。
 */
public final class ItemFactory {

    private final CustomDisc plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey sigKey;

    public ItemFactory(CustomDisc plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "id");
        this.sigKey = new NamespacedKey(plugin, "sig");
    }

    /** 物品上的 ID（disc:xxx / horn:xxx），不是自訂物品則回傳 null。 */
    public String idOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public boolean isCustom(ItemStack item) {
        return idOf(item) != null;
    }

    /** 找出物品對應的資料；物品不是自訂物品或已被刪除時回傳 null。 */
    public CustomSound soundOf(ItemStack item) {
        String id = idOf(item);
        if (id == null) {
            return null;
        }
        int cut = id.indexOf(':');
        if (cut < 0) {
            return null;
        }
        SoundType type = SoundType.fromId(id.substring(0, cut));
        if (type == null) {
            return null;
        }
        return plugin.store().get(type, id.substring(cut + 1));
    }

    /** 自訂物品但資料已被刪除。 */
    public boolean isInvalid(ItemStack item) {
        return isCustom(item) && soundOf(item) == null;
    }

    public ItemStack create(CustomSound s, int amount) {
        ItemStack item = new ItemStack(s.type() == SoundType.DISC ? Vanilla.material(s.appearance()) : Material.GOAT_HORN);
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        apply(item, s);
        return item;
    }

    private String signature(CustomSound s) {
        if (s == null) {
            return "x";
        }
        return s.revision() + ":" + (plugin.datapack().isNative(s) ? "n" : "p");
    }

    private void apply(ItemStack item, CustomSound s) {
        boolean nativeMode = plugin.datapack().isNative(s);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Text.item(s.displayName()));
        meta.lore(Text.itemLines(s.lore()));
        hideExtraTooltip(meta);

        if (s.type() == SoundType.DISC) {
            // 1.21.5 起歌名提示改由 tooltip_display 隱藏（上面的旗標）；舊版要在元件裡關掉
            boolean oldTooltip = !McVersion.current().atLeast(1, 21, 5);
            if (nativeMode || oldTooltip) {
                JukeboxPlayableComponent jukebox = meta.getJukeboxPlayable();
                if (nativeMode) {
                    jukebox.setSongKey(DatapackService.key(s));
                }
                if (oldTooltip) {
                    try {
                        jukebox.setShowInTooltip(false);
                    } catch (Throwable ignored) {
                    }
                }
                meta.setJukeboxPlayable(jukebox);
            }
        } else if (meta instanceof MusicInstrumentMeta horn) {
            MusicInstrument instrument = null;
            if (nativeMode) {
                instrument = Registry.INSTRUMENT.get(DatapackService.key(s));
            }
            horn.setInstrument(instrument != null ? instrument : Vanilla.instrument(s.appearance()));
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(idKey, PersistentDataType.STRING, s.id());
        pdc.set(sigKey, PersistentDataType.STRING, signature(s));
        item.setItemMeta(meta);
    }

    private static void hideExtraTooltip(ItemMeta meta) {
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (Throwable ignored) {
            // 新版沒有這個旗標時就不隱藏
        }
    }

    /**
     * 物品如果已經過期（資料被編輯、刪除或原生模式狀態改變），回傳更新後的新物品；不需要更新時回傳 null。
     */
    public ItemStack refresh(ItemStack item) {
        String id = idOf(item);
        if (id == null) {
            return null;
        }
        CustomSound s = soundOf(item);
        String want = signature(s);
        String have = item.getItemMeta().getPersistentDataContainer().get(sigKey, PersistentDataType.STRING);
        if (want.equals(have)) {
            return null;
        }
        ItemMeta oldMeta = item.getItemMeta();
        Component customName = oldMeta.hasDisplayName() ? oldMeta.displayName() : null;

        ItemStack fresh;
        if (s == null) {
            fresh = invalid(item, id);
        } else {
            fresh = create(s, item.getAmount());
            fresh.setAmount(item.getAmount());
        }
        if (customName != null) {
            ItemMeta m = fresh.getItemMeta();
            m.displayName(customName);
            fresh.setItemMeta(m);
        }
        return fresh;
    }

    /** 已刪除的唱片／號角：保留外觀，標示已失效。 */
    private ItemStack invalid(ItemStack old, String id) {
        ItemStack item = old.clone();
        ItemMeta meta = item.getItemMeta();
        String typeName = id.startsWith("horn:") ? "號角" : "唱片";
        meta.itemName(Text.item("&c已失效的" + typeName));
        List<String> lore = new ArrayList<>();
        lore.add("&7這個" + typeName + "已被刪除，無法使用");
        meta.lore(Text.itemLines(lore));
        meta.getPersistentDataContainer().set(sigKey, PersistentDataType.STRING, "x");
        item.setItemMeta(meta);
        return item;
    }
}
