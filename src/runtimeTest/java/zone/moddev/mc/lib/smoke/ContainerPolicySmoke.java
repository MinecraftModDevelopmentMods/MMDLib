package zone.moddev.mc.lib.smoke;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.items.ItemStackHandler;

import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerNetwork;
import zone.moddev.mc.lib.gui.runtime.ContainerPolicy;
import zone.moddev.mc.lib.gui.runtime.ContainerTargets;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

/** Provider settings stay authoritative even when a client forges, replays or races gestures. */
final class ContainerPolicySmoke {
    static void check(MachineMenu menu, Player player) {
        player.getInventory().clearContent();
        var settings = new ContainerPolicy.Settings[]{
            new ContainerPolicy.Settings(
                new ContainerPolicy.Layout(100, 3, 4, 8, 26, 24, 24, 20, 24),
                6,
                3,
                EnumSet.of(ContainerPolicy.Gesture.PICKUP, ContainerPolicy.Gesture.DISTRIBUTE),
                true)
        };
        var customInstruction = Component.literal("custom gesture instructions");
        var defaults = ContainerPolicy.STORAGE.settings();
        var gestures = List.of(
            ContainerPolicy.Gesture.DISTRIBUTE,
            ContainerPolicy.Gesture.COLLECT,
            ContainerPolicy.Gesture.SWAP);
        var keys = List.of(
            "screen.mmdlib.container.distribute",
            "screen.mmdlib.container.collect",
            "screen.mmdlib.container.swap");
        for (int mask = 0; mask < 8; mask++) {
            var enabled = EnumSet.noneOf(ContainerPolicy.Gesture.class);
            var expected = new ArrayList<String>();
            for (int i = 0; i < 3; i++)
                if ((mask & (1 << i)) != 0) {
                    enabled.add(gestures.get(i));
                    expected.add(keys.get(i));
                }
            var sample = new ContainerPolicy.Settings(
                defaults.layout(),
                defaults.initialPageSize(),
                defaults.pageAlignment(),
                enabled,
                defaults.protectSource());
            var actual = ContainerPolicy.defaultInstructions(sample).stream()
                .map(
                    c -> ((net.minecraft.network.chat.contents.TranslatableContents) c
                        .getContents())
                        .getKey())
                .toList();
            require(actual.equals(expected),
                "Each enabled gesture independently supplies instructions");
        }
        var policy = new ContainerPolicy() {
            public Settings settings() {
                return settings[0];
            }

            public List<Component> instructions(Settings sampled) {
                require(sampled == settings[0], "Instructions receive the sampled policy");
                return List.of(customInstruction);
            }
        };
        class DescribedStorage extends ItemStackHandler
            implements
                EquippedContainers.DescribedHandler {
            DescribedStorage() {
                super(12);
            }

            public EquippedContainers.SlotInfo describe(int slot) {
                return new EquippedContainers.SlotInfo(
                    "custom",
                    slot,
                    false,
                    true,
                    net.minecraft.resources.ResourceLocation.parse("test:cell"));
            }
        }
        var storage = new DescribedStorage();
        var source = new EquippedContainers.Source(
            "test:policy", new ItemStack(Items.CHEST), storage, null, "policy", policy);
        var drawer = new ContainerDrawerMenu(menu, player, () -> List.of(source));
        drawer.refresh();
        var detached = source.presentation();
        customInstruction.append(" changed");
        require(
            detached.gestureRows().getFirst().getString().equals("custom gesture instructions"),
            "Instruction inputs are copied");
        ((net.minecraft.network.chat.MutableComponent) detached.gestureRows().getFirst())
            .append(" output");
        require(
            detached.gestureRows().getFirst().getString().equals("custom gesture instructions"),
            "Instruction accessors are copied");
        try {
            detached.withGestureRows(Collections.nCopies(17, Component.empty()));
            throw new AssertionError("Oversized instructions accepted");
        } catch (IllegalArgumentException expected) {
        }
        send(drawer, menu, player, ContainerDrawerMenu.TOGGLE, 0, 6, List.of());
        require(drawer.state().open() && drawer.state().pageSize() == 6,
            "Source owns initial page");
        send(drawer, menu, player, ContainerDrawerMenu.PAGE, 0, 54, List.of());
        require(drawer.state().pageSize() == 6,
            "Server rejects a page larger than the provider's visible capacity");
        storage.setStackInSlot(10, new ItemStack(Items.DIAMOND));
        send(drawer, menu, player, ContainerDrawerMenu.PICKUP, 10, 54, List.of());
        require(menu.getCarried().isEmpty() && storage.getStackInSlot(10).is(Items.DIAMOND),
            "Rejected oversized pages cannot expose hidden slots to pickup");
        storage.setStackInSlot(10, ItemStack.EMPTY);
        send(drawer, menu, player, ContainerDrawerMenu.PAGE, 1, 6, List.of());
        require(drawer.state().offset() == 0, "Server rejects misaligned offset");
        send(drawer, menu, player, ContainerDrawerMenu.PAGE, 3, 5, List.of());
        require(drawer.state().offset() == 0, "Server rejects misaligned size");
        send(drawer, menu, player, ContainerDrawerMenu.PAGE, 3, 6, List.of());
        require(drawer.state().offset() == 3, "Custom alignment accepted");
        var nativeSlot = menu.nativeSlots().entries().stream()
            .map(e -> e.slot())
            .filter(s -> s.container == player.getInventory() && s.getContainerSlot() == 0)
            .findFirst()
            .orElseThrow();
        int target = drawer.targets().playerTarget(nativeSlot);
        require(
            target >= 0 && drawer.targets().playerIndex(target) == 0,
            "Registered viewer slot resolves");
        var impostor = new net.minecraft.world.inventory.Slot(player.getInventory(), 0, 0, 0);
        impostor.index = nativeSlot.index;
        require(
            drawer.targets().playerTarget(impostor) < 0,
            "Unregistered slot cannot masquerade as target");
        require(
            !drawer.targets().contains(0)
                && !drawer.targets().contains(ContainerTargets.TARGET_LIMIT),
            "Hidden and out-of-range targets refused");
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        drawer.targets().contents(target).setCount(40);
        require(player.getInventory().getItem(0).getCount() == 2, "Target contents are detached");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 9));
        send(drawer, menu, player, ContainerDrawerMenu.DISTRIBUTE, 0, 6, List.of(3, 4, target));
        require(
            menu.getCarried().isEmpty()
                && storage.getStackInSlot(3).getCount() == 3
                && player.getInventory().getItem(0).getCount() == 3,
            "Custom policy retains distribution and target routing");
        menu.setCarried(new ItemStack(Items.IRON_INGOT));
        send(drawer, menu, player, ContainerDrawerMenu.COLLECT, 3, 6, List.of());
        require(menu.getCarried().getCount() == 1, "Forged disabled gesture rejected");
        var stale = request(drawer, menu, ContainerDrawerMenu.DISTRIBUTE, 0, 6, List.of(3));
        settings[0] = new ContainerPolicy.Settings(
            new ContainerPolicy.Layout(100, 4, 4, 8, 26, 22, 22, 18, 24),
            4,
            2,
            EnumSet.of(ContainerPolicy.Gesture.PICKUP),
            true);
        drawer.action(player, stale);
        require(
            menu.getCarried().getCount() == 1
                && drawer.state().offset() == 2
                && drawer.state().pageSize() == 4,
            "Changed policy invalidates gesture and realigns page");
        send(drawer, menu, player, ContainerDrawerMenu.DISTRIBUTE, 0, 4, List.of(3));
        require(menu.getCarried().getCount() == 1, "Fresh forbidden request also rejected");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), player.registryAccess());
        try {
            ContainerDrawerNetwork.State.CODEC.encode(buffer, drawer.state());
            var decoded = ContainerDrawerNetwork.State.CODEC.decode(buffer);
            require(
                decoded.presentations().getFirst().policy().equals(settings[0]),
                "Custom settings round trip");
            require(
                decoded
                    .presentations()
                    .getFirst()
                    .gestureRows()
                    .getFirst()
                    .getString()
                    .equals("custom gesture instructions changed"),
                "Mod instructions round trip");
            require(
                decoded.descriptions().size() == 4 && !decoded.equipment(),
                "Provider descriptions are independent of drawer identity");
        } finally {
            buffer.release();
        }
        menu.setCarried(ItemStack.EMPTY);
        player.getInventory().clearContent();
    }

    private static ContainerDrawerNetwork.Request request(
        ContainerDrawerMenu drawer,
        MachineMenu menu,
        int kind,
        int argument,
        int size,
        List<Integer> targets) {
        return new ContainerDrawerNetwork.Request(
            menu.containerId,
            false,
            menu.sessionId(),
            drawer.state().revision(),
            drawer.state().acknowledged() + 1,
            kind,
            argument,
            size,
            menu.getCarried().copy(),
            targets);
    }

    private static void send(
        ContainerDrawerMenu drawer,
        MachineMenu menu,
        Player player,
        int kind,
        int argument,
        int size,
        List<Integer> targets) {
        drawer.action(player, request(drawer, menu, kind, argument, size, targets));
    }

    private static void require(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
    }
}
