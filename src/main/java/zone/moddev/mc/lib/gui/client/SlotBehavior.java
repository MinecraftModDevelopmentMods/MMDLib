package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;

/** Ghost selection and reservation tooltips; real inventory gestures remain vanilla-owned. */
public class SlotBehavior extends DefaultElementBehavior implements IngredientDropHandler {
    private final SlotElement slot;

    public SlotBehavior(SlotElement slot, ElementAccess access) {
        super(access);
        this.slot = slot;
    }

    protected boolean directInteraction() {
        return false;
    }

    protected Click click(double x, double y, int button) {
        if (selectionAction() == null)
            return Click.PASS;
        if (access.pending(slot))
            return Click.CONSUME;
        var carried = access.carried().copy();
        if (!slot.acceptsSelection(button, carried.copy()))
            return Click.PASS;
        submitSelection(button, carried);
        return Click.CONSUME;
    }

    public List<Component> tooltip(int x, int y) {
        return slot.reservationTooltip(access.reservation(slot));
    }

    private ElementAction<ItemStack> selectionAction() {
        return slot.selectionAction()
            .filter(action -> slot.action(action.id()).orElse(null) == action)
            .orElse(null);
    }

    private boolean submitSelection(int button, ItemStack value) {
        var action = selectionAction();
        return action != null
            && !access.pending(slot)
            && slot.acceptsSelection(button, value.copy())
            && access.submit(action, slot.selection(button, value.copy()).copy());
    }

    public <T> List<IngredientDropHandler.Target<T>> targets(IngredientType<T> type, T ingredient) {
        if (selectionAction() == null || access.pending(slot))
            return List.of();
        var selection = slot.selectionIngredient(type.id(), type.snapshot(ingredient));
        if (selection.isEmpty() || !slot.acceptsSelection(0, selection.get().copy()))
            return List.of();
        return List.of(
            new IngredientDropHandler.Target<>(
                "selection",
                access.bounds(),
                value -> slot.selectionIngredient(type.id(), type.snapshot(value))
                    .map(stack -> submitSelection(0, stack))
                    .orElse(false)));
    }
}
