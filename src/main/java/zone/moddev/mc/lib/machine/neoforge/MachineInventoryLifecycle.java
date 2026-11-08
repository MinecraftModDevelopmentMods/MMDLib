package zone.moddev.mc.lib.machine.neoforge;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.entity.BlockEntity;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.machine.feature.FeatureHost;
import zone.moddev.mc.lib.machine.inventory.MachineStorageKeys;

/**
 * Composes storage persistence, processor restoration, transfer policy and displaced-content
 * recovery.
 */
public final class MachineInventoryLifecycle {
  public enum PausePolicy {
    PROCESSING_ONLY(true, false),
    TRANSFERS_ONLY(false, true),
    BOTH(true, true),
    NEITHER(false, false);
    private final boolean processing, transfers;

    PausePolicy(boolean processing, boolean transfers) {
      this.processing = processing;
      this.transfers = transfers;
    }

    public boolean processingEnabled(boolean paused) {
      return !paused || !processing;
    }

    public boolean transfersEnabled(boolean paused) {
      return !paused || !transfers;
    }
  }

  private final BlockEntity owner;
  private final FeatureHost features;
  private final AutomationInventory automation;
  private final EnergyBuffer power;
  private final Map<String, Object> storages = new LinkedHashMap<>();

  public MachineInventoryLifecycle(
      BlockEntity owner, FeatureHost features, AutomationInventory automation, EnergyBuffer power) {
    this.owner = Objects.requireNonNull(owner);
    this.features = Objects.requireNonNull(features);
    this.automation = Objects.requireNonNull(automation);
    this.power = Objects.requireNonNull(power);
  }

  public MachineInventoryLifecycle items(String saveKey, ItemStorage storage) {
    return add(saveKey, storage);
  }

  public MachineInventoryLifecycle fluids(String saveKey, FluidStorage storage) {
    return add(saveKey, storage);
  }

  public MachineInventoryLifecycle energy(String saveKey, EnergyBuffer storage) {
    return add(saveKey, storage);
  }

  public AutomationInventory automation() {
    return automation;
  }

  /** Ensures a powered base cannot omit its owned buffer or attach another machine's lifecycle. */
  void bindPower(BlockEntity owner, FeatureHost features, EnergyBuffer power) {
    if (this.owner != owner || this.features != features || this.power != power)
      throw new IllegalArgumentException(
          "Powered lifecycle must use its machine's owner, features and energy");
    if (storages.get("energy") != power) add("energy", power);
  }

  private MachineInventoryLifecycle add(String key, Object storage) {
    Objects.requireNonNull(storage);
    if (key == null
        || key.isBlank()
        || MachineStorageKeys.RESERVED.contains(key)
        || storages.containsKey(key)
        || storages.containsValue(storage))
      throw new IllegalArgumentException("Duplicate or reserved storage key: " + key);
    storages.put(key, storage);
    return this;
  }

  public void save(CompoundTag tag, HolderLookup.Provider registries) {
    storages.forEach(
        (key, storage) -> {
          if (storage instanceof ItemStorage items) tag.put(key, items.serializeNBT(registries));
          else if (storage instanceof FluidStorage fluids)
            tag.put(key, fluids.serializeNBT(registries));
          else tag.put(key, ((EnergyBuffer) storage).serializeNBT(registries));
        });
    tag.put("automation", automation.save(registries));
    tag.put("features", features.save(registries.createSerializationContext(NbtOps.INSTANCE)));
  }

  public void load(CompoundTag tag, HolderLookup.Provider registries) {
    load(tag, registries, (key, saved) -> IntUnaryOperator.identity());
  }

  /** Resolves pre-ID item/tank index mappings from each storage key and the complete world save. */
  public void load(
      CompoundTag tag,
      HolderLookup.Provider registries,
      BiFunction<String, CompoundTag, IntUnaryOperator> legacySlots) {
    features.reset();
    storages.forEach(
        (key, storage) -> {
          if (storage instanceof ItemStorage items)
            items.deserializeNBT(registries, tag.getCompound(key), legacySlots.apply(key, tag));
          else if (storage instanceof FluidStorage fluids)
            fluids.deserializeNBT(registries, tag.getCompound(key), legacySlots.apply(key, tag));
          else if (tag.contains(key))
            ((EnergyBuffer) storage).deserializeNBT(registries, tag.get(key));
        });
    automation.load(tag.getCompound("automation"), registries);
    if (tag.contains("features"))
      features.load(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("features"));
  }

  public void tick(boolean processingAllowed, boolean paused, PausePolicy policy) {
    var level = owner.getLevel();
    if (level == null || level.isClientSide) return;
    features.tick(level, processingAllowed && policy.processingEnabled(paused));
    // Refunds are already owed; a pause must not prevent their return.
    automation.settleRefund(power);
    if (policy.transfersEnabled(paused)) AutomationTransfers.tick(automation, power);
  }

  /** Explicit destructive reset, including displaced contents and pending transfers. */
  public void clear() {
    features.reset();
    automation.clear();
    for (var storage : storages.values()) {
      if (storage instanceof ItemStorage items) items.clear();
      else if (storage instanceof FluidStorage fluids) fluids.clear();
      else ((EnergyBuffer) storage).setStored(0);
    }
    owner.setChanged();
  }

  /**
   * Ordinary fluid/energy destruction remains a host policy; displaced resources are always
   * recovered.
   */
  public void dropContents(Consumer<CompoundTag> recoverResources) {
    var level = owner.getLevel();
    if (level == null || level.isClientSide) return;
    for (var storage : storages.values()) {
      if (storage instanceof FluidStorage fluids) fluids.recover(recoverResources);
    }
    automation.dropAll(recoverResources);
    features.reset();
    for (var storage : storages.values())
      if (storage instanceof ItemStorage items)
        items.dropAll(level, owner.getBlockPos(), recoverResources);
  }
}
