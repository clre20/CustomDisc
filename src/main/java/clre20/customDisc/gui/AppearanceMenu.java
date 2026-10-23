package clre20.customDisc.gui;

import clre20.customDisc.data.CustomSound;
import clre20.customDisc.data.SoundType;
import clre20.customDisc.util.Vanilla;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MusicInstrumentMeta;

import java.util.ArrayList;
import java.util.List;

/** 步驟 1：選擇外觀（原版唱片或 8 種山羊角）。 */
final class AppearanceMenu extends EditorMenu {

    private static final int PER_PAGE = 45;
    private final int page;

    AppearanceMenu(EditSession session, int page) {
        super(session, 6, "gui.appearance.title", 1);
        this.page = page;
    }

    @Override
    protected void build() {
        CustomSound d = session.draft();
        List<String> options = new ArrayList<>();
        if (d.type() == SoundType.DISC) {
            for (Material m : Vanilla.discs()) {
                options.add(m.name());
            }
        } else {
            options.addAll(Vanilla.HORNS);
        }
        int pages = Math.max(1, (options.size() + PER_PAGE - 1) / PER_PAGE);
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(options.size(), from + PER_PAGE); i++) {
            String option = options.get(i);
            boolean selected = option.equals(d.appearance());
            ItemStack icon = d.type() == SoundType.DISC ? discIcon(option, selected) : hornIcon(option, selected);
            set(i - from, selected ? glow(icon) : icon, p -> {
                d.appearance(option);
                editor().afterAppearance(p, session);
            });
        }

        back(45, null);
        if (page > 0) {
            set(48, icon(Material.ARROW, text("gui.previous-page"), List.of()), p -> new AppearanceMenu(session, page - 1).open(p));
        }
        stepIndicator(49);
        if (page < pages - 1) {
            set(50, icon(Material.ARROW, text("gui.next-page"), List.of()), p -> new AppearanceMenu(session, page + 1).open(p));
        }
        fill(45, 53);
    }

    private ItemStack discIcon(String material, boolean selected) {
        return icon(new ItemStack(Vanilla.material(material)),
                text("gui.appearance.disc-name", "name", Vanilla.discName(material)),
                join(lines("gui.appearance.disc-lore", "comparator", Vanilla.comparator(material)),
                        one(""), one(selectLine(selected))));
    }

    private ItemStack hornIcon(String horn, boolean selected) {
        ItemStack item = new ItemStack(Material.GOAT_HORN);
        if (item.getItemMeta() instanceof MusicInstrumentMeta meta) {
            meta.setInstrument(Vanilla.instrument(horn));
            item.setItemMeta(meta);
        }
        return icon(item, text("gui.appearance.horn-name", "name", Vanilla.hornName(horn)),
                join(lines("gui.appearance.horn-lore"), one(""), one(selectLine(selected))));
    }
}
