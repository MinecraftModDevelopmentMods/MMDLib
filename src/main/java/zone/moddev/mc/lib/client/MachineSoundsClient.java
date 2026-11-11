package zone.moddev.mc.lib.client;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import zone.moddev.mc.lib.common.types.SoundDefinition;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * The {@code running-sound} of XML machines: one looping sound per machine,
 * started when a cycle runs and stopped when it no longer does or the machine
 * is gone.
 */
public final class MachineSoundsClient {
    private static final Map<DefinedMachineEntity, Loop> LOOPS = new WeakHashMap<>();

    private MachineSoundsClient() {
    }

    /** Starts {@code machine}'s loop if a cycle runs and it is not playing. */
    public static void tick(DefinedMachineEntity machine) {
        var id = machine.definition.sounds().running().orElse(null);
        if (id == null || !machine.running())
            return;
        var loop = LOOPS.get(machine);
        if (loop != null && !loop.isStopped())
            return;
        loop = new Loop(machine, id);
        LOOPS.put(machine, loop);
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final DefinedMachineEntity machine;

        Loop(DefinedMachineEntity machine,
            net.minecraft.resources.ResourceLocation id) {
            super(SoundDefinition.event(id), SoundSource.BLOCKS,
                RandomSource.create());
            this.machine = machine;
            var pos = machine.getBlockPos();
            x = pos.getX() + .5;
            y = pos.getY() + .5;
            z = pos.getZ() + .5;
            looping = true;
            delay = 0;
            attenuation = SoundInstance.Attenuation.LINEAR;
        }

        @Override
        public void tick() {
            if (machine.isRemoved() || !machine.running())
                stop();
        }
    }
}
