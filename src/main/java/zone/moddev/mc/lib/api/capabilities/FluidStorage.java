package zone.moddev.mc.lib.api.capabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A machine's tanks as one {@link IFluidHandler}, each tank with a {@link Role}, capacity and
 * filter, and a change callback. The owning block entity works on {@link #tank} directly, which
 * ignores roles; players' buckets and automation use the handler or a {@link #view}.
 *
 * <p>Filling picks the first tank that already holds the fluid, then the first empty tank that
 * accepts it. Draining picks the first tank holding the requested fluid, or for an amount, the
 * first tank that is not empty.
 */
public class FluidStorage implements IFluidHandler, INBTSerializable<CompoundTag> {
  private final List<FluidTank> tanks = new ArrayList<>();
  private final List<Role> roles = new ArrayList<>();
  private Runnable onChange = () -> {};

  /** A storage with no tanks; add them with {@link #addTank}. */
  public FluidStorage() {}

  /** Runs after every change, typically the block entity's {@code setChanged}. */
  public FluidStorage onChange(Runnable onChange) {
    this.onChange = onChange;
    return this;
  }

  /** Adds a tank and returns its index. */
  public int addTank(int capacity, Role role, Predicate<FluidStack> filter) {
    tanks.add(
        new FluidTank(capacity, filter) {
          @Override
          protected void onContentsChanged() {
            onChange.run();
          }
        });
    roles.add(role);
    return tanks.size() - 1;
  }

  /** Adds a tank that accepts any fluid and returns its index. */
  public int addTank(int capacity, Role role) {
    return addTank(capacity, role, fluid -> true);
  }

  /** The tank itself, for the machine's own work; it ignores roles but keeps its filter. */
  public FluidTank tank(int index) {
    return tanks.get(index);
  }

  /** The role of tank {@code tank}. */
  public Role role(int tank) {
    return roles.get(tank);
  }

  @Override
  public int getTanks() {
    return tanks.size();
  }

  @Override
  public FluidStack getFluidInTank(int tank) {
    return inRange(tank) ? tanks.get(tank).getFluid() : FluidStack.EMPTY;
  }

  @Override
  public int getTankCapacity(int tank) {
    return inRange(tank) ? tanks.get(tank).getCapacity() : 0;
  }

  @Override
  public boolean isFluidValid(int tank, FluidStack stack) {
    return inRange(tank) && roles.get(tank) != Role.OUTPUT && tanks.get(tank).isFluidValid(stack);
  }

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    return fill(resource, action, role -> role != Role.OUTPUT);
  }

  @Override
  public FluidStack drain(FluidStack resource, FluidAction action) {
    return drain(resource, action, role -> true);
  }

  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    return drain(maxDrain, action, role -> true);
  }

  /** An automation view with a fixed mode, e.g. {@link IoMode#BOTH} for unsided access. */
  public IFluidHandler view(IoMode mode) {
    return view(() -> mode);
  }

  /**
   * An automation view whose mode is read on every call, so it follows side-configuration changes
   * and rotation without invalidating capabilities. Every view shows all tanks with the same
   * numbering.
   */
  public IFluidHandler view(Supplier<IoMode> mode) {
    return new IFluidHandler() {
      @Override
      public int getTanks() {
        return FluidStorage.this.getTanks();
      }

      @Override
      public FluidStack getFluidInTank(int tank) {
        return FluidStorage.this.getFluidInTank(tank);
      }

      @Override
      public int getTankCapacity(int tank) {
        return FluidStorage.this.getTankCapacity(tank);
      }

      @Override
      public boolean isFluidValid(int tank, FluidStack stack) {
        return mode.get().canInsert()
            && inRange(tank)
            && roles.get(tank).canInsert()
            && FluidStorage.this.isFluidValid(tank, stack);
      }

      @Override
      public int fill(FluidStack resource, FluidAction action) {
        return mode.get().canInsert()
            ? FluidStorage.this.fill(resource, action, Role::canInsert)
            : 0;
      }

      @Override
      public FluidStack drain(FluidStack resource, FluidAction action) {
        return mode.get().canExtract()
            ? FluidStorage.this.drain(resource, action, Role::canExtract)
            : FluidStack.EMPTY;
      }

      @Override
      public FluidStack drain(int maxDrain, FluidAction action) {
        return mode.get().canExtract()
            ? FluidStorage.this.drain(maxDrain, action, Role::canExtract)
            : FluidStack.EMPTY;
      }
    };
  }

  private int fill(FluidStack resource, FluidAction action, Predicate<Role> allowed) {
    if (resource.isEmpty()) return 0;
    int target = -1;
    for (int i = 0; i < tanks.size() && target < 0; i++)
      if (allowed.test(roles.get(i))
          && FluidStack.isSameFluidSameComponents(tanks.get(i).getFluid(), resource)
          && tanks.get(i).getSpace() > 0) target = i;
    for (int i = 0; i < tanks.size() && target < 0; i++)
      if (allowed.test(roles.get(i))
          && tanks.get(i).isEmpty()
          && tanks.get(i).isFluidValid(resource)) target = i;
    return target < 0 ? 0 : tanks.get(target).fill(resource, action);
  }

  private FluidStack drain(FluidStack resource, FluidAction action, Predicate<Role> allowed) {
    if (resource.isEmpty()) return FluidStack.EMPTY;
    for (int i = 0; i < tanks.size(); i++)
      if (allowed.test(roles.get(i))
          && FluidStack.isSameFluidSameComponents(tanks.get(i).getFluid(), resource))
        return tanks.get(i).drain(resource, action);
    return FluidStack.EMPTY;
  }

  private FluidStack drain(int maxDrain, FluidAction action, Predicate<Role> allowed) {
    for (int i = 0; i < tanks.size(); i++)
      if (allowed.test(roles.get(i)) && !tanks.get(i).isEmpty())
        return tanks.get(i).drain(maxDrain, action);
    return FluidStack.EMPTY;
  }

  private boolean inRange(int tank) {
    return tank >= 0 && tank < tanks.size();
  }

  @Override
  public CompoundTag serializeNBT(HolderLookup.Provider provider) {
    ListTag list = new ListTag();
    for (FluidTank tank : tanks) list.add(tank.writeToNBT(provider, new CompoundTag()));
    CompoundTag tag = new CompoundTag();
    tag.put("Tanks", list);
    return tag;
  }

  /** Loads tank contents by index; tanks missing from the save are emptied, extra ones ignored. */
  @Override
  public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
    ListTag list = tag.getList("Tanks", Tag.TAG_COMPOUND);
    for (int i = 0; i < tanks.size(); i++) {
      if (i < list.size()) tanks.get(i).readFromNBT(provider, list.getCompound(i));
      else tanks.get(i).setFluid(FluidStack.EMPTY);
    }
  }
}
