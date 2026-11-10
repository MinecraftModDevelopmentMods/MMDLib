package zone.moddev.mc.lib.gui.runtime.component;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/** Typed lookup without putting component-specific fields on the menu. */
public record MenuComponentKey<T extends MenuComponent>(ResourceLocation id, Class<T> type) {
    public MenuComponentKey {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
    }
}
