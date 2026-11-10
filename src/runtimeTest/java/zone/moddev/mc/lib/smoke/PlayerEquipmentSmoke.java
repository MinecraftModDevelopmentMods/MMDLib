package zone.moddev.mc.lib.smoke;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.model.elements.PlayerSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/** Real player inventory ownership, armor restrictions and equipment transfer regression checks. */
final class PlayerEquipmentSmoke {
    static void check(ServerLevel level, BlockPos pos, Player player, Player creative) {
        level.setBlock(pos, BuiltInRegistries.BLOCK.get(CrusherSmoke.ID).defaultBlockState(), 3);
        var entity = (CrusherSmoke.Crusher) level.getBlockEntity(pos);
        var menu = new MachineMenu(92, player.getInventory(), entity);
        player.getInventory().clearContent();
        var inventory = player.getInventory();
        var bounds = ScreenLayout.of(menu.bound.screen(), TextMeasure.NONE)
            .region(PlayerInventoryElement.INVENTORY)
            .orElseThrow();
        require(
            menu.slots.stream().filter(s -> s.container == inventory).count() == 41,
            "41 player slots");
        var expectedOrder = new ArrayList<Integer>();
        for (int i = 9; i < 36; i++)
            expectedOrder.add(i);
        for (int i = 0; i < 9; i++)
            expectedOrder.add(i);
        expectedOrder.addAll(List.of(39, 38, 37, 36, 40));
        require(
            menu.slots.stream()
                .filter(slot -> slot.container == inventory)
                .map(Slot::getContainerSlot)
                .toList()
                .equals(expectedOrder),
            "Default vanilla player ordering is preserved");
        for (int index = 0; index <= 40; index++) {
            var slot = slot(menu, player, index);
            var expected = zone.moddev.mc.lib.gui.model.elements.PlayerSlotElement.player(index)
                .itemPosition(PlayerInventoryElement.slot(bounds, index));
            require(slot.x == expected[0] && slot.y == expected[1], "Slot position " + index);
        }
        var helmet = slot(menu, player, 39);
        var offhand = slot(menu, player, 40);
        var armor = new net.minecraft.world.item.Item[]{
            Items.DIAMOND_BOOTS,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_HELMET
        };
        for (int index = 36; index <= 39; index++) {
            var slot = slot(menu, player, index);
            require(slot.getMaxStackSize(new ItemStack(armor[index - 36])) == 1, "Armor limit");
            for (int other = 36; other <= 39; other++)
                require(slot.mayPlace(new ItemStack(armor[other - 36])) == (index == other),
                    "Armor type");
            require(!slot.mayPlace(new ItemStack(Items.STONE)), "Reject non-armor");
            require(
                menu.slotElement(slot.index).icon().isPresent(),
                "Slot element owns empty armor icon");
        }
        require(
            offhand.mayPlace(new ItemStack(Items.STONE))
                && offhand.getMaxStackSize(new ItemStack(Items.STONE)) == 64,
            "Offhand accepts ordinary stacks");
        require(
            menu.slotElement(offhand.index).icon().isPresent(),
            "Slot element owns empty offhand icon");
        menu.setCarried(new ItemStack(Items.DIAMOND_HELMET));
        menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
        require(
            inventory.getItem(39).is(Items.DIAMOND_HELMET) && menu.getCarried().isEmpty(),
            "Equip helmet");
        require(
            !menu.quickMoveStack(player, helmet.index).isEmpty() && inventory.getItem(39).isEmpty(),
            "Shift-click unequips armor");
        require(entity.items.getStackInSlot(0).isEmpty(), "Unequipping does not feed the machine");
        var cursed = new ItemStack(Items.DIAMOND_HELMET);
        cursed.enchant(
            level
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.BINDING_CURSE),
            1);
        helmet.setByPlayer(cursed);
        require(!helmet.mayPickup(player), "Binding curse prevents pickup");
        menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
        require(menu.getCarried().isEmpty() && helmet.hasItem(), "Cursed click refused");
        require(menu.quickMoveStack(player, helmet.index).isEmpty(), "Cursed shift-click refused");
        require(helmet.mayPickup(creative), "Creative curse exemption");
        helmet.set(ItemStack.EMPTY);
        inventory.clearContent();
        menu.setCarried(new ItemStack(Items.COBBLESTONE, 12));
        menu.clicked(offhand.index, 0, ClickType.PICKUP, player);
        require(inventory.getItem(40).getCount() == 12, "Place offhand stack");
        menu.quickMoveStack(player, offhand.index);
        require(
            inventory.getItem(40).isEmpty() && inventory.countItem(Items.COBBLESTONE) == 12,
            "Offhand shift-click conserves items");
        inventory.clearContent();
        for (int index = 0; index < 36; index++)
            inventory.setItem(index, new ItemStack(Items.STONE, 64));
        entity.items.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 5));
        require(menu.quickMoveStack(player, 1).isEmpty(), "Full inventory refuses output");
        require(
            offhand.getItem().isEmpty() && entity.items.getStackInSlot(1).getCount() == 5,
            "Output does not spill into offhand");
        inventory.setItem(0, ItemStack.EMPTY);
        require(
            !menu.quickMoveStack(player, 1).isEmpty() && inventory.getItem(0).getCount() == 5,
            "Output still moves to hotbar");
        inventory.clearContent();
        AutomationSlotSmoke.check(menu, player);
        customSlots(entity, player);
        InventoryBindingSmoke.check(player);
        SlotPolicySmoke.check(player);
    }

    /** A direct interface implementation, with no dependency on PlayerSlotElement. */
    private record ModSlot(int index, int playerSlotOrder) implements SlotElement {
        public Common common() {
            return Common.empty();
        }

        public SlotKind kind() {
            return SlotKind.NORMAL;
        }

        public Appearance appearance() {
            return Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty());
        }

        public boolean playerInventory() {
            return true;
        }

        public MenuSlots.Role role() {
            return MenuSlots.Role.EQUIPMENT;
        }

        public Slot createPlayerSlot(Inventory inventory, Rect bounds, BooleanSupplier visible) {
            var xy = itemPosition(bounds);
            return new Slot(inventory, index, xy[0], xy[1]) {
                public boolean isActive() {
                    return visible.getAsBoolean();
                }

                public boolean mayPlace(ItemStack stack) {
                    return isActive() && stack.is(Items.DIAMOND);
                }
            };
        }
    }

    private static void customSlots(CrusherSmoke.Crusher entity, Player player) {
        var original = Screens.all();
        var bound = Screens.get(CrusherSmoke.ID).orElseThrow();
        var first = new ModSlot(0, 10);
        var second = new ModSlot(1, -10);
        var tied = new ModSlot(2, 10);
        var derived = new PlayerSlotElement(Common.empty(), 3, first.appearance()) {
            public int playerSlotOrder() {
                return -20;
            }

            public MenuSlots.Role role() {
                return MenuSlots.Role.AUXILIARY;
            }

            public Slot createPlayerSlot(Inventory inventory, Rect rect, BooleanSupplier visible) {
                var slot = super.createPlayerSlot(inventory, rect, visible);
                slot.set(new ItemStack(Items.EMERALD));
                return slot;
            }
        };
        var machineSlot = new ItemSlotElement(Common.empty(), 0, SlotKind.NORMAL,
            first.appearance()) {
            public MenuSlots.Role role() {
                return MenuSlots.Role.PLAYER_STORAGE;
            }
        };
        var definition = new Screen(
            CrusherSmoke.ID,
            "custom-player-slots",
            bound.screen().title(),
            bound.screen().blockEntity(),
            false,
            List.of(machineSlot, first, second, tied, derived),
            List.of());
        var definitions = new HashMap<>(original);
        definitions.put(
            CrusherSmoke.ID,
            ScreenBinder.bind(definition, PlayerEquipmentSmoke.class.getClassLoader()));
        try {
            Screens.install(definitions);
            var menu = new MachineMenu(93, player.getInventory(), entity);
            require(
                menu.nativeSlots().entry(menu.slot(machineSlot).index)
                    .role() == MenuSlots.Role.PLAYER_STORAGE,
                "Machine-backed slots retain their declared role");
            require(
                menu.layout().region(PlayerInventoryElement.INVENTORY).isEmpty(),
                "No standard player region");
            var entries = menu.nativeSlots().entries().stream()
                .filter(e -> e.element() != null && e.element().playerInventory())
                .toList();
            require(
                entries.stream()
                    .map(MenuSlots.Entry::element)
                    .toList()
                    .equals(List.of(derived, second, first, tied)),
                "Custom order and stable equal priorities");
            require(
                menu.playerSlotElements().equals(List.of(derived, second, first, tied)),
                "Player discovery uses backing inventory, independent of transfer roles");
            require(
                entries.getFirst().role() == MenuSlots.Role.AUXILIARY
                    && entries.getFirst().slot().getItem().is(Items.EMERALD),
                "Subclass factory and role overrides");
            for (var entry : entries.subList(1, entries.size())) {
                require(entry.role() == MenuSlots.Role.EQUIPMENT,
                    "Direct implementation role override");
                require(
                    entry.slot().mayPlace(new ItemStack(Items.DIAMOND))
                        && !entry.slot().mayPlace(new ItemStack(Items.STONE)),
                    "Direct implementation factory override");
                var xy = entry.element().itemPosition(menu.layout().bounds(entry.element()));
                require(entry.slot().x == xy[0] && entry.slot().y == xy[1], "Custom slot position");
            }
        } finally {
            Screens.install(original);
            player.getInventory().clearContent();
        }
    }

    private static Slot slot(MachineMenu menu, Player player, int index) {
        return menu.slots.stream()
            .filter(s -> s.container == player.getInventory() && s.getContainerSlot() == index)
            .findFirst()
            .orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
