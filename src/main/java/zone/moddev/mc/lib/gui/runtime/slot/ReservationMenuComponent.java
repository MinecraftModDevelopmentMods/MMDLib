package zone.moddev.mc.lib.gui.runtime.slot;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.gui.runtime.component.ComponentSnapshot;
import zone.moddev.mc.lib.gui.runtime.component.ComponentStateView;
import zone.moddev.mc.lib.gui.runtime.component.ComponentSync;
import zone.moddev.mc.lib.gui.runtime.component.MenuSnapshot;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.inventory.TankPlan;

/**
 * Owns reservation sampling, wire encoding and client slot adaptation.
 */
public final class ReservationMenuComponent implements MenuSnapshot {
    private static final int MAX_SLOTS = 32767;

    public static final ComponentSync<Map<Integer, SlotPlan>> PLANS = new ComponentSync<>(
        ResourceLocation.parse("mmdlib:reservation_plans"), 1,
        StreamCodec.<RegistryFriendlyByteBuf, Map<Integer, SlotPlan>>of(
            (buffer, plans) -> {
                buffer.writeVarInt(plans.size());
                new TreeMap<>(plans).forEach((slot, plan) -> {
                    buffer.writeVarInt(slot);
                    buffer.writeVarInt(plan.take());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, plan.add());
                });
            }, buffer -> {
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_SLOTS)
                    throw new IllegalArgumentException(
                        "Too many reservation plans");
                var plans = new HashMap<Integer, SlotPlan>();
                for (int i = 0; i < count; i++) {
                    int slot = buffer.readVarInt();
                    if (slot < 0 || slot >= MAX_SLOTS
                        || plans.containsKey(slot))
                        throw new IllegalArgumentException(
                            "Invalid reservation slot: " + slot);
                    int take = buffer.readVarInt();
                    if (take < 0)
                        throw new IllegalArgumentException(
                            "Negative reserved input count");
                    plans.put(slot, new SlotPlan(take,
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));
                }
                return Map.copyOf(plans);
            }),
        65536, Map::of,
        plans -> plans.size() <= MAX_SLOTS && plans.entrySet().stream()
            .allMatch(e -> e.getKey() != null && e.getKey() >= 0
                && e.getKey() < MAX_SLOTS && e.getValue() != null));

    public static final ComponentSync<Map<Integer, TankPlan>> TANK_PLANS = new ComponentSync<>(
        ResourceLocation.parse("mmdlib:tank_reservation_plans"), 1,
        ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.VAR_INT,
            StreamCodec.composite(ByteBufCodecs.VAR_INT, TankPlan::take,
                FluidAmount.STREAM_CODEC, TankPlan::add, TankPlan::new),
            MAX_SLOTS),
        65536, Map::of,
        plans -> plans.size() <= MAX_SLOTS
            && plans.entrySet().stream().allMatch(e -> e.getKey() >= 0
                && e.getKey() < MAX_SLOTS && e.getValue() != null));

    private final MenuSlots slots;
    private final boolean client;
    private final FluidStorage fluids;
    private Map<Integer, TankPlan> tankPlans = Map.of();

    public ReservationMenuComponent(MenuSlots slots, boolean client) {
        this(slots, client, null);
    }

    public ReservationMenuComponent(MenuSlots slots, boolean client,
        FluidStorage fluids) {
        this.slots = Objects.requireNonNull(slots);
        this.client = client;
        this.fluids = fluids;
    }

    public void snapshot(ComponentSnapshot values) {
        var plans = new HashMap<Integer, SlotPlan>();
        for (var entry : slots.entries())
            if (entry.reservations())
                plans.put(entry.slot().index, entry.plan());
        values.put(PLANS, plans);
        if (fluids != null) {
            var tanks = new LinkedHashMap<Integer, TankPlan>();
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                var plan = fluids.plan(tank);
                if (!plan.isNone())
                    tanks.put(tank, plan);
            }
            values.put(TANK_PLANS, tanks);
        }
    }

    public void applied(ComponentStateView values) {
        if (!client)
            return;
        if (fluids != null)
            tankPlans = Map.copyOf(values.get(TANK_PLANS));
        var plans = values.get(PLANS);
        // This is a complete field snapshot. Omitted plans must clear any
        // previous client
        // reservation.
        for (var entry : slots.entries())
            if (entry.reservations())
                ((SlotAdapter) entry.slot()).applyPlan(
                    plans.getOrDefault(entry.slot().index, SlotPlan.NONE));
    }

    public TankPlan tankPlan(int tank) {
        return tankPlans.getOrDefault(tank, TankPlan.NONE);
    }
}
