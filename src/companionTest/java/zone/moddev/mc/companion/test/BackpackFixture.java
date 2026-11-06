package zone.moddev.mc.companion.test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import top.theillusivec4.curios.api.CuriosApi;

/** Test-only fixture: exercises the real optional runtime mods without linking a backpack API. */
final class BackpackFixture {
  static boolean installed() {
    return ModList.get().isLoaded("sophisticatedbackpacks");
  }

  static ItemStack equip(ServerPlayer player) {
    var bag =
        new ItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
    require(!bag.isEmpty(), "Runtime backpack fixture registered");
    bag.set(DataComponents.CUSTOM_NAME, Component.literal("Copper supplies"));
    var previous = player.getMainHandItem();
    player.setItemInHand(InteractionHand.MAIN_HAND, bag);
    // Normal use creates the backpack's own storage identity and initializes its capability.
    bag.use(player.level(), player, InteractionHand.MAIN_HAND);
    player.closeContainer();
    player.setItemInHand(InteractionHand.MAIN_HAND, previous);
    var slots =
        CuriosApi.getCuriosInventory(player)
            .orElseThrow()
            .getStacksHandler("back")
            .orElseThrow()
            .getStacks();
    slots.setStackInSlot(0, bag);
    var handler = bag.getCapability(Capabilities.ItemHandler.ITEM);
    require(
        handler != null && handler.getSlots() >= 27,
        "Initialized backpack exposes generic item storage");
    require(
        handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 24), false).isEmpty(),
        "Fixture accepts iron");
    require(
        handler.insertItem(1, new ItemStack(Items.COPPER_INGOT, 12), false).isEmpty(),
        "Fixture accepts copper");
    require(
        handler.insertItem(2, new ItemStack(Items.REDSTONE, 32), false).isEmpty(),
        "Fixture accepts redstone");
    return bag;
  }

  static void unequip(ServerPlayer player) {
    CuriosApi.getCuriosInventory(player)
        .orElseThrow()
        .getStacksHandler("back")
        .orElseThrow()
        .getStacks()
        .setStackInSlot(0, ItemStack.EMPTY);
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
