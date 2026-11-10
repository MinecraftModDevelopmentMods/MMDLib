package zone.moddev.mc.lib.gui.runtime.slot;

import java.util.*;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.runtime.component.*;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Owns reservation sampling, wire encoding and client slot adaptation for every contributor. */
public final class ReservationMenuComponent implements MenuSnapshot {
  private static final int MAX_SLOTS = 32767;

  public static final ComponentSync<Map<Integer, SlotPlan>> PLANS =
      new ComponentSync<>(
          ResourceLocation.parse("mmdlib:reservation_plans"),
          1,
          StreamCodec.<RegistryFriendlyByteBuf, Map<Integer, SlotPlan>>of(
              (buffer, plans) -> {
                buffer.writeVarInt(plans.size());
                new TreeMap<>(plans)
                    .forEach(
                        (slot, plan) -> {
                          buffer.writeVarInt(slot);
                          buffer.writeVarInt(plan.take());
                          ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, plan.add());
                        });
              },
              buffer -> {
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_SLOTS)
                  throw new IllegalArgumentException("Too many reservation plans");
                var plans = new HashMap<Integer, SlotPlan>();
                for (int i = 0; i < count; i++) {
                  int slot = buffer.readVarInt();
                  if (slot < 0 || slot >= MAX_SLOTS || plans.containsKey(slot))
                    throw new IllegalArgumentException("Invalid reservation slot: " + slot);
                  int take = buffer.readVarInt();
                  if (take < 0) throw new IllegalArgumentException("Negative reserved input count");
                  plans.put(
                      slot, new SlotPlan(take, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));
                }
                return Map.copyOf(plans);
              }),
          65536,
          Map::of,
          plans ->
              plans.size() <= MAX_SLOTS
                  && plans.entrySet().stream()
                      .allMatch(
                          e ->
                              e.getKey() != null
                                  && e.getKey() >= 0
                                  && e.getKey() < MAX_SLOTS
                                  && e.getValue() != null));

  private final MenuSlots slots;
  private final boolean client;

  public ReservationMenuComponent(MenuSlots slots, boolean client) {
    this.slots = Objects.requireNonNull(slots);
    this.client = client;
  }

  public void snapshot(ComponentSnapshot values) {
    var plans = new HashMap<Integer, SlotPlan>();
    for (var entry : slots.entries())
      if (entry.reservations()) plans.put(entry.slot().index, entry.plan());
    values.put(PLANS, plans);
  }

  public void applied(ComponentStateView values) {
    if (!client) return;
    var plans = values.get(PLANS);
    // This is a complete field snapshot. Omitted plans must clear any previous client reservation.
    for (var entry : slots.entries())
      if (entry.reservations())
        ((SlotAdapter) entry.slot())
            .applyPlan(plans.getOrDefault(entry.slot().index, SlotPlan.NONE));
  }
}
