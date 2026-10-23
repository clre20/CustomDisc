package clre20.customDisc.gui;

import clre20.customDisc.CustomDisc;
import clre20.customDisc.config.Messages;
import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.pack.DatapackService;
import clre20.customDisc.pack.SoundLibrary;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 建立／編輯流程。
 * 建立：1 外觀 → 2 名稱與描述 → 3 音檔 → 4 循環／冷卻 → 5 音量與範圍 → 6 總覽確認。
 * 編輯：直接打開總覽頁，點哪一項就改哪一項，改完回到總覽。
 */
public final class Editor {

    public static final int STEPS = 6;

    private final CustomDisc plugin;
    private final Map<UUID, EditSession> sessions = new HashMap<>();

    public Editor(CustomDisc plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    // ------------------------------------------------------------ 開始／結束

    public void startCreate(Player player, SoundType type, String name) {
        end(player, false);
        CustomSound d = new CustomSound(type, name);
        if (type == SoundType.DISC) {
            d.appearance("MUSIC_DISC_CAT");
            d.volume(plugin.config().defaultDiscVolume());
            d.range(plugin.config().defaultDiscRange());
            d.loop(false);
            d.comparator(null);
        } else {
            d.appearance("PONDER");
            d.volume(plugin.config().defaultHornVolume());
            d.range(plugin.config().defaultHornRange());
            d.cooldown(plugin.config().defaultHornCooldown());
        }
        d.displayName(name);
        d.soundFile("");
        d.revision(0);
        EditSession s = new EditSession(player.getUniqueId(), d, true);
        sessions.put(player.getUniqueId(), s);
        appearance(player);
    }

    public void startEdit(Player player, CustomSound sound) {
        end(player, false);
        EditSession s = new EditSession(player.getUniqueId(), sound.copy(), false);
        sessions.put(player.getUniqueId(), s);
        overview(player);
    }

    public EditSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** 結束編輯（不儲存）。 */
    public void end(Player player, boolean message) {
        EditSession s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        stopPreview(player, s);
        plugin.chat().cancelSilently(player.getUniqueId());
        if (message) {
            msg().send(player, "edit.cancelled", "action", msg().get(s.creating() ? "edit.action-create" : "edit.action-edit"));
        }
    }

    public void endAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                p.closeInventory();
                end(p, true);
            }
        }
        sessions.clear();
    }

    /** 介面被關掉一個 tick 後檢查：不是換頁也不是在等輸入，就當作取消。 */
    void checkClosed(Player player, EditSession s) {
        if (sessions.get(player.getUniqueId()) != s || plugin.chat().waiting(player)) {
            return;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu m && m.session == s) {
            return;
        }
        end(player, true);
    }

    private EditSession require(Player player) {
        return sessions.get(player.getUniqueId());
    }

    // ------------------------------------------------------------ 各頁面

    public void appearance(Player player) {
        EditSession s = require(player);
        if (s != null) {
            new AppearanceMenu(s, 0).open(player);
        }
    }

    public void overview(Player player) {
        EditSession s = require(player);
        if (s != null) {
            s.reachedOverview();
            new OverviewMenu(s).open(player);
        }
    }

    /** 選擇音檔頁：先在背景重新掃描 sounds 資料夾，再回主執行緒開介面。 */
    public void soundFiles(Player player) {
        EditSession s = require(player);
        if (s == null) {
            return;
        }
        s.navigating(true);
        java.util.UUID id = player.getUniqueId();
        plugin.library().scanAsync().thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            s.navigating(false);
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && sessions.get(id) == s) {
                new SoundFileMenu(s, 0).open(p);
            }
        }));
    }

    public void loopCooldown(Player player) {
        EditSession s = require(player);
        if (s != null) {
            new LoopCooldownMenu(s).open(player);
        }
    }

    public void volumeRange(Player player) {
        EditSession s = require(player);
        if (s != null) {
            new VolumeRangeMenu(s).open(player);
        }
    }

    public void comparator(Player player) {
        EditSession s = require(player);
        if (s != null) {
            new ComparatorMenu(s).open(player);
        }
    }

    /** 建立流程：選完外觀之後。 */
    void afterAppearance(Player player, EditSession s) {
        if (s.wizard()) {
            promptName(player);
        } else {
            overview(player);
        }
    }

    public void promptName(Player player) {
        EditSession s = require(player);
        if (s == null) {
            return;
        }
        String cancel = plugin.config().cancelWord();
        plugin.chat().ask(player, text -> {
            if (text.isBlank()) {
                msg().send(player, "edit.name-empty");
                promptName(player);
                return;
            }
            s.draft().displayName(text);
            msg().send(player, "edit.name-set", "display", text);
            if (s.wizard()) {
                promptLore(player);
            } else {
                overview(player);
            }
        }, () -> {
            if (s.wizard()) {
                appearance(player);
            } else {
                overview(player);
            }
        });
        player.closeInventory();
        step(player, s, 2);
        msg().send(player, "edit.name-ask");
        msg().send(player, "edit.name-current", "display", s.draft().displayName(), "cancel", cancel);
    }

    public void promptLore(Player player) {
        EditSession s = require(player);
        if (s == null) {
            return;
        }
        String cancel = plugin.config().cancelWord();
        String clear = msg().get("edit.lore-clear-word");
        plugin.chat().ask(player, text -> {
            List<String> lines = new ArrayList<>();
            if (!text.equals(clear)) {
                for (String line : text.split("\\|")) {
                    lines.add(line.trim());
                }
            }
            s.draft().lore(lines);
            if (lines.isEmpty()) {
                msg().send(player, "edit.lore-cleared");
            } else {
                msg().send(player, "edit.lore-set", "lines", lines.size());
            }
            if (s.wizard()) {
                soundFiles(player);
            } else {
                overview(player);
            }
        }, () -> {
            if (s.wizard()) {
                promptName(player);
            } else {
                overview(player);
            }
        });
        player.closeInventory();
        step(player, s, 2);
        msg().send(player, "edit.lore-ask");
        msg().send(player, "edit.lore-hint", "clear", clear, "cancel", cancel);
    }

    private void step(Player player, EditSession s, int step) {
        if (s.wizard()) {
            msg().send(player, "edit.step", "type", s.draft().type().displayName(), "name", s.draft().name(),
                    "step", step, "total", STEPS);
        }
    }

    /** 選完音檔。 */
    void afterSoundFile(Player player, EditSession s, SoundLibrary.Entry entry) {
        CustomSound d = s.draft();
        d.soundFile(entry.path());
        d.length(entry.info().seconds());
        msg().send(player, "edit.sound-selected", "file", entry.path(), "time", formatTime(entry.info().seconds()));
        if (entry.info().stereo()) {
            msg().send(player, "edit.sound-stereo");
        }
        if (d.type() == SoundType.HORN && d.cooldown() < d.minCooldown()) {
            d.cooldown(d.minCooldown());
            msg().send(player, "edit.cooldown-raised", "seconds", d.cooldown());
        }
        if (s.wizard()) {
            loopCooldown(player);
        } else {
            overview(player);
        }
    }

    // ------------------------------------------------------------ 試聽

    void togglePreview(Player player, EditSession s) {
        if (s.previewEvent() != null) {
            stopPreview(player, s);
            return;
        }
        SoundLibrary.Entry e = plugin.library().find(s.draft().soundFile());
        if (e == null || !e.valid()) {
            msg().send(player, "edit.preview-no-file");
            return;
        }
        if (!plugin.packs().contains(e.id())) {
            msg().send(player, "edit.preview-new-file");
            return;
        }
        String event = DatapackService.NAMESPACE + ":file." + e.id();
        player.playSound(player.getLocation(), event, SoundCategory.RECORDS, s.draft().volume() / 100f, 1.0f);
        s.previewEvent(event);
        msg().send(player, "edit.preview-playing");
    }

    void stopPreview(Player player, EditSession s) {
        if (s.previewEvent() != null) {
            player.stopSound(s.previewEvent(), SoundCategory.RECORDS);
            s.previewEvent(null);
        }
    }

    // ------------------------------------------------------------ 儲存

    void save(Player player, EditSession s) {
        CustomSound d = s.draft();
        SoundLibrary.Entry e = plugin.library().find(d.soundFile());
        if (e == null || !e.valid()) {
            msg().send(player, "edit.save-no-file");
            return;
        }
        d.length(e.info().seconds());
        if (d.type() == SoundType.HORN && d.cooldown() < d.minCooldown()) {
            d.cooldown(d.minCooldown());
        }
        if (s.creating() && plugin.store().exists(d.type(), d.name())) {
            msg().send(player, "edit.save-exists", "name", d.name());
            return;
        }
        plugin.store().save(d);
        sessions.remove(player.getUniqueId());
        stopPreview(player, s);
        player.closeInventory();

        CustomSound saved = plugin.store().get(d.type(), d.name());
        plugin.applyChanges();
        msg().send(player, s.creating() ? "edit.save-created" : "edit.save-saved",
                "type", d.type().displayName(), "name", d.name());
        if (plugin.datapack().nativeAfterRestart(saved)) {
            msg().send(player, "edit.save-restart-hint");
        }
        if (s.creating()) {
            msg().send(player, "edit.save-give-hint", "id", d.type().id(), "name", d.name());
        }
    }

    static String formatTime(double seconds) {
        int total = (int) Math.round(seconds);
        return total / 60 + ":" + String.format("%02d", total % 60);
    }
}
