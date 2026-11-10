package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.load.ScreenValidationContext;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ElementGroup;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/** An ordinary item slot backed by the viewing player's inventory, never machine storage. */
public class PlayerSlotElement extends ItemSlotElement {
    @Override
    public boolean playerInventory() {
        return true;
    }

    public PlayerSlotElement(Common common, int index, Appearance appearance) {
        super(common, index, SlotKind.NORMAL, appearance);
        if (index < 0 || index > 40)
            throw new IllegalArgumentException("Player slot outside 0..40");
    }

    public static PlayerSlotElement player(int index) {
        ResourceLocation icon = switch (index) {
            case 39 -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case 38 -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case 37 -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            case 36 -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            case 40 -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            default -> null;
        };
        return new PlayerSlotElement(
            Common.empty(),
            index,
            new Appearance(
                SlotSize.NORMAL,
                java.util.Optional.empty(),
                java.util.Optional.ofNullable(icon),
                ResourceLocation.parse("mmdlib:slot"),
                .42f,
                icon == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(InventoryMenu.BLOCK_ATLAS)));
    }

    public Slot createPlayerSlot(Inventory inventory, Rect bounds) {
        return createPlayerSlot(inventory, bounds, () -> true);
    }

    public Slot createPlayerSlot(Inventory inventory, Rect bounds, BooleanSupplier visible) {
        var xy = itemPosition(bounds);
        int index = index();
        return index < 36
            ? new Slot(inventory, index, xy[0], xy[1]) {
                public boolean isActive() {
                    return visible.getAsBoolean();
                }

                public boolean mayPlace(ItemStack stack) {
                    return isActive() && super.mayPlace(stack);
                }

                public boolean mayPickup(Player player) {
                    return isActive() && super.mayPickup(player);
                }
            }
            : new PlayerMenuSlot(inventory, index, xy[0], xy[1], visible);
    }

    public static class PlayerMenuSlot extends Slot {
        private final Player owner;
        private final EquipmentSlot equipment;
        private final BooleanSupplier visible;

        protected PlayerMenuSlot(
            Inventory inventory, int index, int x, int y, BooleanSupplier visible) {
            super(inventory, index, x, y);
            this.visible = visible;
            owner = inventory.player;
            equipment = switch (index) {
                case 39 -> EquipmentSlot.HEAD;
                case 38 -> EquipmentSlot.CHEST;
                case 37 -> EquipmentSlot.LEGS;
                case 36 -> EquipmentSlot.FEET;
                case 40 -> EquipmentSlot.OFFHAND;
                default -> throw new IllegalArgumentException("Equipment slot: " + index);
            };
        }

        public boolean isActive() {
            return visible.getAsBoolean();
        }

        @Override
        public void setByPlayer(ItemStack stack, ItemStack previous) {
            owner.onEquipItem(equipment, previous, stack);
            super.setByPlayer(stack, previous);
        }

        @Override
        public int getMaxStackSize() {
            return equipment == EquipmentSlot.OFFHAND ? super.getMaxStackSize() : 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive()
                && (equipment == EquipmentSlot.OFFHAND || stack.canEquip(equipment, owner));
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive()
                && (equipment == EquipmentSlot.OFFHAND
                    || player.isCreative()
                    || !EnchantmentHelper.has(
                        getItem(), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
                && super.mayPickup(player);
        }
    }

    @Override
    public int playerSlotOrder() {
        return index() < 36 ? (index() + 27) % 36 : index() == 40 ? 40 : 75 - index();
    }

    @Override
    public MenuSlots.Role role() {
        return index() < 36 ? MenuSlots.Role.PLAYER_STORAGE : MenuSlots.Role.EQUIPMENT;
    }

    @Override
    public void validateInventory(Screen screen, ScreenHost host) {
    }

    @Override
    public void validateScreen(ScreenValidationContext context) {
        context.unique(
            ResourceLocation.parse("mmdlib:player_slot"),
            index(),
            "Player slot " + index() + " is placed twice");
    }

    @Override
    public List<String> associatedGroups(Screen screen, List<ElementGroup> groups) {
        return groups.stream()
            .filter(group -> common().id().filter(group.anchors()::contains).isPresent())
            .map(ElementGroup::id)
            .toList();
    }
}
