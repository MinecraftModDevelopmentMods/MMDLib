package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

/** Real player inventory ownership, armor restrictions and equipment transfer regression checks. */
final class PlayerEquipmentSmoke {
  static void check(ServerLevel level, BlockPos pos, Player player, Player creative) {
    level.setBlock(pos, BuiltInRegistries.BLOCK.get(CrusherSmoke.ID).defaultBlockState(), 3);
    var entity = (CrusherSmoke.Crusher) level.getBlockEntity(pos);
    var menu = new MachineMenu(92, player.getInventory(), entity);
    player.getInventory().clearContent();
    var inventory = player.getInventory();
    var bounds = ScreenLayout.of(menu.bound.screen(), TextMeasure.NONE).inventory().orElseThrow();
    require(
        menu.slots.stream().filter(s -> s.container == inventory).count() == 41, "41 player slots");
    for (int index = 0; index <= 40; index++) {
      var slot = slot(menu, player, index);
      var expected = ScreenLayout.itemPosition(ScreenLayout.playerSlot(bounds, index));
      require(slot.x == expected[0] && slot.y == expected[1], "Slot position " + index);
    }
    var helmet = slot(menu, player, 39);
    var offhand = slot(menu, player, 40);
    var armor =
        new net.minecraft.world.item.Item[] {
          Items.DIAMOND_BOOTS,
          Items.DIAMOND_LEGGINGS,
          Items.DIAMOND_CHESTPLATE,
          Items.DIAMOND_HELMET
        };
    for (int index = 36; index <= 39; index++) {
      var slot = slot(menu, player, index);
      require(slot.getMaxStackSize(new ItemStack(armor[index - 36])) == 1, "Armor limit");
      for (int other = 36; other <= 39; other++)
        require(slot.mayPlace(new ItemStack(armor[other - 36])) == (index == other), "Armor type");
      require(!slot.mayPlace(new ItemStack(Items.STONE)), "Reject non-armor");
      require(slot.getNoItemIcon() != null, "Empty armor icon");
    }
    require(
        offhand.mayPlace(new ItemStack(Items.STONE))
            && offhand.getMaxStackSize(new ItemStack(Items.STONE)) == 64,
        "Offhand accepts ordinary stacks");
    require(offhand.getNoItemIcon() != null, "Empty offhand icon");
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
  }

  private static Slot slot(MachineMenu menu, Player player, int index) {
    return menu.slots.stream()
        .filter(s -> s.container == player.getInventory() && s.getContainerSlot() == index)
        .findFirst()
        .orElseThrow();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
