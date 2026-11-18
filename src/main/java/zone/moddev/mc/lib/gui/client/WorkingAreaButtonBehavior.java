package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.gui.model.elements.WorkingAreaButtonElement;

/**
 * Purely local selection: toggling an outline does not change machine
 * operation.
 */
public final class WorkingAreaButtonBehavior implements ElementBehavior {
    private final ElementAccess access;
    public WorkingAreaButtonBehavior(WorkingAreaButtonElement element,
        ElementAccess access) {
        this.access = access;
    }
    private CompoundTag data() {
        return access.presentation().state().get(access.element(),
            WorkingAreaButtonElement.AREA);
    }
    public Click mouseClicked(double x, double y, int button) {
        if (button != 0)
            return Click.PASS;
        WorkingAreaDisplay.toggle(data());
        ClickSounds.play(access.element());
        return Click.CONSUME;
    }
    public void tick() {
        WorkingAreaDisplay.update(data());
    }
    public List<Component> tooltip(int x, int y) {
        return List.of(Component.translatable(WorkingAreaDisplay.visible(data())
            ? "screen.mmdlib.working_area.hide"
            : "screen.mmdlib.working_area.show"));
    }
    public static void render(WorkingAreaButtonElement element,
        ElementRenderers.Context context) {
        ButtonRenderer.draw(element, context);
        var data = context.access().state().get(element,
            WorkingAreaButtonElement.AREA);
        int colour = WorkingAreaDisplay.visible(data)
            ? 0xff000000 | WorkingAreaDisplay.color(data)
            : 0xff808080;
        var bounds = context.bounds();
        var g = context.graphics();
        g.renderOutline(bounds.x() + 2, bounds.y() + 2, 10, 10, colour);
        g.fill(bounds.x() + 6, bounds.y() + 4, bounds.x() + 7, bounds.y() + 10,
            colour);
        g.fill(bounds.x() + 4, bounds.y() + 6, bounds.x() + 10, bounds.y() + 7,
            colour);
    }
}
