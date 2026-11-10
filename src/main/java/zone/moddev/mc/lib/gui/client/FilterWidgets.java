package zone.moddev.mc.lib.gui.client;

import java.util.*;
import java.util.function.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.gui.layout.Rect;

/**
 * Client-only renderer registration. Factories receive read-only snapshots and
 * a typed submitter, never the installed ItemStack or mutable server storage.
 */
public final class FilterWidgets {
    public record Access<T>(Supplier<Rect> bounds,
        Supplier<CompoundTag> configuration, BooleanSupplier pending,
        Consumer<T> submit) {
    }

    public interface Instance {
        void render(GuiGraphics graphics, Rect bounds,
            CompoundTag configuration, int mouseX, int mouseY);

        default Component tooltip() {
            return null;
        }

        default boolean click(double x, double y, int button) {
            return false;
        }

        default boolean key(int key, int scan, int modifiers) {
            return false;
        }

        default boolean character(char value, int modifiers) {
            return false;
        }

        default boolean drag(double x, double y, int button, double dx,
            double dy) {
            return false;
        }

        default boolean release(double x, double y, int button) {
            return false;
        }

        default boolean scroll(double x, double y, double amount) {
            return false;
        }

        default void focus(boolean focused) {
        }

        default void cancel() {
        }

        default void removed() {
        }
    }

    private interface Factory {
        Instance create(Supplier<Rect> bounds,
            Supplier<CompoundTag> configuration, BooleanSupplier pending,
            Consumer<String> submit);
    }

    private static final Map<ResourceLocation, Factory> FACTORIES = new HashMap<>();

    public static <T> void register(FilterWidgetType<T> type,
        Function<Access<T>, Instance> factory) {
        if (FACTORIES.putIfAbsent(type.id(),
            (bounds, config, pending, submit) -> factory
                .apply(new Access<>(bounds, config, pending,
                    value -> submit.accept(type.encode(value))))) != null)
            throw new IllegalArgumentException(
                "Duplicate filter renderer: " + type.id());
    }

    static Instance create(FilterControl.Custom control, Supplier<Rect> bounds,
        Supplier<CompoundTag> config, BooleanSupplier pending,
        Consumer<String> submit) {
        var factory = FACTORIES.get(control.type().id());
        return factory == null
            ? null
            : factory.create(bounds, config, pending, submit);
    }

    private FilterWidgets() {
    }
}
