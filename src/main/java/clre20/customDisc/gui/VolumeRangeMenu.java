package clre20.customDisc.gui;

import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import org.bukkit.Material;

import java.util.List;

/** 步驟 5：音量（0–100%）與可聽範圍（格）。 */
final class VolumeRangeMenu extends EditorMenu {

    VolumeRangeMenu(EditSession session) {
        super(session, 4, "gui.volume.title", 5);
    }

    @Override
    protected void build() {
        CustomSound d = session.draft();
        set(9, icon(Material.NOTE_BLOCK, text("gui.volume.volume-label-name"), lines("gui.volume.volume-label-lore")), null);
        adjuster(10, d.volume(), 0, 100, d::volume,
                icon(Material.NOTE_BLOCK, text("gui.volume.volume-name", "volume", d.volume()),
                        lines("gui.volume.volume-lore")));

        set(18, icon(Material.ENDER_EYE, text("gui.volume.range-label-name"), lines("gui.volume.range-label-lore")), null);
        List<String> rangeLore = lines("gui.volume.range-lore");
        if (d.type() == SoundType.DISC) {
            rangeLore.addAll(lines("gui.volume.range-lore-disc"));
        }
        adjuster(19, d.range(), 1, 512, d::range,
                icon(Material.ENDER_EYE, text("gui.volume.range-name", "range", d.range()), rangeLore));

        back(27, p -> editor().loopCooldown(p));
        stepIndicator(31);
        next(35, p -> editor().overview(p));
        fill(0, 35);
    }
}
