package zone.moddev.mc.lib.smoke;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.LiquidBlock;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;

/**
 * Sound events declared in test resources' {@code mmdlib/sound/}: {@code
 * test/chime} with a fixed 24-block range, {@code test/buzz} with the default,
 * which the test script {@code mmdlib_sounds.zs} changes to a fixed 32; that
 * script's own {@code test/ping}, with the default. The block sound set
 * {@code mmdlib:test/chiming}, which the test quartz material's blocks use and
 * the script raises to pitch 1.2, and the script's own {@code test/thud}.
 */
final class SoundSmoke {
    private SoundSmoke() {
    }

    static void registered() {
        var chime = sound("mmdlib:test/chime");
        var buzz = sound("mmdlib:test/buzz");
        // A fixed range ignores the volume; the default grows with it, 16
        // blocks per unit of volume above 1.
        require(chime.getRange(1) == 24 && chime.getRange(4) == 24,
            "chime keeps its fixed 24-block range");
        require(buzz.getRange(1) == 32 && buzz.getRange(4) == 32,
            "the script's edit gave buzz a fixed 32-block range");
        var ping = sound("mmdlib:test/ping");
        require(ping.getRange(1) == 16 && ping.getRange(4) == 64,
            "the script's ping uses the volume-based range");
        require(MMDLib.sounds().size() == 3, "all three are listed");
    }

    /**
     * A quartz block plays the test chime when placed, glass's other sounds, at
     * the set's own pitch.
     */
    static void blockSoundSet() {
        var quartz = ResourceLocation.parse("mmdlib:quartz");
        var block = RuntimeContentRegistrar.instances().stream()
            .flatMap(registrar -> registrar.blockMaterials().entrySet().stream()
                .filter(entry -> entry.getValue().name().orElseThrow()
                    .equals(quartz))
                .map(entry -> registrar.blocks().get(entry.getKey()).get()))
            .filter(candidate -> !(candidate instanceof LiquidBlock))
            .findFirst().orElseThrow(() -> new AssertionError(
                "Sound smoke failed: no quartz block"));
        var type = block.defaultBlockState().getSoundType();
        require(type.getPlaceSound() == sound("mmdlib:test/chime"),
            "placing plays the set's chime");
        require(
            type.getBreakSound() == SoundEvents.GLASS_BREAK
                && type.getStepSound() == SoundEvents.GLASS_STEP,
            "other sounds come from the glass base");
        require(type.getPitch() == 1.2f && type.getVolume() == 1,
            "the set's pitch, the base's volume");
    }

    /**
     * The script's {@code test/thud}: wood, but breaking plays the ping, at
     * half volume; and the script material that names it as its block sound.
     */
    static void scriptSoundSet() {
        var thud = zone.moddev.mc.lib.common.runtime.SoundTypes
            .resolve("mmdlib:test/thud");
        require(thud.getBreakSound() == sound("mmdlib:test/ping"),
            "breaking plays the ping");
        require(thud.getStepSound() == SoundEvents.WOOD_STEP
            && thud.getPitch() == 1, "the rest comes from the wood base");
        require(thud.getVolume() == .5f, "the set's half volume");
        var material = MMDLib.loadedMaterials()
            .get(ResourceLocation.parse("mmdlib:sound_material"));
        require(
            material != null && material.material().runtime().blockSound()
                .equals(java.util.Optional.of("mmdlib:test/thud")),
            "a script material can name a sound set as its blockSound");
    }

    private static SoundEvent sound(String id) {
        var location = ResourceLocation.parse(id);
        var sound = BuiltInRegistries.SOUND_EVENT.get(location);
        require(sound != null && sound.getLocation().equals(location),
            id + " is registered");
        return sound;
    }

    private static void require(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("Sound smoke failed: " + what);
    }
}
