package zone.moddev.mc.lib.gui.runtime.slot;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Read-only routing inputs. Returned targets remain subject to the menu's transfer checks. */
public final class SlotTransferContext {
    private final MenuSlots.Entry source;
    private final List<MenuSlots.Entry> entries;
    private final ItemStack stack;
    private final Function<ItemStack, List<Slot>> destinations;

    public SlotTransferContext(
        MenuSlots.Entry source,
        List<MenuSlots.Entry> entries,
        ItemStack stack,
        Function<ItemStack, List<Slot>> destinations) {
        this.source = Objects.requireNonNull(source);
        this.entries = List.copyOf(entries);
        this.stack = stack.copy();
        this.destinations = Objects.requireNonNull(destinations);
    }

    public MenuSlots.Entry source() {
        return source;
    }

    /** Native registration order, with each slot's element and role. Do not mutate native slots. */
    public List<MenuSlots.Entry> entries() {
        return entries;
    }

    /** A display/routing copy, never the stack being moved. */
    public ItemStack stack() {
        return stack.copy();
    }

    /** Feature-provided insertion targets, evaluated only when requested. */
    public List<Slot> machineDestinations() {
        return List.copyOf(destinations.apply(stack.copy()));
    }
}
