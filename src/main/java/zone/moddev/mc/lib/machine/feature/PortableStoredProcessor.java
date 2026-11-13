package zone.moddev.mc.lib.machine.feature;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Optional base for processors whose explicitly portable variables travel with
 * their item.
 */
public abstract class PortableStoredProcessor extends StoredProcessor
    implements PortableProcessor {
    public static final ResourceLocation PICKUP = ResourceLocation
        .parse("mmdlib:pickup");
    private final ProcessorState.Key<Boolean> taken = variables.define("taken",
        Codec.BOOL, false);
    private Map<ResourceLocation, ProcessorAction> declaredActions;

    public final boolean taken() {
        return variables.get(taken);
    }

    /** Mod-owned commands; the library pickup action is always included. */
    protected Map<ResourceLocation, ProcessorAction> customActions() {
        return Map.of();
    }

    @Override
    public final Map<ResourceLocation, ProcessorAction> actions() {
        if (declaredActions != null)
            return declaredActions;
        var actions = new LinkedHashMap<>(customActions());
        if (actions.putIfAbsent(PICKUP, new ProcessorAction(
            (state, context) -> !taken(), (state, context) -> {
                if (!context.machine().authorizePickup(context.player()))
                    return ProcessorAction.Result.unavailable();
                var stack = take();
                if (stack.isEmpty())
                    return ProcessorAction.Result.unavailable();
                context.machine().remove();
                if (!context.machine().removed()) {
                    placed(stack);
                    return ProcessorAction.Result.unavailable();
                }
                context.player().getInventory().placeItemBackInInventory(stack);
                context.player().closeContainer();
                return ProcessorAction.Result.success();
            })) != null)
            throw new IllegalArgumentException(
                "The mmdlib:pickup action is reserved");
        declaredActions = Map.copyOf(actions);
        return declaredActions;
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
