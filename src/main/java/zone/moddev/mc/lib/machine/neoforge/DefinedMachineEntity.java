package zone.moddev.mc.lib.machine.neoforge;

import java.util.*;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.runtime.ScreenInventoryValidation;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** Shared runtime assembled from XML. There is no per-machine Java subclass. */
public final class DefinedMachineEntity extends BasePoweredMachine {
  public final MachineDefinition definition;
  public final ItemStorage items;
  public final MachineResources resources;
  public final MachineInventoryLifecycle inventory;
  private final RedstoneControl redstone;

  public DefinedMachineEntity(
      BlockEntityType<?> type,
      BlockPos pos,
      BlockState state,
      MachineDefinition definition,
      MachineRecipeType recipes) {
    super(
        type,
        pos,
        state,
        definition.power().capacity(),
        definition.power().input(),
        definition.power().output());
    this.definition = definition;
    items = new ItemStorage(definition.slots().size()).onChange(this::setChanged);
    for (int i = 0; i < definition.slots().size(); i++) {
      var slot = definition.slots().get(i);
      items.slotId(i, slot.id()).role(slot.role(), i).limit(slot.limit(), i);
    }
    resources = new MachineResources(this, this::facing);
    var screen = Screens.get(definition.id()).orElseThrow().screen();
    for (var group : definition.groups()) {
      var highlights =
          screen.elements().stream()
              .filter(e -> e instanceof Element.Slot s && group.members().contains(s.index()))
              .flatMap(e -> e.common().id().stream())
              .toList();
      resources.items(
          new ResourceGroup<>(
              group.id(),
              Component.literal(group.id()),
              group.color(),
              ResourceType.ITEMS,
              group.mode(),
              group.members(),
              highlights,
              new SideConfig(group.mode())),
          items);
    }
    var energyHighlights =
        screen.elements().stream()
            .filter(e -> e instanceof Element.EnergyGauge)
            .flatMap(e -> e.common().id().stream())
            .toList();
    resources.energy(
        new ResourceGroup<>(
            "energy",
            Component.literal("Energy"),
            definition.power().color(),
            ResourceType.ENERGY,
            IoMode.INPUT,
            List.of(0),
            energyHighlights,
            new SideConfig(IoMode.INPUT)),
        energy);
    for (var group : definition.groups())
      if (group.mode() == IoMode.INPUT) resources.recipeInputs(recipes, group.id());
    resources.build();
    features.add(
        new RecipeProcessor(
            definition.processor(),
            recipes,
            resources.recipes(),
            Component.literal(definition.processorName())));
    inventory = poweredInventory(resources.automation()).items(definition.itemStorageId(), items);
    redstone = definition.redstone() ? new RedstoneControl(this, List.of(features)) : null;
    ScreenInventoryValidation.validate(this);
  }

  @Override
  public String screenDefinitionFingerprint() {
    return definition.checksum();
  }

  @Override
  public ItemStorage screenItems() {
    return items;
  }

  @Override
  public RedstoneControl screenRedstone() {
    return redstone;
  }

  @Override
  protected MachineInventoryLifecycle machineInventory() {
    return inventory;
  }

  @Override
  public int[] screenRecipeSlots(ResourceLocation category) {
    if (!definition.recipeType().id().equals(category)) return new int[0];
    return definition.groups().stream()
        .filter(g -> g.mode() == IoMode.INPUT)
        .flatMap(g -> g.members().stream())
        .mapToInt(Integer::intValue)
        .toArray();
  }

  @Override
  protected void recoverResources(Level level, BlockPos pos, CompoundTag payload) {
    net.minecraft.world.Containers.dropItemStack(
        level,
        pos.getX() + .5,
        pos.getY() + .5,
        pos.getZ() + .5,
        MachineRecoveryItem.create(payload));
  }
}
