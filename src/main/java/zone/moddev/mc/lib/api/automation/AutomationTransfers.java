package zone.moddev.mc.lib.api.automation;

import static zone.moddev.mc.lib.api.automation.AutomationInventory.COLUMNS;

import java.util.*;
import java.util.function.Predicate;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import zone.moddev.mc.lib.api.capabilities.*;

/**
 * Server-only, paid transfer scheduler. Extracted resources remain owned by
 * persistent escrow until a destination accepts them, including handlers that
 * disagree with their simulation.
 */
public final class AutomationTransfers {
    public interface Transport<T> {
        BlockCapability<T, Direction> capability();

        /** Override for a custom resource's escrow schema. */
        default boolean pending(CompoundTag state) {
            return state.contains("item") || state.contains("fluid")
                || state.contains("energy");
        }

        /**
         * Delivers escrow first; otherwise stages one bounded extraction.
         * Returns actual delivery. Implementations must preserve every
         * unaccepted resource in escrow and respect max.
         */
        int move(T from, T to, int max, Predicate<Object> filter,
            CompoundTag escrow, HolderLookup.Provider registries);

        /**
         * Deliver already-owned resources without consulting or extracting from
         * the source.
         */
        int deliver(T to, int max, CompoundTag escrow,
            HolderLookup.Provider registries);

        /**
         * Resource-only payload handed to the host on break; custom transports
         * may keep their schema.
         */
        default CompoundTag recovery(CompoundTag escrow) {
            return pending(escrow) ? escrow.copy() : new CompoundTag();
        }
    }

    private static final Map<ResourceType<?>, Transport<?>> TRANSPORTS = new IdentityHashMap<>();

    public static synchronized <T> void register(ResourceType<T> type,
        Transport<T> transport) {
        if (TRANSPORTS.putIfAbsent(type, transport) != null)
            throw new IllegalArgumentException("Duplicate transport");
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<Transport<T>> transport(ResourceType<T> type) {
        return Optional.ofNullable((Transport<T>) TRANSPORTS.get(type));
    }

    static {
        register(ResourceType.ITEMS, new Transport<IItemHandler>() {
            public BlockCapability<IItemHandler, Direction> capability() {
                return Capabilities.ItemHandler.BLOCK;
            }

            public int move(IItemHandler from, IItemHandler to, int max,
                Predicate<Object> filter, CompoundTag escrow,
                HolderLookup.Provider registries) {
                ItemStack staged = ItemStack.parseOptional(registries,
                    escrow.getCompound("item"));
                if (staged.isEmpty()) {
                    int start = escrow.getInt("cursor");
                    for (int n = 0; n < from.getSlots(); n++) {
                        int slot = Math.floorMod(start + n, from.getSlots());
                        var offered = from.extractItem(slot, max, true);
                        if (offered.isEmpty() || !filter.test(offered))
                            continue;
                        int accepted = offered.getCount()
                            - Transfer.insert(to, offered, true).getCount();
                        if (accepted <= 0)
                            continue;
                        staged = from.extractItem(slot, Math.min(max, accepted),
                            false);
                        escrow.putInt("cursor", slot + 1);
                        break;
                    }
                }
                if (staged.isEmpty())
                    return 0;
                // Persist before invoking an external handler, and keep any
                // remainder.
                escrow.put("item", staged.saveOptional(registries));
                return deliver(to, max, escrow, registries);
            }

            public int deliver(IItemHandler to, int max, CompoundTag escrow,
                HolderLookup.Provider registries) {
                var staged = ItemStack.parseOptional(registries,
                    escrow.getCompound("item"));
                if (staged.isEmpty())
                    return 0;
                var offered = staged
                    .copyWithCount(Math.min(max, staged.getCount()));
                int delivered = offered.getCount()
                    - Transfer.insert(to, offered, false).getCount();
                staged.shrink(delivered);
                if (staged.isEmpty())
                    escrow.remove("item");
                else
                    escrow.put("item", staged.saveOptional(registries));
                return delivered;
            }

            public CompoundTag recovery(CompoundTag escrow) {
                return payload("item", escrow);
            }
        });
        register(ResourceType.FLUIDS, new Transport<IFluidHandler>() {
            public BlockCapability<IFluidHandler, Direction> capability() {
                return Capabilities.FluidHandler.BLOCK;
            }

            public int move(IFluidHandler from, IFluidHandler to, int max,
                Predicate<Object> filter, CompoundTag escrow,
                HolderLookup.Provider registries) {
                var staged = FluidStack.parseOptional(registries,
                    escrow.getCompound("fluid"));
                if (staged.isEmpty()) {
                    int start = escrow.getInt("cursor");
                    for (int n = 0; n < from.getTanks(); n++) {
                        int tank = Math.floorMod(start + n, from.getTanks());
                        var candidate = from.getFluidInTank(tank).copy();
                        if (candidate.isEmpty() || !filter.test(candidate))
                            continue;
                        candidate
                            .setAmount(Math.min(max, candidate.getAmount()));
                        var offered = from.drain(candidate,
                            IFluidHandler.FluidAction.SIMULATE);
                        int accepted = to.fill(offered,
                            IFluidHandler.FluidAction.SIMULATE);
                        if (accepted <= 0)
                            continue;
                        offered.setAmount(Math.min(max, accepted));
                        staged = from.drain(offered,
                            IFluidHandler.FluidAction.EXECUTE);
                        escrow.putInt("cursor", tank + 1);
                        break;
                    }
                }
                if (staged.isEmpty())
                    return 0;
                escrow.put("fluid", staged.save(registries));
                return deliver(to, max, escrow, registries);
            }

            public int deliver(IFluidHandler to, int max, CompoundTag escrow,
                HolderLookup.Provider registries) {
                var staged = FluidStack.parseOptional(registries,
                    escrow.getCompound("fluid"));
                if (staged.isEmpty())
                    return 0;
                var offered = staged.copy();
                offered.setAmount(Math.min(max, staged.getAmount()));
                int delivered = to.fill(offered,
                    IFluidHandler.FluidAction.EXECUTE);
                staged.shrink(delivered);
                if (staged.isEmpty())
                    escrow.remove("fluid");
                else
                    escrow.put("fluid", staged.save(registries));
                return delivered;
            }

            public CompoundTag recovery(CompoundTag escrow) {
                return payload("fluid", escrow);
            }
        });
        register(ResourceType.ENERGY, new Transport<IEnergyStorage>() {
            public BlockCapability<IEnergyStorage, Direction> capability() {
                return Capabilities.EnergyStorage.BLOCK;
            }

            public int move(IEnergyStorage from, IEnergyStorage to, int max,
                Predicate<Object> filter, CompoundTag escrow,
                HolderLookup.Provider registries) {
                int staged = escrow.getInt("energy");
                if (staged == 0) {
                    int offered = from.extractEnergy(max, true);
                    int accepted = to.receiveEnergy(offered, true);
                    if (accepted > 0)
                        staged = from.extractEnergy(Math.min(max, accepted),
                            false);
                }
                escrow.putInt("energy", staged);
                return deliver(to, max, escrow, registries);
            }

            public int deliver(IEnergyStorage to, int max, CompoundTag escrow,
                HolderLookup.Provider registries) {
                int staged = escrow.getInt("energy");
                int delivered = to.receiveEnergy(Math.min(max, staged), false);
                if (staged == delivered)
                    escrow.remove("energy");
                else
                    escrow.putInt("energy", staged - delivered);
                return delivered;
            }

            public CompoundTag recovery(CompoundTag escrow) {
                return escrow.getInt("energy") > 0
                    ? payload("energy", escrow)
                    : new CompoundTag();
            }
        });
    }

    private AutomationTransfers() {
    }

    private static CompoundTag payload(String key, CompoundTag escrow) {
        var payload = new CompoundTag();
        if (escrow.contains(key))
            payload.put(key, escrow.get(key).copy());
        return payload;
    }

    /**
     * Preserve unknown transport state too, so removing a mod cannot destroy
     * its pending resources.
     */
    public static CompoundTag recovery(String type, CompoundTag state) {
        try {
            var transport = TRANSPORTS.get(ResourceType
                .require(net.minecraft.resources.ResourceLocation.parse(type)));
            return transport == null ? state.copy() : transport.recovery(state);
        } catch (IllegalArgumentException unknown) {
            return state.copy();
        }
    }

    private static boolean pending(ResourceType<?> type, CompoundTag state) {
        var transport = TRANSPORTS.get(type);
        return transport != null && transport.pending(state);
    }

    /**
     * Call once per server tick, after recipe processing. A rotating start
     * prevents slot starvation.
     */
    public static void tick(AutomationInventory inventory, EnergyBuffer power) {
        var level = inventory.owner().getLevel();
        if (level == null || level.isClientSide)
            return;
        inventory.revalidate();
        inventory.recover(null);
        inventory.settleRefund(power);
        int total = inventory.groups().size() * COLUMNS;
        if (total == 0)
            return;
        int start = (int) Math.floorMod(level.getGameTime(), total);
        for (int n = 0; n < total; n++) {
            int index = (start + n) % total, g = index / COLUMNS,
                c = index % COLUMNS;
            var group = inventory.group(g);
            var stack = inventory.addon(g, c);
            var addon = stack.getCapability(AddonCapabilities.ADDON);
            var state = inventory.existingTransferState(group.id(), c);
            boolean pending = state != null && pending(group.type(), state);
            AddonSpec spec = addon == null ? null : addon.specification(stack);
            if (!pending && (spec == null || !spec.accepts(group)))
                continue;
            if (state == null)
                state = inventory.transferState(group.id(), c);
            long cost = pending ? state.getLong("cost") : spec.energyPerUnit();
            int batch = pending ? state.getInt("batch") : spec.batch();
            if (!pending && Math.floorMod(level.getGameTime() + index,
                spec.interval()) != 0)
                continue;
            int max = (int) Math.min(batch,
                cost == 0 ? batch : power == null ? 0 : power.stored() / cost);
            if (max <= 0)
                continue;
            var filter = inventory.filter(g, c);
            var predicate = filter.getCapability(AddonCapabilities.FILTER);
            if (!pending && !filter.isEmpty()
                && (predicate == null || !addon.acceptsFilter(stack, filter)))
                continue;
            Predicate<Object> accepts = value -> predicate == null
                || predicate.matches(filter, value, level.registryAccess());
            int first = state.getInt("face");
            boolean both = !pending && spec.direction() == IoMode.BOTH;
            for (int offset = 0; offset < (both ? 12 : 6); offset++) {
                int face = Math.floorMod(first + offset / (both ? 2 : 1), 6);
                var direction = group.world(RelativeFace.values()[face]);
                boolean input = pending
                    ? state.getBoolean("input")
                    : both
                        ? ((level.getGameTime() / spec.interval() + index
                            + offset) & 1) == 0
                        : spec.direction().canInsert();
                if (!(pending && input) && (input
                    ? !group.mode(direction).canInsert()
                    : !group.mode(direction).canExtract()))
                    continue;
                var transport = TRANSPORTS.get(group.type());
                if (transport == null || (!(pending && input) && inventory
                    .neighbour(transport.capability(), direction) == null))
                    continue;
                if (!pending) {
                    state.putBoolean("input", input);
                    state.putLong("cost", cost);
                    state.putInt("batch", batch);
                    state.putInt("face", face);
                }
                long reserved = cost * max;
                if (reserved > 0 && (power == null || !power.reserve(reserved)))
                    break;
                int moved;
                try {
                    moved = move(group, inventory, direction, input, pending,
                        max, accepts, state);
                } catch (RuntimeException failure) {
                    inventory.refund(power, reserved);
                    inventory.owner().setChanged();
                    throw failure;
                }
                inventory.refund(power, reserved - cost * moved);
                inventory.owner().setChanged();
                if (moved > 0 || (pending && input)
                    || (!pending && pending(group.type(), state))) {
                    if (!pending(group.type(), state))
                        state.putInt("face", (face + 1) % 6);
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> int move(ResourceGroup<T> group,
        AutomationInventory inventory, Direction direction, boolean input,
        boolean pending, int max, Predicate<Object> filter,
        CompoundTag escrow) {
        var transport = (Transport<T>) TRANSPORTS.get(group.type());
        if (transport == null)
            return 0;
        var level = inventory.owner().getLevel();
        if (pending && input)
            return transport.deliver(group.port(null), max, escrow,
                level.registryAccess());
        T neighbour = inventory.neighbour(transport.capability(), direction);
        if (neighbour == null)
            return 0;
        if (pending)
            return transport.deliver(neighbour, max, escrow,
                level.registryAccess());
        T local = group.port(direction);
        return transport.move(input ? neighbour : local,
            input ? local : neighbour, max, filter, escrow,
            level.registryAccess());
    }
}
