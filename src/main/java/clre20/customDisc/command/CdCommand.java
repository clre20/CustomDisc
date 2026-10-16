package clre20.customDisc.command;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.config.Msg;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.update.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /cdisc、/c-disc、/cd 指令。 */
public final class CdCommand implements TabExecutor {

    private static final List<String> SUBS = List.of(
            "help", "list", "create", "edit", "del", "delete", "give", "reload");
    private static final Map<String, String> PERMS = Map.of(
            "help", "customdisc.help",
            "list", "customdisc.list",
            "create", "customdisc.create",
            "edit", "customdisc.edit",
            "del", "customdisc.delete",
            "delete", "customdisc.delete",
            "give", "customdisc.give",
            "reload", "customdisc.reload");

    private final CustomDisc plugin;

    public CdCommand(CustomDisc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            version(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String perm = PERMS.get(sub);
        if (perm == null) {
            Msg.send(sender, "&c未知的指令，輸入 &f/" + label + " help &c查看說明");
            return true;
        }
        if (!sender.hasPermission(perm)) {
            Msg.send(sender, "&c你沒有權限使用這個指令");
            return true;
        }
        switch (sub) {
            case "help" -> help(sender, label);
            case "list" -> list(sender, args);
            case "create" -> create(sender, label, args);
            case "edit" -> edit(sender, label, args);
            case "del", "delete" -> delete(sender, label, args);
            case "give" -> give(sender, label, args);
            case "reload" -> reload(sender);
            default -> {
            }
        }
        return true;
    }

    // ------------------------------------------------------------ 子指令

    private void version(CommandSender sender) {
        if (!sender.hasPermission("customdisc.use")) {
            Msg.send(sender, "&c你沒有權限使用這個指令");
            return;
        }
        String current = plugin.getPluginMeta().getVersion();
        Msg.send(sender, "&fCustomDisc（C-Disc）&7版本 &e" + current);
        Msg.send(sender, "&7正在檢查更新…");
        plugin.updates().check().thenAccept(r -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (r.latest() == null) {
                Msg.send(sender, "&7檢查更新：" + r.error());
            } else if (r.newer()) {
                Msg.send(sender, "&a有新版本 &e" + r.latest() + "&a！下載：&f" + UpdateChecker.PAGE);
            } else {
                Msg.send(sender, "&a目前已是最新版本");
            }
        }));
    }

    private void help(CommandSender sender, String label) {
        Msg.send(sender, "&f指令說明");
        line(sender, "customdisc.use", "/" + label, "查看版本並檢查更新");
        line(sender, "customdisc.list", "/" + label + " list [disc|horn]", "唱片／號角列表");
        line(sender, "customdisc.create", "/" + label + " create <disc|horn> <name>", "建立");
        line(sender, "customdisc.edit", "/" + label + " edit <disc|horn> <name>", "編輯（已發出的物品會自動更新）");
        line(sender, "customdisc.delete", "/" + label + " delete <disc|horn> <name>", "刪除（別名 del）");
        line(sender, "customdisc.give", "/" + label + " give <disc|horn> <name> [玩家] [數量]", "給予");
        line(sender, "customdisc.reload", "/" + label + " reload", "重新載入設定、資料與音檔");
        Msg.raw(sender, "&7name 只能用 a-z、0-9、_；音檔放在 plugins/CustomDisc/sounds/");
    }

    private void line(CommandSender sender, String perm, String usage, String desc) {
        if (sender.hasPermission(perm)) {
            Msg.raw(sender, " &e" + usage + " &7- " + desc);
        }
    }

    private void list(CommandSender sender, String[] args) {
        List<SoundType> types = new ArrayList<>();
        if (args.length >= 2) {
            SoundType t = SoundType.fromId(args[1]);
            if (t == null) {
                Msg.send(sender, "&c類型只能是 disc 或 horn");
                return;
            }
            types.add(t);
        } else {
            types.add(SoundType.DISC);
            types.add(SoundType.HORN);
        }
        for (SoundType t : types) {
            Collection<CustomSound> all = plugin.store().all(t);
            Msg.send(sender, "&f" + t.displayName() + "（" + all.size() + "）");
            if (all.isEmpty()) {
                Msg.raw(sender, " &8（沒有）");
            }
            for (CustomSound s : all) {
                String mode = plugin.datapack().isNative(s) ? "&a原生" : "&e插件播放";
                Msg.raw(sender, " &7- &f" + s.name() + " &7" + "「&r" + s.displayName() + "&7」 " + mode);
            }
        }
    }

    private CustomSound target(CommandSender sender, String label, String[] args, String sub) {
        if (args.length < 3) {
            Msg.send(sender, "&c用法：&f/" + label + " " + sub + " <disc|horn> <name>");
            return null;
        }
        SoundType type = SoundType.fromId(args[1]);
        if (type == null) {
            Msg.send(sender, "&c類型只能是 disc 或 horn");
            return null;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        CustomSound s = plugin.store().get(type, name);
        if (s == null) {
            Msg.send(sender, "&c找不到" + type.displayName() + " " + name);
        }
        return s;
    }

    private void create(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&c只有玩家可以使用介面建立");
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, "&c用法：&f/" + label + " create <disc|horn> <name>");
            return;
        }
        SoundType type = SoundType.fromId(args[1]);
        if (type == null) {
            Msg.send(sender, "&c類型只能是 disc 或 horn");
            return;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        if (!CustomSound.NAME_PATTERN.matcher(name).matches()) {
            Msg.send(sender, "&cname 只能用 a-z、0-9、_，最多 32 個字（顯示名稱可以在介面裡另外設定中文）");
            return;
        }
        if (plugin.store().exists(type, name)) {
            Msg.send(sender, "&c" + type.displayName() + " " + name + " 已經存在，要修改請用 &f/" + label + " edit " + type.id() + " " + name);
            return;
        }
        plugin.editor().startCreate(player, type, name);
    }

    private void edit(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&c只有玩家可以使用介面編輯");
            return;
        }
        CustomSound s = target(sender, label, args, "edit");
        if (s != null) {
            plugin.editor().startEdit(player, s);
        }
    }

    private void delete(CommandSender sender, String label, String[] args) {
        CustomSound s = target(sender, label, args, args[0].toLowerCase(Locale.ROOT));
        if (s == null) {
            return;
        }
        plugin.store().delete(s.type(), s.name());
        plugin.applyChanges();
        Msg.send(sender, "&a已刪除" + s.type().displayName() + " &f" + s.name() + "&a，已發出去的物品會變成「已失效」");
    }

    private void give(CommandSender sender, String label, String[] args) {
        CustomSound s = target(sender, label, args, "give");
        if (s == null) {
            return;
        }
        Player to;
        if (args.length >= 4) {
            to = Bukkit.getPlayerExact(args[3]);
            if (to == null) {
                Msg.send(sender, "&c找不到線上玩家 " + args[3]);
                return;
            }
            if (to != sender && !sender.hasPermission("customdisc.give.others")) {
                Msg.send(sender, "&c你沒有權限給其他玩家");
                return;
            }
        } else if (sender instanceof Player p) {
            to = p;
        } else {
            Msg.send(sender, "&c後台使用時請指定玩家：&f/" + label + " give " + args[1] + " " + args[2] + " <玩家> [數量]");
            return;
        }
        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                Msg.send(sender, "&c數量必須是數字");
                return;
            }
        }
        if (amount < 1 || amount > 2304) {
            Msg.send(sender, "&c數量必須在 1 到 2304 之間");
            return;
        }
        int left = amount;
        int dropped = 0;
        while (left > 0) {
            ItemStack item = plugin.items().create(s, left);
            left -= item.getAmount();
            for (ItemStack rest : to.getInventory().addItem(item).values()) {
                to.getWorld().dropItemNaturally(to.getLocation(), rest);
                dropped += rest.getAmount();
            }
        }
        Msg.send(sender, "&a已給 &f" + to.getName() + " &a" + amount + " 個" + s.type().displayName() + " &f" + s.name()
                + (dropped > 0 ? "&7（背包滿了，" + dropped + " 個掉在地上）" : ""));
        if (to != sender) {
            Msg.send(to, "&a你收到了 " + amount + " 個" + s.type().displayName() + "「&r" + s.displayName() + "&a」");
        }
    }

    private void reload(CommandSender sender) {
        Msg.send(sender, "&7重新載入中…");
        plugin.reload().thenAccept(changed -> Bukkit.getScheduler().runTask(plugin, () -> {
            Msg.send(sender, "&a重新載入完成" + (changed ? "，資源包已更新並推送給線上玩家" : ""));
            if (plugin.datapackChanged()) {
                Msg.send(sender, "&7有唱片／號角的設定要重啟伺服器後才會改用原生模式");
            }
        }));
    }

    // ------------------------------------------------------------ Tab 補全

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (sender.hasPermission(PERMS.get(s))) {
                    out.add(s);
                }
            }
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!PERMS.containsKey(sub) || !sender.hasPermission(PERMS.get(sub)) || sub.equals("help") || sub.equals("reload")) {
                return out;
            }
            if (args.length == 2) {
                out.add("disc");
                out.add("horn");
            } else if (args.length == 3 && !sub.equals("list") && !sub.equals("create")) {
                SoundType t = SoundType.fromId(args[1]);
                if (t != null) {
                    out.addAll(plugin.store().names(t));
                }
            } else if (args.length == 4 && sub.equals("give")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
            } else if (args.length == 5 && sub.equals("give")) {
                out.add("1");
                out.add("16");
                out.add("64");
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
