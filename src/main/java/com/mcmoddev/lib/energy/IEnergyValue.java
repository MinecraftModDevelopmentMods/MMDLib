package com.mcmoddev.lib.energy;

import java.util.Objects;

public interface IEnergyValue<T> extends Comparable<IEnergyValue<T>> {
    IEnergySystem<T> getSystem();

    T getValue();
    void setValue(T value);

    default boolean isCompatible(final IEnergyValue other) {
        return this.getSystem().isCompatibleWith(other.getSystem());
    }

    IEnergyValue<T> add(IEnergyValue other);
    IEnergyValue<T> substract(IEnergyValue other);

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

    IEnergyValue<T> copy();
}
