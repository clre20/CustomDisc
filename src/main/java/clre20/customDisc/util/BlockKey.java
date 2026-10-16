package clre20.customDisc.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

/** 方塊座標（當 Map 的 key 用）。 */
public record BlockKey(UUID world, int x, int y, int z) {

    public static BlockKey of(Block b) {
        return new BlockKey(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }

    /** 方塊所在區塊已載入時回傳方塊，否則 null。 */
    public Block block() {
        World w = Bukkit.getWorld(world);
        if (w == null || !w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return w.getBlockAt(x, y, z);
    }
}
