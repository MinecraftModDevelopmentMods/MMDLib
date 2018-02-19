package com.mcmoddev.lib.feature;

public enum FeatureDirtyLevel {
    LOAD(0),
    TICK(10),
    GUI(20);

    public final static FeatureDirtyLevel MIN_LEVEL = TICK;

    private final int level;

    FeatureDirtyLevel(final int level) {
        this.level = level;
    }

    public int getLevel() {
        return this.level;
    }

    public boolean isMatchOrHigher(final FeatureDirtyLevel level) {
        return (level.level >= this.level);
    }
}
