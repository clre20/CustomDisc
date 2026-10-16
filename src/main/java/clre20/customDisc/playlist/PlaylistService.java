package clre20.customDisc.playlist;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.util.BlockKey;
import clre20.customDisc.util.McVersion;
import clre20.customDisc.util.Vanilla;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 唱片機輪播（照原版 26.4 的 Jukebox Playlists 規則）。
 * 只在 1.21.9 ～ 26.3 啟用；26.4 以上由原版處理。
 *
 * <ul>
 *   <li>唱片機正上方放單個箱子、陷阱箱或銅箱，箱內唱片就是歌單</li>
 *   <li>依格子順序播放，跳過空格與非唱片物品，播完回到開頭</li>
 *   <li>一張播完，唱片機裡的唱片與箱子裡的下一張互換</li>
 *   <li>箱內沒有唱片時，唱片機裡那張重播</li>
 *   <li>退出目前唱片會跳到下一張</li>
 * </ul>
 */
public final class PlaylistService implements Listener {

    private static final class State {
        boolean hadRecord;
        boolean wasPlaying;
        String recordId;
    }

    private final CustomDisc plugin;
    private final boolean enabled;
    /** 每個唱片機下一次從箱子哪一格開始找。 */
    private final Map<BlockKey, Integer> pointers = new HashMap<>();
    private final Map<BlockKey, State> tracked = new HashMap<>();
    private BukkitTask task;

    public PlaylistService(CustomDisc plugin) {
        this.plugin = plugin;
        McVersion v = McVersion.current();
        this.enabled = v.atLeast(1, 21, 9) && !v.atLeast(26, 4, 0);
    }

    public boolean enabled() {
        return enabled;
    }

    public void start() {
        if (!enabled) {
            return;
        }
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                scan(c);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        tracked.clear();
        pointers.clear();
    }

    /** 這個唱片機現在是不是輪播模式（上方有可用的箱子）。 */
    public boolean isPlaylist(Block jukebox) {
        return enabled && container(jukebox) != null;
    }

    /** 唱片機上方可當歌單的箱子，沒有則回傳 null。 */
    private Inventory container(Block jukebox) {
        Block above = jukebox.getRelative(BlockFace.UP);
        if (!Vanilla.isPlaylistContainer(above.getType())) {
            return null;
        }
        BlockData data = above.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Chest chestData
                && chestData.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            return null; // 大箱子不算
        }
        BlockState state = above.getState();
        if (state instanceof Chest chest) {
            return chest.getBlockInventory();
        }
        if (state instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    // ------------------------------------------------------------ 追蹤唱片機

    private void track(Block jukebox) {
        if (enabled && jukebox.getType() == Material.JUKEBOX) {
            tracked.computeIfAbsent(BlockKey.of(jukebox), k -> snapshot(jukebox));
        }
    }

    private State snapshot(Block jukebox) {
        State s = new State();
        if (jukebox.getState() instanceof Jukebox jb) {
            s.hadRecord = jb.hasRecord();
            s.wasPlaying = playing(jukebox, jb);
            s.recordId = recordId(jb.getRecord());
        }
        return s;
    }

    private void scan(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities(b -> b.getType() == Material.JUKEBOX, false)) {
            track(state.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (enabled) {
            scan(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        if (b.getType() == Material.JUKEBOX) {
            track(b);
        } else if (Vanilla.isPlaylistContainer(b.getType())) {
            track(b.getRelative(BlockFace.DOWN));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.JUKEBOX) {
            track(event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() == InventoryType.JUKEBOX
                && event.getDestination().getHolder() instanceof Jukebox jb) {
            track(jb.getBlock());
        }
    }

    // ------------------------------------------------------------ 輪播

    private boolean playing(Block block, Jukebox jb) {
        return jb.isPlaying() || plugin.jukebox().hasSession(block);
    }

    private String recordId(ItemStack record) {
        if (record == null || record.getType() == Material.AIR) {
            return null;
        }
        String custom = plugin.items().idOf(record);
        return custom != null ? custom : record.getType().name();
    }

    private void tick() {
        Iterator<Map.Entry<BlockKey, State>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockKey, State> e = it.next();
            Block block = e.getKey().block();
            if (block == null) {
                continue; // 區塊沒載入，先保留
            }
            if (!(block.getState() instanceof Jukebox jb)) {
                it.remove();
                continue;
            }
            State st = e.getValue();
            boolean has = jb.hasRecord();
            boolean playing = playing(block, jb);
            String id = recordId(jb.getRecord());

            Inventory chest = container(block);
            if (chest != null) {
                boolean sameRecord = id != null && id.equals(st.recordId);
                if (st.hadRecord && st.wasPlaying && has && !playing && sameRecord) {
                    // 原版／原生模式的唱片播完了（插件播放的由 onSongFinished 處理）
                    advance(block, chest, jb.getRecord());
                    st = snapshot(block);
                    e.setValue(st);
                    continue;
                }
                if (st.hadRecord && !has) {
                    // 唱片被退出：跳到下一張
                    pullNext(block, chest);
                    st = snapshot(block);
                    e.setValue(st);
                    continue;
                }
            }
            st.hadRecord = has;
            st.wasPlaying = playing;
            st.recordId = id;
        }
    }

    /** 插件播放的唱片播完時由 JukeboxService 呼叫。 */
    public void onSongFinished(Block block) {
        Inventory chest = container(block);
        if (chest == null || !(block.getState() instanceof Jukebox jb) || !jb.hasRecord()) {
            return;
        }
        advance(block, chest, jb.getRecord());
        tracked.put(BlockKey.of(block), snapshot(block));
    }

    /** 播完：和箱子裡的下一張互換；箱子沒唱片就重播。 */
    private void advance(Block block, Inventory chest, ItemStack current) {
        int slot = findNext(block, chest);
        if (slot < 0) {
            plugin.jukebox().insertAndPlay(block, current.clone(), true);
            return;
        }
        ItemStack next = chest.getItem(slot);
        chest.setItem(slot, current.clone());
        setPointer(block, slot + 1);
        plugin.jukebox().insertAndPlay(block, refreshed(next), true);
    }

    /** 唱片被退出：從箱子拿下一張放進去。 */
    private void pullNext(Block block, Inventory chest) {
        int slot = findNext(block, chest);
        if (slot < 0) {
            return;
        }
        ItemStack next = chest.getItem(slot);
        chest.setItem(slot, null);
        setPointer(block, slot + 1);
        plugin.jukebox().insertAndPlay(block, refreshed(next), true);
    }

    private ItemStack refreshed(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        ItemStack fresh = plugin.items().refresh(copy);
        return fresh != null ? fresh : copy;
    }

    /** 從上次的位置往後找下一張可以播的唱片（會繞回開頭）。 */
    private int findNext(Block block, Inventory chest) {
        int size = chest.getSize();
        int start = Math.floorMod(getPointer(block), size);
        for (int i = 0; i < size; i++) {
            int slot = (start + i) % size;
            ItemStack item = chest.getItem(slot);
            if (item != null && Vanilla.isDisc(item.getType()) && !plugin.items().isInvalid(item)) {
                return slot;
            }
        }
        return -1;
    }

    private int getPointer(Block block) {
        return pointers.getOrDefault(BlockKey.of(block), 0);
    }

    private void setPointer(Block block, int value) {
        pointers.put(BlockKey.of(block), value);
    }
}
