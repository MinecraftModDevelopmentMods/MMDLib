package zone.moddev.mc.lib.gui.load;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.model.Screen;

/**
 * One validation pass over a screen; elements own the rules and namespaced
 * uniqueness claims.
 */
public final class ScreenValidationContext {
    private record Claim(ResourceLocation domain, Object key) {
    }

    private final Screen screen;
    private final Set<Claim> claims = new HashSet<>();

    public ScreenValidationContext(Screen screen) {
        this.screen = Objects.requireNonNull(screen);
    }

    public Screen screen() {
        return screen;
    }

    /**
     * Claim a unique binding in this pass. Keys must have stable equality and
     * hash codes.
     */
    public void unique(ResourceLocation domain, Object key,
        String duplicateMessage) {
        if (!claims.add(new Claim(Objects.requireNonNull(domain),
            Objects.requireNonNull(key))))
            throw new IllegalArgumentException(duplicateMessage);
    }
}
