package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import zone.moddev.mc.lib.common.types.SoundDefinition;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * Plays an element's {@code click-sound} for the player, as an interface sound
 * (not placed in the world), the way vanilla buttons click.
 */
final class ClickSounds {
    private ClickSounds() {
    }

    /** Plays {@code element}'s click sound, if it has one. */
    static void play(Element element) {
        element.common().clicks().sound()
            .ifPresent(id -> Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundDefinition.event(id), 1)));
    }
}
