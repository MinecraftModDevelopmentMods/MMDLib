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
    private static final Map<DefinedMachineEntity, Map<String, Loop>> LOOPS = new WeakHashMap<>();

    private MachineSoundsClient() {
    }

    /** Starts {@code machine}'s loop if a cycle runs and it is not playing. */
    public static void tick(DefinedMachineEntity machine) {
        var loops = LOOPS.computeIfAbsent(machine,
            ignored -> new java.util.HashMap<>());
        for (var config : machine.definition.processors()) {
            var id = config.sounds().running().orElse(null);
            if (id == null || !running(machine, config.id()))
                continue;
            var loop = loops.get(config.id());
            if (loop != null && !loop.isStopped())
                continue;
            loop = new Loop(machine, config.id(), id);
            loops.put(config.id(), loop);
            Minecraft.getInstance().getSoundManager().play(loop);
        }
    }

    private static boolean running(DefinedMachineEntity machine, String key) {
        return machine.features.findProcessor(key)
            .map(zone.moddev.mc.lib.machine.feature.Processor::processing)
            .orElse(false);
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final DefinedMachineEntity machine;
        private final String processor;

        Loop(DefinedMachineEntity machine, String processor,
            net.minecraft.resources.ResourceLocation id) {
            super(SoundDefinition.event(id), SoundSource.BLOCKS,
                RandomSource.create());
            this.machine = machine;
            this.processor = processor;
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
            if (machine.isRemoved() || !running(machine, processor))
                stop();
        }
    }
}
