package zone.moddev.mc.lib.smoke;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.gui.runtime.ScreenNetwork;
import zone.moddev.mc.lib.gui.runtime.ScreenValues;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.gui.runtime.component.ComponentStates;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentRegistry;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;
import zone.moddev.mc.lib.gui.runtime.slot.ReservationMenuComponent;
import zone.moddev.mc.lib.gui.runtime.slot.SlotAdapter;
import zone.moddev.mc.lib.gui.sync.ElementStateValue;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Registered reservation state supports any mod slot adapter, independent of storage layout. */
final class ReservationSyncSmoke {
    private static final class Probe extends Slot implements SlotAdapter {
        SlotPlan plan = SlotPlan.NONE;
        int applied;

        Probe() {
            super(new SimpleContainer(1), 0, 0, 0);
        }

        public SlotPlan plan() {
            return plan;
        }

        public void applyPlan(SlotPlan plan) {
            this.plan = plan;
            applied++;
        }
    }

    private record Fixture(
        ReservationMenuComponent component,
        ComponentStates state,
        Probe input,
        Probe output,
        Probe player) {
    }

    private static Fixture fixture(RegistryAccess registries, boolean client) {
        var slots = new MenuSlots();
        var registrar = slots.registrar(slot -> slot.index = slots.entries().size());
        registrar.add(new Slot(new SimpleContainer(1), 0, 0, 0), null, MenuSlots.Role.AUXILIARY);
        var input = new Probe();
        var output = new Probe();
        var player = new Probe();
        registrar.add(input, null, MenuSlots.Role.MACHINE);
        registrar.add(output, null, MenuSlots.Role.MACHINE);
        registrar.add(player, null, MenuSlots.Role.PLAYER_STORAGE);
        var component = new ReservationMenuComponent(slots, client);
        var registry = new MenuComponentRegistry();
        registry.register(
            BuiltinMenuComponents.RESERVATIONS, 0, c -> null, ReservationMenuComponent.PLANS);
        var state = new ComponentStates(
            registry.schema(), Map.of(BuiltinMenuComponents.RESERVATIONS, component), registries);
        return new Fixture(component, state, input, output, player);
    }

    static void check(RegistryAccess registries) {
        var server = fixture(registries, false);
        var client = fixture(registries, true);
        var add = new ItemStack(Items.GOLD_INGOT, 2);
        add.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved output"));
        server.input.plan = new SlotPlan(3, ItemStack.EMPTY);
        server.output.plan = new SlotPlan(0, add);
        client.player.plan = new SlotPlan(9, ItemStack.EMPTY);
        var before = server.state.sample(List.of(server.component));
        require(
            server.input.applied == 0 && server.output.applied == 0,
            "Server sampling called a client adapter");
        var plans = server.state.get(server.component, ReservationMenuComponent.PLANS);
        require(
            plans.keySet().equals(Set.of(1, 2)),
            "Reservation indexes ignored earlier native contributions or included player slots");
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ScreenNetwork.Values.CODEC.encode(wire, new ScreenNetwork.Values(7, before));
            client.state.apply(ScreenNetwork.Values.CODEC.decode(wire).delta());
            require(!wire.isReadable(), "Trailing reservation packet bytes");
        } finally {
            wire.release();
        }
        require(
            client.input.plan.sameAs(server.input.plan)
                && client.output.plan.sameAs(server.output.plan),
            "Typed reservation packet lost input/output or item components");
        require(
            client.player.applied == 0 && client.player.plan.take() == 9,
            "Reservation field mutated a player slot");
        plans.get(2).add().setCount(50);
        require(
            server.state.get(server.component, ReservationMenuComponent.PLANS).get(2).add()
                .getCount() == 2,
            "Decoded reservation aliases stored state");
        server.output.plan = new SlotPlan(0, add.copy());
        require(
            ScreenValues.changed(before, server.state.sample(List.of(server.component)), false)
                .isEmpty(),
            "Equal recreated plans produced a delta");
        var reverse = new LinkedHashMap<Integer, SlotPlan>();
        reverse.put(2, server.output.plan);
        reverse.put(1, server.input.plan);
        var field = ReservationMenuComponent.PLANS;
        require(
            field.encode(registries, reverse).equals(field.encode(registries, plans)),
            "Map order changed reservation encoding");
        int address = before.keySet().iterator().next();
        var saved = client.state.snapshot();
        for (var malformed : List.of(
            encoded(registries, b -> b.writeVarInt(-1)),
            encoded(registries, b -> b.writeVarInt(32768)),
            encoded(
                registries,
                b -> {
                    b.writeVarInt(1);
                    b.writeVarInt(-1);
                }),
            encoded(
                registries,
                b -> {
                    b.writeVarInt(1);
                    b.writeVarInt(32767);
                }),
            encoded(
                registries,
                b -> {
                    b.writeVarInt(1);
                    b.writeVarInt(1);
                    b.writeVarInt(-1);
                }),
            encoded(
                registries,
                b -> {
                    b.writeVarInt(2);
                    b.writeVarInt(1);
                    b.writeVarInt(0);
                    b.writeVarInt(0);
                    b.writeVarInt(1);
                }),
            encoded(
                registries,
                b -> {
                    b.writeVarInt(0);
                    b.writeByte(1);
                }))) {
            rejected(() -> client.state.apply(Map.of(address, malformed)));
            require(
                client.state.snapshot().equals(saved) && client.input.plan.take() == 3,
                "Malformed reservation update changed state");
        }
        rejected(() -> field.encode(registries, Map.of(-1, SlotPlan.NONE)));
        rejected(() -> field.encode(registries, Map.of(32767, SlotPlan.NONE)));
        var oversized = new HashMap<Integer, SlotPlan>();
        for (int i = 0; i < 32767; i++)
            oversized.put(i, SlotPlan.NONE);
        rejected(() -> field.encode(registries, oversized));
        require(
            fixture(registries, true).input.plan.isNone(),
            "Another menu inherited reservation state");
        server.input.plan = SlotPlan.NONE;
        server.output.plan = SlotPlan.NONE;
        var after = server.state.sample(List.of(server.component));
        var delta = ScreenValues.changed(before, after, false);
        require(delta.size() == 1, "Plan clearing did not emit the registered field");
        client.state.apply(delta);
        require(
            client.input.plan.isNone() && client.output.plan.isNone(),
            "Cleared plans stayed on client adapters");
        // A complete snapshot also clears omitted plans, and never treats payload indexes as wire
        // keys.
        client.state.apply(
            Map.of(address, field.encode(registries, Map.of(1, new SlotPlan(1, ItemStack.EMPTY)))));
        client.state.apply(
            Map.of(
                address,
                field.encode(
                    registries, Map.of(3, new SlotPlan(20, ItemStack.EMPTY), 20, SlotPlan.NONE))));
        require(
            client.input.plan.isNone() && client.player.applied == 0,
            "Omitted plans or non-machine indexes were applied incorrectly");
    }

    private static ElementStateValue encoded(
        RegistryAccess registries, Consumer<RegistryFriendlyByteBuf> write) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            write.accept(buffer);
            var data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new ElementStateValue(data);
        } finally {
            buffer.release();
        }
    }

    private static void rejected(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Malformed reservation state was accepted");
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
