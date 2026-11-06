package zone.moddev.mc.companion.test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioCanUnequipEvent;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerNetwork;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

/** Only loaded in the real optional-mod client run. Slot definitions/tags are test resources. */
final class CuriosFixture {
  private static ContainerDrawerNetwork.Request stale;

  static void setup(ServerPlayer player) {
    var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
    var rings = inventory.getStacksHandler("ring").orElseThrow();
    require(
        rings.hasCosmetic() && rings.getStacks().getSlots() == 3,
        "Cosmetic fixture slots registered");
    require(
        rings.getStacks().insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty(),
        "Functional fixture accepts tagged item");
    var locked = new ItemStack(Items.DIAMOND);
    locked.set(
        net.minecraft.core.component.DataComponents.CUSTOM_NAME,
        net.minecraft.network.chat.Component.literal("Locked ring"));
    rings.getStacks().insertItem(1, locked, false);
    rings.getCosmeticStacks().insertItem(0, new ItemStack(Items.EMERALD), false);
    inventory
        .getStacksHandler("necklace")
        .orElseThrow()
        .getStacks()
        .insertItem(0, new ItemStack(Items.GOLD_INGOT), false);
    inventory
        .getStacksHandler("charm")
        .orElseThrow()
        .getStacks()
        .insertItem(11, new ItemStack(Items.AMETHYST_SHARD), false);
    NeoForge.EVENT_BUS.addListener(
        (CurioCanUnequipEvent event) -> {
          if (event.getSlotContext().entity() == player
              && event.getSlotContext().identifier().equals("ring")
              && event.getSlotContext().index() == 1) event.setUnequipResult(TriState.FALSE);
        });
  }

  static void resize(ServerPlayer player) {
    var menu = (MachineMenu) player.containerMenu;
    var drawer = menu.curiosDrawer;
    stale =
        new ContainerDrawerNetwork.Request(
            menu.containerId,
            true,
            menu.widgetSession(),
            drawer.state().revision(),
            drawer.state().acknowledged() + 1,
            ContainerDrawerMenu.PICKUP,
            0,
            drawer.state().pageSize(),
            ItemStack.EMPTY);
    CuriosApi.getCuriosInventory(player)
        .orElseThrow()
        .addTransientSlotModifier(
            "ring",
            ResourceLocation.parse("mmdlib_companion:drawer_test"),
            1,
            AttributeModifier.Operation.ADD_VALUE);
  }

  static void checkStale(ServerPlayer player) {
    var menu = (MachineMenu) player.containerMenu;
    menu.curiosDrawer.action(player, stale);
    require(menu.getCarried().isEmpty(), "Pre-resize request cannot mutate the new topology");
    require(
        CuriosApi.getCuriosInventory(player)
            .orElseThrow()
            .getStacksHandler("ring")
            .orElseThrow()
            .getStacks()
            .getStackInSlot(0)
            .is(Items.DIAMOND),
        "Stale click left equipment intact");
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
