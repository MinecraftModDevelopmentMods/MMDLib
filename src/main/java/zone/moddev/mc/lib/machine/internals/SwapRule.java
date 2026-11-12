package zone.moddev.mc.lib.machine.internals;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** When a player may install or remove parts. */
public enum SwapRule implements StringRepresentable {
    /** Any time, even while the machine runs; progress is kept. */
    ALWAYS("always"),
    /** Only while the machine is not running. The default. */
    WHEN_IDLE("when_idle"),
    /** Never: parts are fixed once installed. */
    NEVER("never");

    /** The JSON form: {@code always}, {@code when_idle} or {@code never}. */
    public static final Codec<SwapRule> CODEC = StringRepresentable
        .fromEnum(SwapRule::values);

    private final String name;

    SwapRule(String name) {
        this.name = name;
    }

    /** Whether a swap is allowed in this state. */
    public boolean allows(boolean running) {
        return switch (this) {
            case ALWAYS -> true;
            case WHEN_IDLE -> !running;
            case NEVER -> false;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
