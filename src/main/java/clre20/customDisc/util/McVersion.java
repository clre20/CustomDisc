package clre20.customDisc.util;

import org.bukkit.Bukkit;

/**
 * 伺服器的 Minecraft 版本。
 * 舊格式：1.21.11；新格式：26.1、26.3.2。兩種都轉成 (major, minor, patch) 比較，
 * 新格式的 26 一定大於舊格式的 1，所以可以直接比較。
 */
public final class McVersion implements Comparable<McVersion> {

    private static McVersion current;

    private final int major;
    private final int minor;
    private final int patch;

    private McVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static McVersion of(int major, int minor, int patch) {
        return new McVersion(major, minor, patch);
    }

    public static McVersion parse(String text) {
        String clean = text.trim();
        int cut = clean.indexOf('-');
        if (cut >= 0) {
            clean = clean.substring(0, cut);
        }
        cut = clean.indexOf(' ');
        if (cut >= 0) {
            clean = clean.substring(0, cut);
        }
        String[] parts = clean.split("\\.");
        int[] nums = new int[3];
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                nums[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                nums[i] = 0;
            }
        }
        return new McVersion(nums[0], nums[1], nums[2]);
    }

    public static McVersion current() {
        if (current == null) {
            String version;
            try {
                version = Bukkit.getServer().getMinecraftVersion();
            } catch (Throwable t) {
                version = Bukkit.getBukkitVersion();
            }
            current = parse(version);
        }
        return current;
    }

    public boolean atLeast(int major, int minor, int patch) {
        return compareTo(of(major, minor, patch)) >= 0;
    }

    @Override
    public int compareTo(McVersion o) {
        if (major != o.major) return Integer.compare(major, o.major);
        if (minor != o.minor) return Integer.compare(minor, o.minor);
        return Integer.compare(patch, o.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + (patch > 0 ? "." + patch : "");
    }
}
