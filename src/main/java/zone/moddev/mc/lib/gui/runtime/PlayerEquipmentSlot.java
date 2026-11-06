package zone.moddev.mc.lib.gui.runtime;

import com.mojang.datafixers.util.Pair;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Player-owned armor and offhand slots, with the same rules and callbacks as InventoryMenu. */
final class PlayerEquipmentSlot extends Slot {
  private final Player owner;
  private final EquipmentSlot equipment;
  private final ResourceLocation icon;

  PlayerEquipmentSlot(Inventory inventory, int index, int x, int y) {
    super(inventory, index, x, y);
    owner = inventory.player;
    equipment =
        switch (index) {
          case 39 -> EquipmentSlot.HEAD;
          case 38 -> EquipmentSlot.CHEST;
          case 37 -> EquipmentSlot.LEGS;
          case 36 -> EquipmentSlot.FEET;
          case 40 -> EquipmentSlot.OFFHAND;
          default -> throw new IllegalArgumentException("Equipment slot: " + index);
        };
    icon =
        switch (equipment) {
          case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
          case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
          case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
          case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
          default -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
        };
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
    return equipment == EquipmentSlot.OFFHAND || stack.canEquip(equipment, owner);
  }

  @Override
  public boolean mayPickup(Player player) {
    return (equipment == EquipmentSlot.OFFHAND
            || player.isCreative()
            || !EnchantmentHelper.has(getItem(), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
        && super.mayPickup(player);
  }

  @Override
  public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
    return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
  }
}
