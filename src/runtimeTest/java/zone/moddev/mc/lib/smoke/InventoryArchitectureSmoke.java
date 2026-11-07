package zone.moddev.mc.lib.smoke;

import java.util.*;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.neoforge.*;
import zone.moddev.mc.lib.machine.recipe.*;

final class InventoryArchitectureSmoke {
  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  static void itemMigration(ServerLevel level) {
    var provider = level.registryAccess();
    var original = new ItemStorage(3).slotId(0, "ore").slotId(1, "result").slotId(2, "removed");
    original.restoreStack(0, new ItemStack(Items.IRON_ORE, 40));
    original.restoreStack(1, new ItemStack(Items.GOLD_INGOT, 12));
    original.restoreStack(2, new ItemStack(Items.DIAMOND, 7));
    var smaller = new ItemStorage(2).slotId(0, "result").slotId(1, "ore").limit(16, 1);
    smaller.deserializeNBT(provider, original.serializeNBT(provider));
    require(
        smaller.getStackInSlot(0).is(Items.GOLD_INGOT), "stable item identity survives reordering");
    require(smaller.getStackInSlot(1).getCount() == 16, "reduced limit respected");
    require(smaller.recoveryContents().size() == 2, "removed slot and excess count recovered");
    for (int i = 0; i < 3; i++) smaller.deserializeNBT(provider, smaller.serializeNBT(provider));
    require(smaller.recoveryContents().size() == 2, "repeated loading never duplicates recovery");
    int recovered =
        smaller.recoveryContents().stream()
            .mapToInt(t -> ItemStack.parseOptional(provider, (CompoundTag) t).getCount())
            .sum();
    require(recovered == 31, "all 24 displaced iron and 7 diamonds preserved");
    var expanded = new ItemStorage(3).slotId(0, "ore").slotId(1, "result").slotId(2, "removed");
    expanded.deserializeNBT(provider, smaller.serializeNBT(provider));
    require(
        expanded.getStackInSlot(0).getCount() == 40
            && expanded.getStackInSlot(2).getCount() == 7
            && expanded.recoveryContents().isEmpty(),
        "restored slots and limits recover all displaced items");
    try {
      smaller.recover(
          payload -> {
            throw new IllegalStateException("recovery refused");
          });
    } catch (IllegalStateException expected) {
    }
    require(smaller.recoveryContents().size() == 2, "failed ownership handoff retains contents");
    var payloads = new ArrayList<CompoundTag>();
    smaller.recover(payloads::add);
    smaller.recover(payloads::add);
    require(
        payloads.size() == 1 && smaller.recoveryContents().isEmpty(),
        "successful handoff exactly once");

    var legacy = new ItemStorage(2);
    legacy.restoreStack(1, new ItemStack(Items.EMERALD, 3));
    var shrunk = new ItemStorage(1);
    shrunk.deserializeNBT(provider, legacy.serializeNBT(provider));
    require(shrunk.recoveryContents().size() == 1, "legacy out-of-range slot preserved");
    var restored = new ItemStorage(2);
    restored.deserializeNBT(provider, shrunk.serializeNBT(provider));
    require(
        restored.getStackInSlot(1).getCount() == 3 && restored.recoveryContents().isEmpty(),
        "restored legacy slot recovers saved stack");
    var remapped = new ItemStorage(2).slotId(0, "emeralds").slotId(1, "unused").limit(1, 0);
    remapped.deserializeNBT(provider, legacy.serializeNBT(provider), slot -> 1 - slot);
    require(
        remapped.getStackInSlot(0).is(Items.EMERALD), "explicit legacy index migration applied");
    var movedAgain = new ItemStorage(2).slotId(0, "unused").slotId(1, "emeralds");
    movedAgain.deserializeNBT(provider, remapped.serializeNBT(provider));
    require(
        movedAgain.getStackInSlot(1).getCount() == 3 && movedAgain.recoveryContents().isEmpty(),
        "legacy capacity overflow acquires stable identity before another migration");

    var unknown = new CompoundTag();
    unknown.putString("id", "removed_mod:lost_item");
    unknown.putInt("count", 9);
    unknown.putInt("Slot", 0);
    var list = new ListTag();
    list.add(unknown);
    var tag = new CompoundTag();
    tag.put("Items", list);
    restored.deserializeNBT(provider, tag);
    require(
        restored.recoveryContents().getCompound(0).equals(unknown),
        "unknown item NBT retained verbatim");
  }

  static void fluidMigration(ServerLevel level) {
    var provider = level.registryAccess();
    var original = new FluidStorage();
    original.addTank(2000, Role.INPUT);
    original.addTank(2000, Role.INPUT);
    original.tankId(0, "water").tankId(1, "lava");
    original.restoreFluid(0, new FluidStack(Fluids.WATER, 1500));
    original.restoreFluid(1, new FluidStack(Fluids.LAVA, 700));
    var smaller = new FluidStorage();
    smaller.addTank(500, Role.INPUT);
    smaller.tankId(0, "water");
    smaller.deserializeNBT(provider, original.serializeNBT(provider));
    require(smaller.getFluidInTank(0).getAmount() == 500, "reduced capacity respected");
    require(smaller.recoveryContents().size() == 2, "removed tank and capacity excess preserved");
    for (int i = 0; i < 3; i++) smaller.deserializeNBT(provider, smaller.serializeNBT(provider));
    int recovered =
        smaller.recoveryContents().stream()
            .mapToInt(t -> FluidStack.parseOptional(provider, (CompoundTag) t).getAmount())
            .sum();
    require(recovered == 1700, "repeated fluid loading conserves displaced contents");
    original.deserializeNBT(provider, smaller.serializeNBT(provider));
    require(
        original.getFluidInTank(0).getAmount() == 1500
            && original.getFluidInTank(1).getAmount() == 700
            && original.recoveryContents().isEmpty(),
        "restored tanks and capacities recover all displaced fluid");
    var reordered = new FluidStorage();
    reordered.addTank(2000, Role.STORAGE);
    reordered.addTank(2000, Role.STORAGE);
    reordered.tankId(0, "lava").tankId(1, "water");
    reordered.deserializeNBT(provider, original.serializeNBT(provider));
    require(
        reordered.getFluidInTank(0).is(Fluids.LAVA)
            && reordered.getFluidInTank(1).getAmount() == 1500,
        "tank identities survive reordering");
    var payloads = new ArrayList<CompoundTag>();
    smaller.recover(payloads::add);
    smaller.recover(payloads::add);
    require(payloads.size() == 1, "fluid recovery handed off exactly once");
    var legacy = new FluidStorage();
    legacy.addTank(2000, Role.INPUT);
    legacy.addTank(2000, Role.INPUT);
    legacy.restoreFluid(1, new FluidStack(Fluids.WATER, 1000));
    var migrated = new FluidStorage();
    migrated.addTank(100, Role.INPUT);
    migrated.addTank(2000, Role.INPUT);
    migrated.tankId(0, "water").tankId(1, "unused");
    migrated.deserializeNBT(provider, legacy.serializeNBT(provider), tank -> 1 - tank);
    require(
        migrated.getFluidInTank(0).getAmount() == 100, "explicit legacy tank migration applied");
    var expanded = new FluidStorage();
    expanded.addTank(2000, Role.INPUT);
    expanded.addTank(2000, Role.INPUT);
    expanded.tankId(0, "unused").tankId(1, "water");
    expanded.deserializeNBT(provider, migrated.serializeNBT(provider));
    require(
        expanded.getFluidInTank(1).getAmount() == 1000 && expanded.recoveryContents().isEmpty(),
        "legacy fluid overflow follows acquired stable identity");
  }

  private static ResourceGroup<IItemHandler> items(String id, IoMode mode, Integer... members) {
    return new ResourceGroup<>(
        id,
        Component.literal(id),
        0x123456,
        ResourceType.ITEMS,
        mode,
        List.of(members),
        List.of(),
        new SideConfig(mode));
  }

  private static ChestBlockEntity owner(ServerLevel level) {
    var owner = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
    owner.setLevel(level);
    return owner;
  }

  private static void rejected(Runnable action, String text) {
    try {
      action.run();
    } catch (IllegalArgumentException e) {
      require(
          e.getMessage().contains(text),
          "diagnostic names the invalid member/group: " + e.getMessage());
      return;
    }
    throw new AssertionError("Expected rejection: " + text);
  }

  static void bindings(ServerLevel level) {
    var owner = owner(level);
    var storage = new ItemStorage(3).role(Role.INPUT, 0, 1).role(Role.OUTPUT, 2);
    rejected(
        () ->
            new MachineResources(owner, () -> Direction.NORTH)
                .items(items("bad", IoMode.INPUT, 4), storage),
        "member 4");
    rejected(
        () ->
            new MachineResources(owner, () -> Direction.NORTH)
                .items(items("bad", IoMode.BOTH, 0), storage),
        "role INPUT");
    var left = items("left", IoMode.INPUT, 0);
    var right = items("right", IoMode.INPUT, 1);
    var resources =
        new MachineResources(owner, () -> Direction.NORTH)
            .items(left, storage)
            .items(right, storage)
            .items(items("result", IoMode.OUTPUT, 2), storage)
            .recipeGroup("ingredients", "left", "right")
            .build();
    var port = left.port(Direction.NORTH);
    require(port.getSlots() == 3, "group ports keep physical slot numbering");
    require(
        port.insertItem(1, new ItemStack(Items.IRON_ORE), false).getCount() == 1,
        "group port cannot touch another input group");
    require(
        port.insertItem(0, new ItemStack(Items.IRON_ORE), false).isEmpty(),
        "group port accepts its own member");
    require(
        ((ItemGroup) resources.recipes().group("ingredients").orElseThrow()).size() == 2,
        "recipe group combines shared definitions");
    left.sides().set(RelativeFace.FRONT, IoMode.DISABLED);
    require(
        port.insertItem(0, new ItemStack(Items.IRON_ORE), true).getCount() == 1,
        "existing port follows side edits");
    require(
        left.port(null).insertItem(2, new ItemStack(Items.IRON_ORE), true).getCount() == 1,
        "unsided port remains isolated");
  }

  static void screenValidation(ServerLevel level) throws Exception {
    var storage = new ItemStorage(1);
    var group =
        new ResourceGroup<>(
            "input",
            Component.literal("Input"),
            0x123456,
            ResourceType.ITEMS,
            IoMode.BOTH,
            List.of(0),
            List.of("input"),
            new SideConfig(IoMode.BOTH));
    var resources =
        new MachineResources(owner(level), () -> Direction.NORTH).items(group, storage).build();
    var host =
        new zone.moddev.mc.lib.gui.runtime.ScreenHost() {
          public ItemStorage screenItems() {
            return storage;
          }

          public AutomationInventory screenAutomation() {
            return resources.automation();
          }
        };
    for (int variant = 0; variant < 3; variant++) {
      String xml =
          "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test\""
              + " block-entity=\"net.minecraft.world.level.block.entity.ChestBlockEntity\"><slot"
              + " id=\""
              + (variant == 1 ? "wrong" : "input")
              + "\" index=\""
              + (variant == 2 ? 2 : 0)
              + "\"/></screen>";
      var screen =
          zone.moddev.mc.lib.gui.load.ScreenReader.read(
              ResourceLocation.parse("mmdlib_smoke:validation"),
              "validation.xml",
              new java.io.ByteArrayInputStream(
                  xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      if (variant == 0)
        zone.moddev.mc.lib.gui.runtime.ScreenInventoryValidation.validate(screen, host);
      else
        rejected(
            () -> zone.moddev.mc.lib.gui.runtime.ScreenInventoryValidation.validate(screen, host),
            variant == 1 ? "missing screen element 'input'" : "index 2");
    }
  }

  static void recipeFilters(ServerLevel level) {
    var owner = owner(level);
    var storage =
        new ItemStorage(3)
            .role(Role.INPUT, 0)
            .role(Role.OUTPUT, 1, 2)
            .filter(stack -> !stack.is(Items.DEEPSLATE_COPPER_ORE), 0);
    var fluids = new FluidStorage();
    fluids.addTank(4000, Role.STORAGE);
    var water =
        new ResourceGroup<>(
            "water",
            Component.literal("Water"),
            0x334455,
            ResourceType.FLUIDS,
            IoMode.BOTH,
            List.of(0),
            List.of(),
            new SideConfig(IoMode.BOTH));
    var energy = new EnergyBuffer(10000, 1000);
    var power =
        new ResourceGroup<>(
            "energy",
            Component.literal("Energy"),
            0x334455,
            ResourceType.ENERGY,
            IoMode.INPUT,
            List.of(0),
            List.of(),
            new SideConfig(IoMode.INPUT));
    var resources =
        new MachineResources(owner, () -> Direction.NORTH)
            .items(items("input", IoMode.INPUT, 0), storage)
            .items(items("output", IoMode.OUTPUT, 1), storage)
            .items(items("byproduct", IoMode.OUTPUT, 2), storage)
            .fluids(water, fluids)
            .energy(power, energy)
            .recipeInputs(MachineRecipeTypeSmoke.GRINDING, "input", "water")
            .build();
    require(
        storage.isItemValid(0, new ItemStack(Items.COPPER_ORE)),
        "tag recipe admits input before other ingredients arrive");
    require(
        storage.isItemValid(0, new ItemStack(Items.IRON_ORE)),
        "union of recipes admits alternative input");
    require(!storage.isItemValid(0, new ItemStack(Items.DIAMOND)), "unrelated item refused");
    require(
        !storage.isItemValid(0, new ItemStack(Items.DEEPSLATE_COPPER_ORE)),
        "explicit storage filter still applies");
    storage.filter(stack -> true, 0);
    require(
        !storage.isItemValid(0, new ItemStack(Items.DIAMOND)),
        "changing the base filter cannot erase linked recipe rules");
    require(
        storage.view(IoMode.INPUT).insertItem(0, new ItemStack(Items.DIAMOND), true).getCount()
            == 1,
        "automation obeys recipe filter");
    require(
        fluids.fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE) == 0,
        "unrelated fluid refused");
    require(
        fluids.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 1,
        "input amount need not satisfy recipe yet");
    fluids.restoreFluid(0, FluidStack.EMPTY);
    require(
        fluids.fillResult(0, new FluidStack(Fluids.LAVA, 10), FluidAction.EXECUTE) == 10,
        "machine products bypass input-only rule");
    require(
        storage.insertResult(1, new ItemStack(Items.RAW_COPPER), false).isEmpty(),
        "recipe products remain insertable");
    var client = new zone.moddev.mc.lib.gui.runtime.ClientMenuItems(3, storage);
    require(
        !client.isItemValid(0, new ItemStack(Items.DIAMOND)), "menu prediction uses input rules");

    var manager = level.getRecipeManager();
    var original = new ArrayList<>(manager.getRecipes());
    try {
      var recipe =
          new MachineRecipe(
              Map.of(
                  "input",
                  List.of(new MachineIngredient.ItemPart(Ingredient.of(Items.DIAMOND), 32)),
                  "water",
                  List.of(
                      new MachineIngredient.FluidPart(
                          HolderSet.direct(Fluids.LAVA.builtInRegistryHolder()), 500))),
              Map.of(),
              20);
      var changed = new ArrayList<RecipeHolder<?>>(original);
      changed.removeIf(holder -> holder.value().getType() == MachineRecipeTypeSmoke.GRINDING);
      changed.add(
          new RecipeHolder<>(
              ResourceLocation.parse("mmdlib_smoke:admission"),
              new MachineRecipeEntry(MachineRecipeTypeSmoke.GRINDING, recipe)));
      manager.replaceRecipes(changed);
      require(
          storage.isItemValid(0, new ItemStack(Items.DIAMOND))
              && !storage.isItemValid(0, new ItemStack(Items.IRON_ORE)),
          "recipe replacement immediately updates item admission");
      require(
          fluids.isFluidValid(0, new FluidStack(Fluids.LAVA, 1))
              && !fluids.isFluidValid(0, new FluidStack(Fluids.WATER, 1)),
          "recipe replacement immediately updates fluid admission");
      changed.removeIf(holder -> holder.value().getType() == MachineRecipeTypeSmoke.GRINDING);
      manager.replaceRecipes(changed);
      require(
          !storage.isItemValid(0, new ItemStack(Items.DIAMOND)),
          "no recipes admits no linked inputs");
    } finally {
      manager.replaceRecipes(original);
    }
    require(storage.isItemValid(0, new ItemStack(Items.IRON_ORE)), "reload restoration observed");
  }
}
