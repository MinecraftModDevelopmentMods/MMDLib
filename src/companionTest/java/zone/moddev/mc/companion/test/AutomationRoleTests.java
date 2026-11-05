package zone.moddev.mc.companion.test;

import java.util.*;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

import zone.moddev.mc.companion.*;
import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.gui.runtime.*;

@GameTestHolder(CompanionMod.ID)
@PrefixGameTestTemplate(false)
public final class AutomationRoleTests {
  private static ProcessorEntity place(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(2, 2, 2));
    h.getLevel().setBlock(pos, CompanionMod.LOGISTICS.block.get().defaultBlockState(), 3);
    return (ProcessorEntity) h.getLevel().getBlockEntity(pos);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  @GameTest(template = "empty")
  public static void duplicateRolesRejectedByStorageAndMenu(GameTestHelper h) {
    var machine = place(h);
    var a = machine.automation;
    var puller = new ItemStack(CompanionAddons.PULLER.get());
    require(a.slots().insertItem(0, puller.copy(), false).isEmpty(), "First puller installs");
    a.slots().setStackInSlot(4, new ItemStack(CompanionAddons.ITEM_FILTER.get()));
    for (boolean simulate : new boolean[] {true, false})
      require(
          a.slots().insertItem(1, puller.copy(), simulate).getCount() == 1,
          "Duplicate puller is refused, including simulation");
    try {
      a.slots().setStackInSlot(1, puller.copy());
      throw new AssertionError("Direct assignment accepted a duplicate role");
    } catch (IllegalArgumentException expected) {
      require(
          a.addon(0, 1).isEmpty() && !a.filter(0, 0).isEmpty(),
          "Refusal leaves installed addon and filter intact");
    }
    require(a.accepts(0, puller), "Replacing the existing role in its own slot is allowed");

    // The menu's client snapshot must enforce the same rule using its own synced contents.
    var client = new ItemStackHandler(a.slots().getSlots());
    client.setStackInSlot(0, puller.copy());
    require(
        !AutomationInventory.acceptsAddon(a.group(0), client, 1, puller),
        "Client prediction refuses duplicate roles");
    for (int group : new int[] {2, 3}) {
      int slot = AutomationInventory.addonSlot(group, 0);
      var pump = new ItemStack(CompanionAddons.INPUT_PUMP.get());
      require(
          a.slots().insertItem(slot, pump.copy(), false).isEmpty(),
          "Independent inventories may each have an input pump");
      require(!a.accepts(slot + 1, pump), "Only one input pump per inventory");
    }
    int energy = AutomationInventory.addonSlot(6, 0);
    a.slots().setStackInSlot(energy, new ItemStack(CompanionAddons.IMPORT_RELAY.get()));
    a.slots().setStackInSlot(energy + 1, new ItemStack(CompanionAddons.EXPORT_RELAY.get()));
    require(
        !a.accepts(energy + 2, new ItemStack(CompanionAddons.IMPORT_RELAY.get()))
            && !a.accepts(energy + 2, new ItemStack(CompanionAddons.EXPORT_RELAY.get())),
        "Input and output coexist, but neither role may be duplicated");

    var player = h.makeMockServerPlayerInLevel();
    player.setPos(
        machine.getBlockPos().getX(), machine.getBlockPos().getY(), machine.getBlockPos().getZ());
    var menu = new MachineMenu(71, player.getInventory(), machine);
    player.containerMenu = menu;
    menu.automation.action(
        player,
        new AutomationNetwork.Action(
            71, menu.widgetSession(), 1, "select", 0, -1, 0, 0, "", 0, ""));
    int destination = menu.automation.slotStart() + 1;
    menu.setCarried(puller.copy());
    menu.clicked(destination, 0, ClickType.PICKUP, player);
    require(
        menu.getCarried().is(puller.getItem()) && a.addon(0, 1).isEmpty(),
        "Cursor insertion refuses duplicate role");
    menu.setCarried(ItemStack.EMPTY);
    player.getInventory().setItem(0, puller.copy());
    menu.clicked(destination, 0, ClickType.SWAP, player);
    require(
        player.getInventory().getItem(0).is(puller.getItem()) && a.addon(0, 1).isEmpty(),
        "Hotbar swap refuses duplicate role");
    // Keep the normal resource inventory full so shift-click only tests addon destinations.
    machine.items.setStackInSlot(0, new ItemStack(Items.STONE, 64));
    var source =
        menu.slots.stream()
            .filter(s -> s.container == player.getInventory() && s.getContainerSlot() == 0)
            .findFirst()
            .orElseThrow();
    menu.clicked(source.index, 0, ClickType.QUICK_MOVE, player);
    for (int c = 1; c < 4; c++)
      require(a.addon(0, c).isEmpty(), "Shift-click cannot add a second puller");
    require(
        player.getInventory().countItem(puller.getItem()) == 1, "Refused addon stays with player");
    player.containerMenu = player.inventoryMenu;
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void legacyDuplicateRolesRecoverWithoutLosingFilters(GameTestHelper h) {
    var machine = place(h);
    var a = machine.automation;
    a.slots().setStackInSlot(0, new ItemStack(CompanionAddons.PULLER.get()));
    a.slots().setStackInSlot(4, new ItemStack(CompanionAddons.ITEM_FILTER.get()));
    var saved = a.save(h.getLevel().registryAccess());
    var contents =
        saved.getCompound("groups").getCompound("input").getList("contents", Tag.TAG_COMPOUND);
    contents.set(1, contents.getCompound(0).copy());
    contents.set(5, contents.getCompound(4).copy());
    a.load(saved, h.getLevel().registryAccess());
    require(
        !a.addon(0, 0).isEmpty() && a.addon(0, 1).isEmpty() && a.filter(0, 1).isEmpty(),
        "Reload keeps the first addon and detaches the duplicate pair");
    a.load(a.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
    var player = h.makeMockServerPlayerInLevel();
    a.recover(player);
    a.recover(player);
    require(
        player.getInventory().countItem(CompanionAddons.PULLER.get()) == 1
            && player.getInventory().countItem(CompanionAddons.ITEM_FILTER.get()) == 1,
        "Duplicate addon and filter survive save/reload and recover exactly once");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void breakingMachineDropsEveryAddonAndConfiguredFilterOnce(GameTestHelper h) {
    var machine = place(h);
    var a = machine.automation;
    var expected = new HashMap<Item, Integer>();
    Item[] modules = {
      CompanionAddons.PULLER.get(),
      CompanionAddons.PUSHER.get(),
      CompanionAddons.INPUT_PUMP.get(),
      CompanionAddons.INPUT_PUMP.get(),
      CompanionAddons.OUTPUT_PUMP.get(),
      CompanionAddons.OUTPUT_PUMP.get(),
      CompanionAddons.IMPORT_RELAY.get()
    };
    for (int g = 0; g < modules.length; g++) {
      a.slots().setStackInSlot(AutomationInventory.addonSlot(g, 0), new ItemStack(modules[g]));
      expected.merge(modules[g], 1, Integer::sum);
    }
    a.slots()
        .setStackInSlot(
            AutomationInventory.addonSlot(6, 1), new ItemStack(CompanionAddons.EXPORT_RELAY.get()));
    expected.put(CompanionAddons.EXPORT_RELAY.get(), 1);
    // Include a detached filter still in recovery, not just currently visible child slots.
    a.slots().setStackInSlot(4, new ItemStack(CompanionAddons.TAG_FILTER.get()));
    a.slots().setStackInSlot(0, new ItemStack(CompanionAddons.PULLER.get()));
    expected.put(CompanionAddons.TAG_FILTER.get(), 1);
    Item[] filters = {
      CompanionAddons.ITEM_FILTER.get(),
      CompanionAddons.LARGE_FILTER.get(),
      CompanionAddons.FLUID_FILTER.get()
    };
    for (int g = 0; g < filters.length; g++) {
      a.slots().setStackInSlot(AutomationInventory.filterSlot(g, 0), new ItemStack(filters[g]));
      expected.put(filters[g], 1);
    }
    require(
        a.edit(
            0,
            0,
            a.generation(0, 0),
            a.revision(0, 0),
            "samples",
            0,
            "sample",
            new ItemStack(Items.DIAMOND),
            h.getLevel().registryAccess()),
        "Configure installed filter");
    machine.items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
    a.transferState("input", 0)
        .put("item", new ItemStack(Items.IRON_INGOT, 2).save(h.getLevel().registryAccess()));
    expected.put(Items.DIAMOND, 3);
    expected.put(Items.IRON_INGOT, 2);
    h.getLevel().destroyBlock(machine.getBlockPos(), true);
    a.dropAll();
    var actual = new HashMap<Item, Integer>();
    var drops =
        h.getLevel()
            .getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(2));
    for (var drop : drops)
      actual.merge(drop.getItem().getItem(), drop.getItem().getCount(), Integer::sum);
    for (var entry : expected.entrySet())
      require(
          Objects.equals(actual.get(entry.getKey()), entry.getValue()),
          "Exact break drops for " + entry.getKey());
    var filter =
        drops.stream()
            .map(ItemEntity::getItem)
            .filter(s -> s.is(CompanionAddons.ITEM_FILTER.get()))
            .findFirst()
            .orElseThrow();
    var provider = filter.getCapability(AddonCapabilities.FILTER);
    require(
        provider.matches(filter, new ItemStack(Items.DIAMOND), h.getLevel().registryAccess())
            && !provider.matches(filter, new ItemStack(Items.DIRT), h.getLevel().registryAccess()),
        "Broken-out filter retains its configuration");
    for (int s = 0; s < a.slots().getSlots(); s++)
      require(a.slots().getStackInSlot(s).isEmpty(), "Dropped slots are cleared");
    h.succeed();
  }
}
