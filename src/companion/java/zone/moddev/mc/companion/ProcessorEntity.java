package zone.moddev.mc.companion;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
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
public final class ProcessorEntity extends DemoEntity implements ScreenHost, DropsContents, Clearable {
  public enum Kind {
    CRUSHER,
    TRANSMUTER,
    DUAL
  }

  public final ItemStorage items;
  public final List<RecipeProcessor> lanes;
  public final FeatureHost features = new FeatureHost(this::setChanged, () -> FeatureSync.send(this));
  private final Kind kind;

  public ProcessorEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Kind kind) {
    super(type, pos, state);
    this.kind = kind;
    var inventory = MachineInventory.builder().group("energy", StorageGroups.energy(energy));
    if (kind == Kind.TRANSMUTER) {
      // One key, and one item: the recipe consumes the whole slot before inserting gold.
      items = new ItemStorage(1).limit(1, 0).onChange(this::setChanged);
      inventory.group("slot", StorageGroups.items(items, 0));
    } else {
      items = new ItemStorage(2).role(Role.INPUT, 0).role(Role.OUTPUT, 1).onChange(this::setChanged);
      inventory.group("input", StorageGroups.items(items, 0))
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
  }

  @Override
  public void serverTick() {
    features.tick(level);
  }

  public void toggle(int index) {
    if (index < 0 || index >= lanes.size()) return;
    var processor = lanes.get(index);
    if (processor.paused()) processor.resume();
    else processor.interrupt();
  }

  public int getProgress() {
    return lanes.getFirst().progress();
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
    for (int i = 0; i < lanes.size(); i++) {
      var status = lanes.get(i).status();
      if (status == RecipeProcessor.Status.STALLED || status == RecipeProcessor.Status.BLOCKED)
        warnings.add(Component.translatable("screen.mmdlib_companion.lane_" + (i == 0 ? "a" : "b"))
            .append(": ").append(Component.translatable(
                "status.mmdlib_companion." + status.name().toLowerCase(java.util.Locale.ROOT))));
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
  }

  @Override
  public void clearContent() {
    lanes.forEach(RecipeProcessor::interrupt);
    for (int slot = 0; slot < items.getSlots(); slot++) {
      items.setPlan(slot, SlotPlan.NONE);
      items.setStackInSlot(slot, ItemStack.EMPTY);
    }
    energy.setStored(0);
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.put("items", items.serializeNBT(registries));
    tag.put("features", features.save(registries.createSerializationContext(NbtOps.INSTANCE)));
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    if (FeatureSync.read(features, tag, registries)) return;
    super.loadAdditional(tag, registries);
    items.deserializeNBT(registries, tag.getCompound("items"));
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
