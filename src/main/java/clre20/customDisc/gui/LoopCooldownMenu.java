package clre20.customDisc.gui;

import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import org.bukkit.Material;

/** 步驟 4：唱片設定循環播放；號角設定冷卻秒數。 */
final class LoopCooldownMenu extends EditorMenu {

    LoopCooldownMenu(EditSession session) {
        super(session, 3, session.draft().type() == SoundType.DISC ? "gui.loop.title" : "gui.cooldown.title", 4);
    }

    @Override
    protected void build() {
        CustomSound d = session.draft();
        if (d.type() == SoundType.DISC) {
            set(13, d.loop()
                            ? glow(icon(Material.LIME_DYE, text("gui.loop.on-name"), lines("gui.loop.on-lore")))
                            : icon(Material.GRAY_DYE, text("gui.loop.off-name"), lines("gui.loop.off-lore")),
                    p -> {
                        d.loop(!d.loop());
                        redraw();
                    });
        } else {
            int min = d.minCooldown();
            adjuster(10, d.cooldown(), min, 3600, d::cooldown,
                    icon(Material.CLOCK, text("gui.cooldown.value-name", "seconds", d.cooldown()),
                            lines("gui.cooldown.value-lore", "min", min)));
        }

        back(18, p -> editor().soundFiles(p));
        stepIndicator(22);
        next(26, p -> editor().volumeRange(p));
        fill(0, 26);
    }
}
