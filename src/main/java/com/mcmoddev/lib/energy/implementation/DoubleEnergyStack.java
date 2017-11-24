package com.mcmoddev.lib.energy.implementation;

public class DoubleEnergyStack extends BaseEnergyStack<Double> {
    public DoubleEnergyStack() {
        this(0.0);
    }

    public DoubleEnergyStack(Double initial) {
        super(initial);
    }
}
