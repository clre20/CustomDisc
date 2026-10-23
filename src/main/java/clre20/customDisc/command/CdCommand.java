package clre20.customDisc.command;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.config.Messages;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.update.UpdateNotifier;
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

    private static final int MAX_GIVE = 2304;
    private static final List<String> SUBS = List.of(
            "help", "version", "list", "create", "edit", "del", "delete", "give", "reload");
    private static final Map<String, String> PERMS = Map.of(
            "help", "customdisc.help",
            "version", UpdateNotifier.PERMISSION,
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

    private Messages msg() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        String perm = PERMS.get(sub);
        if (perm == null) {
            msg().send(sender, "command.unknown", "label", label);
            return true;
        }
        if (!sender.hasPermission(perm)) {
            msg().send(sender, "common.no-permission");
            return true;
        }
        switch (sub) {
            case "help" -> help(sender, label);
            case "version" -> plugin.updateNotifier().showTo(sender);
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

    private void help(CommandSender sender, String label) {
        String p = msg().get("help.arg-player");
        String a = msg().get("help.arg-amount");
        msg().send(sender, "help.header");
        line(sender, UpdateNotifier.PERMISSION, "/" + label + " version", "help.version");
        line(sender, "customdisc.list", "/" + label + " list [disc|horn]", "help.list");
        line(sender, "customdisc.create", "/" + label + " create <disc|horn> <name>", "help.create");
        line(sender, "customdisc.edit", "/" + label + " edit <disc|horn> <name>", "help.edit");
        line(sender, "customdisc.delete", "/" + label + " delete <disc|horn> <name>", "help.delete");
        line(sender, "customdisc.give", "/" + label + " give <disc|horn> <name> [" + p + "] [" + a + "]", "help.give");
        line(sender, "customdisc.reload", "/" + label + " reload", "help.reload");
        msg().raw(sender, "help.footer");
    }

    private void line(CommandSender sender, String perm, String usage, String descKey) {
        if (sender.hasPermission(perm)) {
            msg().raw(sender, "help.line", "usage", usage, "desc", msg().get(descKey));
        }
    }

    private void list(CommandSender sender, String[] args) {
        List<SoundType> types = new ArrayList<>();
        if (args.length >= 2) {
            SoundType t = SoundType.fromId(args[1]);
            if (t == null) {
                msg().send(sender, "common.invalid-type");
                return;
            }
            types.add(t);
        } else {
            types.add(SoundType.DISC);
            types.add(SoundType.HORN);
        }
        for (SoundType t : types) {
            Collection<CustomSound> all = plugin.store().all(t);
            msg().send(sender, "list.header", "type", t.displayName(), "count", all.size());
            if (all.isEmpty()) {
                sender.sendMessage(msg().component("common.none"));
            }
            for (CustomSound s : all) {
                String mode = msg().get(plugin.datapack().isNative(s) ? "common.mode-native" : "common.mode-plugin");
                msg().raw(sender, "list.entry", "name", s.name(), "display", s.displayName(), "mode", mode);
            }
        }
    }

    private CustomSound target(CommandSender sender, String label, String[] args, String sub) {
        if (args.length < 3) {
            msg().send(sender, "command.usage-target", "label", label, "sub", sub);
            return null;
        }
        SoundType type = SoundType.fromId(args[1]);
        if (type == null) {
            msg().send(sender, "common.invalid-type");
            return null;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        CustomSound s = plugin.store().get(type, name);
        if (s == null) {
            msg().send(sender, "common.not-found", "type", type.displayName(), "name", name);
        }
        return s;
    }

    private void create(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "common.player-only");
            return;
        }
        if (args.length < 3) {
            msg().send(sender, "command.usage-target", "label", label, "sub", "create");
            return;
        }
        SoundType type = SoundType.fromId(args[1]);
        if (type == null) {
            msg().send(sender, "common.invalid-type");
            return;
        }
        String name = args[2].toLowerCase(Locale.ROOT);
        if (!CustomSound.NAME_PATTERN.matcher(name).matches()) {
            msg().send(sender, "command.invalid-name");
            return;
        }
        if (plugin.store().exists(type, name)) {
            msg().send(sender, "command.already-exists", "type", type.displayName(), "name", name,
                    "label", label, "id", type.id());
            return;
        }
        plugin.editor().startCreate(player, type, name);
    }

    private void edit(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "common.player-only");
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
        msg().send(sender, "command.delete-done", "type", s.type().displayName(), "name", s.name());
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
                msg().send(sender, "command.give-player-not-found", "player", args[3]);
                return;
            }
            if (to != sender && !sender.hasPermission("customdisc.give.others")) {
                msg().send(sender, "command.give-no-permission-others");
                return;
            }
        } else if (sender instanceof Player p) {
            to = p;
        } else {
            msg().send(sender, "command.give-console-needs-player", "label", label, "id", s.type().id(), "name", s.name());
            return;
        }
        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                msg().send(sender, "command.give-amount-not-number");
                return;
            }
        }
        if (amount < 1 || amount > MAX_GIVE) {
            msg().send(sender, "command.give-amount-range", "max", MAX_GIVE);
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
        String done = msg().get("command.give-done", "player", to.getName(), "amount", amount,
                "type", s.type().displayName(), "name", s.name());
        if (dropped > 0) {
            done += msg().get("command.give-dropped", "dropped", dropped);
        }
        sender.sendMessage(clre20.customDisc.util.Text.color(plugin.config().prefix() + done));
        if (to != sender) {
            msg().send(to, "command.give-received", "amount", amount, "type", s.type().displayName(),
                    "display", s.displayName());
        }
    }

    private void reload(CommandSender sender) {
        msg().send(sender, "command.reload-start");
        plugin.reload().thenAccept(changed -> Bukkit.getScheduler().runTask(plugin, () -> {
            msg().send(sender, changed ? "command.reload-done-pack" : "command.reload-done");
            if (plugin.datapackChanged()) {
                msg().send(sender, "command.reload-restart-hint");
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
            if (!PERMS.containsKey(sub) || !sender.hasPermission(PERMS.get(sub))
                    || sub.equals("help") || sub.equals("reload") || sub.equals("version")) {
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
