package zone.moddev.mc.lib.api.capabilities;

import java.util.function.Supplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A machine's energy, counted as a {@code long} so large buffers do not overflow. Automation sees
 * it through {@link IEnergyStorage}, whose {@code int} values are clamped, and is limited to the
 * transfer rates. The owning block entity uses {@link #insert} and {@link #extract}, which ignore
 * the rates.
 */
public class EnergyBuffer implements IEnergyStorage, INBTSerializable<Tag> {
  private long stored;
  private long capacity;
  private long maxInsert;
  private long maxExtract;
  private Runnable onChange = () -> {};

  /**
   * An empty buffer. Negative values are treated as zero.
   *
   * @param capacity the most energy it holds
   * @param maxInsert the most automation may insert per call
   * @param maxExtract the most automation may extract per call
   */
  public EnergyBuffer(long capacity, long maxInsert, long maxExtract) {
    this.capacity = Math.max(0, capacity);
    this.maxInsert = Math.max(0, maxInsert);
    this.maxExtract = Math.max(0, maxExtract);
  }

  /** An empty buffer with the same insert and extract rate. */
  public EnergyBuffer(long capacity, long maxTransfer) {
    this(capacity, maxTransfer, maxTransfer);
  }

  /** Runs after every change, typically the block entity's {@code setChanged}. */
  public EnergyBuffer onChange(Runnable onChange) {
    this.onChange = onChange;
    return this;
  }

  /** The energy stored, exact. */
  public long stored() {
    return stored;
  }

  /** The most energy the buffer holds. */
  public long capacity() {
    return capacity;
  }

  /** How much more energy fits. */
  public long space() {
    return capacity - stored;
  }

  /** Sets the stored energy, clamped to the capacity. */
  public void setStored(long energy) {
    long clamped = Math.clamp(energy, 0, capacity);
    if (clamped != stored) {
      stored = clamped;
      onChange.run();
    }
  }

  /** Sets the capacity, e.g. after an upgrade; stored energy above it is lost. */
  public void setCapacity(long capacity) {
    this.capacity = Math.max(0, capacity);
    setStored(stored);
  }

  /**
   * Sets the per-call limits for automation; the owner's {@link #insert} and {@link #extract}
   * ignore them.
   */
  public void setRates(long maxInsert, long maxExtract) {
    this.maxInsert = Math.max(0, maxInsert);
    this.maxExtract = Math.max(0, maxExtract);
  }

  /** Adds up to {@code amount}, ignoring the transfer rate. Returns the amount added. */
  public long insert(long amount, boolean simulate) {
    long accepted = Math.clamp(amount, 0, space());
    if (!simulate && accepted > 0) setStored(stored + accepted);
    return accepted;
  }

  /** Removes up to {@code amount}, ignoring the transfer rate. Returns the amount removed. */
  public long extract(long amount, boolean simulate) {
    long removed = Math.clamp(amount, 0, stored);
    if (!simulate && removed > 0) setStored(stored - removed);
    return removed;
  }

  /** Removes exactly {@code amount} if that much is stored, e.g. to run one tick of work. */
  public boolean consume(long amount) {
    if (amount < 0 || amount > stored) return false;
    setStored(stored - amount);
    return true;
  }

  @Override
  public int receiveEnergy(int toReceive, boolean simulate) {
    return (int) insert(Math.min(toReceive, maxInsert), simulate);
  }

  @Override
  public int extractEnergy(int toExtract, boolean simulate) {
    return (int) extract(Math.min(toExtract, maxExtract), simulate);
  }

  @Override
  public int getEnergyStored() {
    return (int) Math.min(stored, Integer.MAX_VALUE);
  }

  @Override
  public int getMaxEnergyStored() {
    return (int) Math.min(capacity, Integer.MAX_VALUE);
  }

  @Override
  public boolean canReceive() {
    return maxInsert > 0;
  }

  @Override
  public boolean canExtract() {
    return maxExtract > 0;
  }

  /** An automation view with a fixed mode, e.g. {@link IoMode#BOTH} for unsided access. */
  public IEnergyStorage view(IoMode mode) {
    return view(() -> mode);
  }

  /**
   * An automation view whose mode is read on every call, so it follows side-configuration changes
   * and rotation without invalidating capabilities.
   */
  public IEnergyStorage view(Supplier<IoMode> mode) {
    return new IEnergyStorage() {
      @Override
      public int receiveEnergy(int toReceive, boolean simulate) {
        return mode.get().canInsert() ? EnergyBuffer.this.receiveEnergy(toReceive, simulate) : 0;
      }

      @Override
      public int extractEnergy(int toExtract, boolean simulate) {
        return mode.get().canExtract() ? EnergyBuffer.this.extractEnergy(toExtract, simulate) : 0;
      }

      @Override
      public int getEnergyStored() {
        return EnergyBuffer.this.getEnergyStored();
      }

      @Override
      public int getMaxEnergyStored() {
        return EnergyBuffer.this.getMaxEnergyStored();
      }

      @Override
      public boolean canReceive() {
        return mode.get().canInsert() && EnergyBuffer.this.canReceive();
      }

      @Override
      public boolean canExtract() {
        return mode.get().canExtract() && EnergyBuffer.this.canExtract();
      }
    };
  }

  @Override
  public Tag serializeNBT(HolderLookup.Provider provider) {
    return LongTag.valueOf(stored);
  }

  @Override
  public void deserializeNBT(HolderLookup.Provider provider, Tag tag) {
    if (tag instanceof NumericTag number) stored = Math.clamp(number.getAsLong(), 0, capacity);
  }
}
