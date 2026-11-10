package zone.moddev.mc.lib.smoke;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.component.ComponentSnapshot;
import zone.moddev.mc.lib.gui.runtime.component.ComponentStateView;
import zone.moddev.mc.lib.gui.runtime.component.ComponentSync;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentKey;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentRegistry;
import zone.moddev.mc.lib.gui.runtime.component.MenuSlotContributor;
import zone.moddev.mc.lib.gui.runtime.component.MenuSnapshot;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/** A mod contributes a native slot without adding a field or branch to MachineMenu. */
final class MenuComponentSmoke {
    private static final MenuComponentKey<Probe> KEY = new MenuComponentKey<>(
        ResourceLocation.parse("test:menu_component"), Probe.class);
    private static final ComponentSync<Integer> VALUE = new ComponentSync<>(
        ResourceLocation.parse("test:value"),
        1,
        net.minecraft.network.codec.StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt()),
        5,
        () -> 0);
    private static boolean installed;

    private static final class TestMenu extends MachineMenu {
        TestMenu(Player player, BlockEntity entity) {
            super(94, player.getInventory(), entity);
        }
    }

    private static final class Probe implements MenuSlotContributor, MenuSnapshot {
        final Slot slot = new Slot(new SimpleContainer(1), 0, 5, 5);
        int ticks, before, after, broadcasts, removals, positions;
        boolean applied;

        public void addSlots(MenuSlots.Registrar slots, ScreenLayout layout) {
            slots.add(slot, null, MenuSlots.Role.AUXILIARY);
        }

        public List<Slot> destinations(ItemStack stack) {
            return List.of(slot);
        }

        public int transferPriority() {
            return -200;
        }

        public void position(ScreenLayout layout) {
            positions++;
        }

        public void tickClient() {
            ticks++;
        }

        public void beforeInteraction() {
            before++;
        }

        public void afterInteraction() {
            after++;
        }

        public void broadcast() {
            broadcasts++;
        }

        public void removed() {
            removals++;
        }

        public void snapshot(ComponentSnapshot values) {
            values.put(VALUE, 42);
        }

        public void applied(ComponentStateView values) {
            applied = values.get(VALUE) == 42;
        }
    }

    static void check(ServerLevel level, BlockPos pos, Player player) {
        if (!installed) {
            MenuComponentRegistry.GLOBAL.register(
                KEY, -2000, c -> c.menu() instanceof TestMenu ? new Probe() : null, VALUE);
            installed = true;
        }
        level.setBlock(pos, BuiltInRegistries.BLOCK.get(CrusherSmoke.ID).defaultBlockState(), 3);
        var entity = level.getBlockEntity(pos);
        var menu = new TestMenu(player, entity);
        var probe = menu.components().require(KEY);
        require(probe.slot.index == 0, "Mod component contributes before built-in inventory");
        require(menu.menuSlot(0) == 1, "Legacy storage lookup follows registered slot ordering");
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.STONE, 12));
        var source = menu.slots.stream()
            .filter(s -> s.container == player.getInventory() && s.getContainerSlot() == 9)
            .findFirst()
            .orElseThrow();
        menu.quickMoveStack(player, source.index);
        require(
            probe.slot.getItem().getCount() == 12 && source.getItem().isEmpty(),
            "Registered insertion destination receives shift-click");
        menu.quickMoveStack(player, probe.slot.index);
        require(
            probe.slot.getItem().isEmpty() && player.getInventory().countItem(Items.STONE) == 12,
            "Contributed slot extracts into player storage");
        menu.clicked(probe.slot.index, 0, ClickType.PICKUP, player);
        require(
            probe.before == 3 && probe.after == 3,
            "Every interaction dispatches one lifecycle pair");
        menu.tickClientActions();
        menu.broadcastChanges();
        menu.components().position(menu.layout());
        menu.apply(menu.components().snapshot());
        require(
            probe.ticks == 1 && probe.broadcasts == 1 && probe.positions == 1 && probe.applied,
            "Component lifecycle and values are generic");
        require(
            new TestMenu(player, entity).components().require(KEY) != probe,
            "Each menu receives fresh state");
        require(
            new MachineMenu(95, player.getInventory(), entity).components().get(KEY) == null,
            "Factories can omit unavailable components");
        record EqualComponent(int value)
            implements
                zone.moddev.mc.lib.gui.runtime.component.MenuComponent {
        }
        var identityRegistry = new MenuComponentRegistry();
        var identityKey = new MenuComponentKey<>(ResourceLocation.parse("test:identity"),
            EqualComponent.class);
        var context = new MenuComponentRegistry.Context(menu, player.getInventory(), null, false);
        var singleton = new EqualComponent(1);
        identityRegistry.register(identityKey, 0, c -> singleton);
        require(identityRegistry.create(context).require(identityKey) == singleton,
            "First factory instance is accepted");
        try {
            identityRegistry.create(context);
            throw new AssertionError("Cross-menu component reuse accepted");
        } catch (IllegalArgumentException expected) {
        }
        identityRegistry.replace(identityKey, 0, c -> new EqualComponent(1));
        var equalFirst = identityRegistry.create(context).require(identityKey);
        var equalSecond = identityRegistry.create(context).require(identityKey);
        require(equalFirst != equalSecond && equalFirst.equals(equalSecond),
            "Equal but distinct component instances are accepted by identity");
        var duplicateRegistry = new MenuComponentRegistry();
        duplicateRegistry.register(identityKey, 0, c -> singleton);
        duplicateRegistry.register(new MenuComponentKey<>(ResourceLocation.parse("test:other"),
            EqualComponent.class), 1, c -> singleton);
        try {
            duplicateRegistry.create(context);
            throw new AssertionError("Same-menu component reuse accepted");
        } catch (IllegalArgumentException expected) {
        }
        menu.removed(player);
        require(probe.removals == 1, "Removal lifecycle");
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
