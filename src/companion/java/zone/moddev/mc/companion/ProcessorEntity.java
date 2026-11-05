package zone.moddev.mc.companion;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.common.runtime.DropsContents;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.FeatureHost;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.neoforge.FeatureSync;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;

/** The dual crusher deliberately runs two lanes over the same input and output groups. */
public final class ProcessorEntity extends DemoEntity
    implements ScreenHost, DropsContents, Clearable {
  public enum Kind {
    CRUSHER,
    TRANSMUTER,
    DUAL,
    LOGISTICS
  }

  public final ItemStorage items;
  public final AutomationInventory automation;
  public final FluidStorage fluids = new FluidStorage().onChange(this::setChanged);
  public final List<RecipeProcessor> lanes;
  public final FeatureHost features =
      new FeatureHost(this::setChanged, () -> FeatureSync.send(this));
  private final Kind kind;
  // 0 ignores redstone, 1 requires a signal, 2 requires no signal. Manual pause is separate.
  private int redstoneMode;
  private String machineName = "Crusher";
  private int reservePercent;

  public Component getMachineName() {
    return Component.literal(machineName);
  }

  public int getReservePercent() {
    return reservePercent;
  }

  public boolean rename(String name) {
    if (name.isBlank() || name.length() > 32 || name.codePoints().anyMatch(Character::isISOControl))
      return false;
    machineName = name.strip();
    setChanged();
    return true;
  }

  public boolean setReservePercent(int value) {
    if (value < 0 || value > 100) return false;
    reservePercent = value;
    setChanged();
    return true;
  }

  public ProcessorEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Kind kind) {
    super(type, pos, state);
    this.kind = kind;
    if (kind == Kind.CRUSHER)
      fluids.addTank(
          4_000,
          Role.STORAGE,
          fluid -> fluid.getFluid() == Fluids.WATER || fluid.getFluid() == Fluids.LAVA);
    if (kind == Kind.LOGISTICS)
      for (int i = 0; i < 4; i++) fluids.addTank(4000, i < 2 ? Role.INPUT : Role.OUTPUT);
    var inventory = MachineInventory.builder().group("energy", StorageGroups.energy(energy));
    if (kind == Kind.TRANSMUTER) {
      // One key, and one item: the recipe consumes the whole slot before inserting gold.
      items = new ItemStorage(1).limit(1, 0).onChange(this::setChanged);
      inventory.group("slot", StorageGroups.items(items, 0));
    } else {
      items =
          new ItemStorage(2).role(Role.INPUT, 0).role(Role.OUTPUT, 1).onChange(this::setChanged);
      inventory
          .group("input", StorageGroups.items(items, 0))
          .group("output", StorageGroups.items(items, 1));
    }
    var shared = inventory.build();
    var recipeType = kind == Kind.TRANSMUTER ? CompanionMod.TRANSMUTING : CompanionMod.CRUSHING;
    var processors = new ArrayList<RecipeProcessor>();
    for (int i = 0; i < (kind == Kind.DUAL ? 2 : 1); i++) {
      var processor = new RecipeProcessor("lane_" + i, recipeType, shared);
      processors.add(processor);
      features.add(processor);
    }
    lanes = List.copyOf(processors);
    var groups = new ArrayList<ResourceGroup<?>>();
    groups.add(
        itemGroup("input", 0, 0x568bd8, kind == Kind.TRANSMUTER ? IoMode.BOTH : IoMode.INPUT));
    if (kind != Kind.TRANSMUTER) groups.add(itemGroup("output", 1, 0xe48f45, IoMode.OUTPUT));
    int[] colours = {0x64bfc1, 0xa678cb, 0xe0c55d, 0xce668e};
    for (int i = 0; i < fluids.getTanks(); i++) {
      final int tank = i;
      var group =
          new ResourceGroup<>(
              "tank_" + i,
              Component.translatable("screen.mmdlib_companion.group.tank", i + 1),
              colours[i],
              ResourceType.FLUIDS,
              kind == Kind.CRUSHER ? IoMode.BOTH : (i < 2 ? IoMode.INPUT : IoMode.OUTPUT),
              List.of(i),
              List.of(i == 0 ? "tank" : "tank_" + i),
              new SideConfig(
                  kind == Kind.CRUSHER ? IoMode.BOTH : (i < 2 ? IoMode.INPUT : IoMode.OUTPUT)));
      group.bind(
          () -> Direction.NORTH,
          side -> fluids.view(index -> index == tank ? group.mode(side) : IoMode.DISABLED));
      groups.add(group);
    }
    var energyGroup =
        new ResourceGroup<>(
            "energy",
            Component.translatable("screen.mmdlib_companion.group.energy"),
            0xd85b5b,
            ResourceType.ENERGY,
            kind == Kind.LOGISTICS ? IoMode.BOTH : IoMode.INPUT,
            List.of(0),
            List.of("energy"),
            new SideConfig(kind == Kind.LOGISTICS ? IoMode.BOTH : IoMode.INPUT));
    if (kind == Kind.LOGISTICS) energy.setRates(1000, 1000);
    energyGroup.bind(() -> Direction.NORTH, side -> energy.view(() -> energyGroup.mode(side)));
    groups.add(energyGroup);
    automation = new AutomationInventory(this, groups);
  }

  private ResourceGroup<net.neoforged.neoforge.items.IItemHandler> itemGroup(
      String id, int slot, int colour, IoMode mode) {
    var group =
        new ResourceGroup<>(
            id,
            Component.translatable("screen.mmdlib_companion.group." + id),
            colour,
            ResourceType.ITEMS,
            mode,
            List.of(slot),
            List.of(id),
            new SideConfig(mode));
    return group.bind(
        () -> Direction.NORTH,
        side -> items.view(index -> index == slot ? group.mode(side) : IoMode.DISABLED));
  }

  @Override
  public AutomationInventory screenAutomation() {
    return automation;
  }

  @Override
  public void serverTick() {
    if (kind != Kind.LOGISTICS
        && redstoneAllowsRun()
        && energy.stored() >= energy.capacity() * reservePercent / 100) features.tick(level);
    AutomationTransfers.tick(automation, energy);
  }

  public FluidStack getTank1() {
    return fluids.getFluidInTank(1);
  }

  public FluidStack getTank2() {
    return fluids.getFluidInTank(2);
  }

  public FluidStack getTank3() {
    return fluids.getFluidInTank(3);
  }

  public int getRedstoneMode() {
    return redstoneMode;
  }

  public void cycleRedstoneMode() {
    redstoneMode = (redstoneMode + 1) % 3;
    setChanged();
  }

  private boolean redstoneAllowsRun() {
    return redstoneMode == 0 || (level.hasNeighborSignal(worldPosition) == (redstoneMode == 1));
  }

  public void toggle(int index) {
    if (index < 0 || index >= lanes.size()) return;
    var processor = lanes.get(index);
    if (processor.paused()) processor.resume();
    else processor.interrupt();
    // Redstone can hold the feature tick, so persist and sync a manual toggle immediately.
    setChanged();
    FeatureSync.send(this);
  }

  public int getProgress() {
    return lanes.getFirst().progress();
  }

  public boolean hasTank() {
    return fluids.getTanks() > 0;
  }

  public FluidStack getTankFluid() {
    return fluids.getFluidInTank(0);
  }

  public int getTankCapacity() {
    return fluids.getTankCapacity(0);
  }

  public int getTime() {
    return lanes.getFirst().time();
  }

  public int getSecondProgress() {
    return lanes.size() == 2 ? lanes.get(1).progress() : 0;
  }

  public int getSecondTime() {
    return lanes.size() == 2 ? lanes.get(1).time() : 0;
  }

  public boolean getPaused() {
    return lanes.getFirst().paused();
  }

  public boolean getSecondPaused() {
    return lanes.size() == 2 && lanes.get(1).paused();
  }

  public List<Component> getWarnings() {
    var warnings = new ArrayList<Component>();
    if (energy.stored() < energy.capacity() * reservePercent / 100)
      warnings.add(
          Component.translatable("screen.mmdlib_companion.reserve.waiting", reservePercent));
    if (!redstoneAllowsRun())
      warnings.add(Component.translatable("screen.mmdlib_companion.redstone.waiting"));
    for (int i = 0; i < lanes.size(); i++) {
      var status = lanes.get(i).status();
      if (status == RecipeProcessor.Status.STALLED || status == RecipeProcessor.Status.BLOCKED)
        warnings.add(
            Component.translatable("screen.mmdlib_companion.lane_" + (i == 0 ? "a" : "b"))
                .append(": ")
                .append(
                    Component.translatable(
                        "status.mmdlib_companion."
                            + status.name().toLowerCase(java.util.Locale.ROOT))));
    }
    return warnings;
  }

  @Override
  public ItemStorage screenItems() {
    return items;
  }

  @Override
  public int[] screenRecipeSlots(ResourceLocation category) {
    var recipeType = kind == Kind.TRANSMUTER ? CompanionMod.TRANSMUTING : CompanionMod.CRUSHING;
    return recipeType.id().equals(category) ? new int[] {0} : new int[0];
  }

  @Override
  public void dropContents(Level level, BlockPos pos) {
    items.dropAll(level, pos);
    automation.dropAll(
        payload ->
            net.minecraft.world.Containers.dropItemStack(
                level,
                pos.getX() + .5,
                pos.getY() + .5,
                pos.getZ() + .5,
                RecoveryCapsuleItem.create(payload)));
  }

  @Override
  public void clearContent() {
    automation.clear();
    lanes.forEach(RecipeProcessor::reset);
    for (int slot = 0; slot < items.getSlots(); slot++) {
      items.setPlan(slot, SlotPlan.NONE);
      items.setStackInSlot(slot, ItemStack.EMPTY);
    }
    energy.setStored(0);
    for (int i = 0; i < fluids.getTanks(); i++) fluids.tank(i).setFluid(FluidStack.EMPTY);
    setChanged();
    FeatureSync.send(this);
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.put("items", items.serializeNBT(registries));
    tag.put("automation", automation.save(registries));
    tag.putInt("redstone_mode", redstoneMode);
    tag.putString("machine_name", machineName);
    tag.putInt("reserve_percent", reservePercent);
    if (hasTank()) tag.put("fluids", fluids.serializeNBT(registries));
    tag.put("features", features.save(registries.createSerializationContext(NbtOps.INSTANCE)));
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    if (FeatureSync.read(features, tag, registries)) return;
    super.loadAdditional(tag, registries);
    int savedMode = tag.getInt("redstone_mode");
    redstoneMode = savedMode >= 0 && savedMode <= 2 ? savedMode : 0;
    String savedName = tag.getString("machine_name");
    machineName =
        !savedName.isBlank()
                && savedName.length() <= 32
                && savedName.codePoints().noneMatch(Character::isISOControl)
            ? savedName.strip()
            : "Crusher";
    reservePercent = Math.clamp(tag.getInt("reserve_percent"), 0, 100);
    items.deserializeNBT(registries, tag.getCompound("items"));
    automation.load(tag.getCompound("automation"), registries);
    if (hasTank() && tag.contains("fluids"))
      fluids.deserializeNBT(registries, tag.getCompound("fluids"));
    features.load(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("features"));
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return FeatureSync.updateTag(features, registries);
  }

  @Override
  public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
