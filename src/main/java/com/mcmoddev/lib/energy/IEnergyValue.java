package com.mcmoddev.lib.energy;

import java.util.Objects;

/**
 * Provides a way to generify energy value for easy conversion between systems.
 * @param <T> The type of the value this energy system works with.
 *            (for example: {@link Integer} for RF and ForgeEnergy, {@link Long} for Tesla)
 */
@SuppressWarnings("rawtypes")
public interface IEnergyValue<T> extends Comparable<IEnergyValue<T>> {
    /**
     * The {@link IEnergySystem} this energy value stores energy values for.
     * @return The energy system that corresponds to this value.
     */
    IEnergySystem<T> getSystem();

    /**
     * Gets the actual value stored.
     * @return The actual value being stored.
     */
    T getValue();

    /**
     * Sets the actual stored value.
     * @param value The new value to be stored.
     */
    void setValue(T value);

    /**
     * Tests if this energy value can be compared to another energy value.
     * @param other The energy value this would be compared to.
     * @return True is this value can be compared with the other one or false otherwise. Usually this means that a successful test to {@link IEnergySystem#isCompatibleWith(IEnergySystem)} was made.
     */
    default boolean isCompatible(final IEnergyValue other) {
        return this.getSystem().isCompatibleWith(other.getSystem());
    }

    /**
     * Adds two compatible energy values together.
     * @param other The energy value that should be added to the current one.
     * @return The result of the addition. Or the current value in case energy values are not compatible.
     * @implNote One should use {@link IEnergyValue#isCompatible(IEnergyValue)} to test if values are compatible first.
     */
    IEnergyValue<T> add(IEnergyValue other);

    /**
     * Subtracts an energy value from the current one.
     * @param other The energy value that should be subtracted from the current one.
     * @return The result of the subtraction. Or the current value in case energy values are not compatible.
     * @implNote One should use {@link IEnergyValue#isCompatible(IEnergyValue)} to test if values are compatible first.
     */
    IEnergyValue<T> subtract(IEnergyValue other);

    @Override
    @SuppressWarnings("unchecked")
    default int compareTo(final IEnergyValue<T> other) {
        final T energy = this.getValue();
        final T compareTo = Objects.requireNonNull(this.getSystem().convertFrom(other)).getValue();
        if ((energy instanceof Comparable) && (compareTo instanceof Comparable)) {
            return Comparable.class.cast(energy).compareTo(compareTo);
        }

        // TODO: throw an error or something
        return 0;
    }

    /**
     * Makes a copy of this energy value.
     * @return The copy.
     */
    IEnergyValue<T> copy();
}
