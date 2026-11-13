package zone.moddev.mc.lib.machine.feature;

import com.mojang.serialization.Codec;

import net.minecraft.world.item.ItemStack;

/**
 * Optional base for processors whose explicitly portable variables travel with
 * their item.
 */
public abstract class PortableStoredProcessor extends StoredProcessor
    implements PortableProcessor {
    private final ProcessorState.Key<Boolean> taken = variables.define("taken",
        Codec.BOOL, false);

    public final boolean taken() {
        return variables.get(taken);
    }

    @Override
    public final ItemStack take() {
        if (taken() || machine == null || machine.level() == null
            || machine.level().isClientSide())
            return ItemStack.EMPTY;
        var stack = machine.item();
        if (stack.isEmpty())
            return ItemStack.EMPTY;
        // Serialize completely before transferring ownership or clearing the
        // source.
        PortableData.write(stack, key(), variables.savePortable());
        variables.update(() -> {
            variables.set(taken, true);
            variables.clearPortable();
        });
        return stack;
    }

    @Override
    public final void placed(ItemStack stack) {
        variables.update(() -> {
            variables.loadPortable(PortableData.read(stack, key()));
            variables.set(taken, false);
        });
    }

    @Override
    public final boolean hasContent(ItemStack stack) {
        // Use the schema without modifying the shared item-side processor
        // prototype.
        var initial = variables.portableDefaults();
        var carried = PortableData.read(stack, key());
        for (var name : carried.getAllKeys())
            if (!java.util.Objects.equals(carried.get(name), initial.get(name)))
                return true;
        return false;
    }

    @Override
    public void creativeDestroy() {
        if (!variables.hasPortableContent())
            take();
    }
}
