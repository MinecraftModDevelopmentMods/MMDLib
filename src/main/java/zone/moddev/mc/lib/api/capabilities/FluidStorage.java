package zone.moddev.mc.lib.api.capabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
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

import zone.moddev.mc.lib.api.stats.StatsProvider;
import zone.moddev.mc.lib.machine.inventory.ReservationPlans;
import zone.moddev.mc.lib.machine.inventory.TankPlan;
import zone.moddev.mc.lib.machine.neoforge.FluidStacks;

/**
 * A machine's tanks as one {@link IFluidHandler}, each tank with a {@link Role}, capacity and
 * filter, and a change callback. The owning block entity works on {@link #tank} directly, which
 * ignores roles; players' buckets and automation use the handler or a {@link #view}.
 *
 * <p>Filling picks the first tank that already holds the fluid, then the first empty tank that
 * accepts it. Draining picks the first tank holding the requested fluid, or for an amount, the
 * first tank that is not empty.
 */
public class FluidStorage implements IFluidHandler, INBTSerializable<CompoundTag>, StatsProvider {
  @Override
  public zone.moddev.mc.lib.api.stats.Statistics statistics() {
    return zone.moddev.mc.lib.api.stats.Statistics.EMPTY;
  }

  private final List<FluidTank> tanks = new ArrayList<>();
  private final List<Role> roles = new ArrayList<>();
  private final ReservationPlans<TankPlan> plans =
      new ReservationPlans<>(
          0,
          TankPlan.NONE,
          TankPlan::plus,
          TankPlan::minus,
          (tank, plan) ->
              plan.legal(
                  FluidStacks.amount(tanks.get(tank).getFluid()), tanks.get(tank).getCapacity()));
  private Runnable onChange = () -> {};
  private long revision;

  /** Change counter for recipe search invalidation. */
  public long revision() {
    return revision + plans.revision();
  }

  /** A storage with no tanks; add them with {@link #addTank}. */
  public FluidStorage() {}

  /** Runs after every change, typically the block entity's {@code setChanged}. */
  public FluidStorage onChange(Runnable onChange) {
    this.onChange = onChange;
    return this;
  }

  /** Adds a tank and returns its index. */
  public int addTank(int capacity, Role role, Predicate<FluidStack> filter) {
    int index = tanks.size();
    tanks.add(
        new FluidTank(capacity, filter) {
          @Override
          public void setFluid(FluidStack stack) {
            super.setFluid(stack);
            onContentsChanged();
          }

          @Override
          public FluidTank setCapacity(int capacity) {
            revision++;
            return super.setCapacity(capacity);
          }

          @Override
          public FluidTank setValidator(Predicate<FluidStack> validator) {
            revision++;
            return super.setValidator(validator);
          }

          @Override
          protected void onContentsChanged() {
            revision++;
            onChange.run();
          }

          @Override
          public int fill(FluidStack resource, FluidAction action) {
            var plan = plans.get(index);
            if (plan.isNone()) return super.fill(resource, action);
            int room =
                plan.room(
                    FluidStacks.amount(getFluid()), FluidStacks.amount(resource), getCapacity());
            if (room <= 0) return 0;
            int amount = Math.min(resource.getAmount(), room);
            return super.fill(resource.copyWithAmount(amount), action);
          }

          @Override
          public FluidStack drain(int maxDrain, FluidAction action) {
            var plan = plans.get(index);
            if (plan.isNone()) return super.drain(maxDrain, action);
            int free = plan.free(FluidStacks.amount(getFluid()));
            return free <= 0 ? FluidStack.EMPTY : super.drain(Math.min(maxDrain, free), action);
          }
        });
    roles.add(role);
    plans.add();
    revision++;
    return tanks.size() - 1;
  }

  /** Adds a tank that accepts any fluid and returns its index. */
  public int addTank(int capacity, Role role) {
    return addTank(capacity, role, fluid -> true);
  }

  /**
   * The tank itself, for the machine's own work; it ignores roles but keeps its filter and obeys
   * its plan.
   */
  public FluidTank tank(int index) {
    return tanks.get(index);
  }

  /** Shared plan table, including transaction tracking for groups over this storage. */
  public ReservationPlans<TankPlan> reservationPlans() {
    return plans;
  }

  /** What the machine plans for tank {@code tank}. */
  public TankPlan plan(int tank) {
    return plans.get(tank);
  }

  /** Replaces the plan with no checks, for undoing and loading; use {@link #reserve} otherwise. */
  public void setPlan(int tank, TankPlan plan) {
    plans.set(tank, plan);
  }

  /**
   * Adds {@code plan} to what is planned for the tank. Returns false and changes nothing if the
   * plans cannot be merged or the result is not legal for what the tank holds now.
   */
  public boolean reserve(int tank, TankPlan plan) {
    return plans.reserve(tank, plan);
  }

  /** Takes {@code plan}, which must have been reserved, out of the tank's plan. */
  public void release(int tank, TankPlan plan) {
    plans.release(tank, plan);
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
    return fill(resource, action, tank -> roles.get(tank) != Role.OUTPUT);
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
    return view(tank -> mode.get());
  }

  /** Per-tank permissions; filling/draining searches only eligible tanks without renumbering. */
  public IFluidHandler view(IntFunction<IoMode> mode) {
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
        return inRange(tank)
            && mode.apply(tank).canInsert()
            && roles.get(tank).canInsert()
            && FluidStorage.this.isFluidValid(tank, stack);
      }

      @Override
      public int fill(FluidStack resource, FluidAction action) {
        return FluidStorage.this.fill(
            resource, action, tank -> roles.get(tank).canInsert() && mode.apply(tank).canInsert());
      }

      @Override
      public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStorage.this.drain(
            resource,
            action,
            tank -> roles.get(tank).canExtract() && mode.apply(tank).canExtract());
      }

      @Override
      public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStorage.this.drain(
            maxDrain,
            action,
            tank -> roles.get(tank).canExtract() && mode.apply(tank).canExtract());
      }
    };
  }

  private int fill(FluidStack resource, FluidAction action, IntPredicate allowed) {
    if (resource.isEmpty()) return 0;
    int target = -1;
    for (int i = 0; i < tanks.size() && target < 0; i++)
      if (allowed.test(i)
          && FluidStack.isSameFluidSameComponents(tanks.get(i).getFluid(), resource)
          && tanks.get(i).fill(resource, FluidAction.SIMULATE) > 0) target = i;
    for (int i = 0; i < tanks.size() && target < 0; i++)
      if (allowed.test(i)
          && tanks.get(i).isEmpty()
          && tanks.get(i).fill(resource, FluidAction.SIMULATE) > 0) target = i;
    return target < 0 ? 0 : tanks.get(target).fill(resource, action);
  }

  private FluidStack drain(FluidStack resource, FluidAction action, IntPredicate allowed) {
    if (resource.isEmpty()) return FluidStack.EMPTY;
    for (int i = 0; i < tanks.size(); i++)
      if (allowed.test(i)
          && FluidStack.isSameFluidSameComponents(tanks.get(i).getFluid(), resource)
          && !tanks.get(i).drain(resource, FluidAction.SIMULATE).isEmpty())
        return tanks.get(i).drain(resource, action);
    return FluidStack.EMPTY;
  }

  private FluidStack drain(int maxDrain, FluidAction action, IntPredicate allowed) {
    for (int i = 0; i < tanks.size(); i++)
      if (allowed.test(i) && !tanks.get(i).drain(maxDrain, FluidAction.SIMULATE).isEmpty())
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
