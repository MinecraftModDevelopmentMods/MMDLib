package com.mcmoddev.lib.feature;

/**
 * Used to specify the level of dirtiness a feature has.
 * Usually used to filter out some of the update message sent from server to client.
 */
public enum FeatureDirtyLevel {
    /**
     * The highest priority level. Usually used when a feature gets loaded.
     */
    LOAD(0),

    /**
     * This represents a feature change that should be sent to client at the end of current tick.
     */
    TICK(10),

    /**
     * This represents a feature change that should be sent to client only if a GUI is open.
     */
    GUI(20);

    /**
     * The minimum level that should be used. Basically the highest priority level above {@link FeatureDirtyLevel#LOAD}.
     */
    public final static FeatureDirtyLevel MIN_LEVEL = TICK;

    private final int level;

    FeatureDirtyLevel(final int level) {
        this.level = level;
    }

    /**
     * Gets the numerical value of this level. Usable to compare levels.
     * @return The numerical value of this level.
     */
    public int getLevel() {
        return this.level;
    }

    /**
     * Compares with another level to see if this one includes the other one or not.
     * @param level The level to compare to.
     * @return True means this level is of a higher importance than the other one. False means it is not.
     */
    public boolean isMatchOrHigher(final FeatureDirtyLevel level) {
        return (level.level >= this.level);
    }
}
