package zone.moddev.mc.lib;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side (per-world) settings; the server is authoritative for world-changing handlers. */
public final class MMDLibConfig {
    private MMDLibConfig() {}
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.IntValue CRACKHAMMER_CRUSH_COUNT;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("crackhammer");
        CRACKHAMMER_CRUSH_COUNT = builder
                .comment("Items crushed from a dropped stack per right-click, at 1 durability each. Sneaking crushes the whole stack.")
                .defineInRange("crush_count", 8, 1, 99);
        builder.pop();
        SERVER_SPEC = builder.build();
    }
}
