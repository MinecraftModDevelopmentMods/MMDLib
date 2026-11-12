package zone.moddev.mc.lib.gui.client;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

/**
 * Client renderer registry. Register built-in or mod-owned element classes
 * during client setup.
 */
public final class ElementRenderers {
    public static final ElementRenderers GLOBAL = builtins();

    public enum Pass {
        BACKGROUND, OVERLAY, FOREGROUND, TOP
    }

    public enum Clip {
        ELEMENT, VIEWPORT
    }

    private record Entry(Consumer<Context> renderer,
        Consumer<Element> validator, Clip clip) {
    }

    private final Map<Pass, Map<Class<? extends Element>, Entry>> entries = new EnumMap<>(
        Pass.class);
    private final Map<Pass, Map<Class<? extends Element>, Optional<Entry>>> resolved = new EnumMap<>(
        Pass.class);

    public ElementRenderers() {
        for (var pass : Pass.values()) {
            entries.put(pass, new HashMap<>());
            resolved.put(pass, new HashMap<>());
        }
    }

    /**
     * Fresh registry containing the defaults; useful for isolated extensions
     * and tests.
     */
    public static ElementRenderers builtins() {
        var registry = new ElementRenderers();
        BuiltinElementClients.registerRenderers(registry);
        return registry;
    }

    @FunctionalInterface
    public interface Renderer<E extends Element> {
        void render(E element, Context context);

        /**
         * Checks client dependencies before a screen opens; throw with a
         * descriptive message.
         */
        default void validate(E element) {
        }
    }

    /**
     * Immutable frame inputs; values are synchronized snapshots and coordinates
     * are GUI pixels.
     */
    public record Context(GuiGraphics graphics, Element element, Rect bounds,
        Object value, Object maximum, int mouseX, int mouseY, float partialTick,
        double elapsedTicks, ElementRenderAccess access) {
        public Context(GuiGraphics graphics, Element element, Rect bounds,
            Object value, Object maximum, int mouseX, int mouseY,
            float partialTick) {
            this(graphics, element, bounds, value, maximum, mouseX, mouseY,
                partialTick, partialTick, null);
        }

        public Context(GuiGraphics graphics, Element element, Rect bounds,
            Object value, Object maximum, int mouseX, int mouseY,
            float partialTick, double elapsedTicks) {
            this(graphics, element, bounds, value, maximum, mouseX, mouseY,
                partialTick, elapsedTicks, null);
        }

        public boolean hovered() {
            return bounds.contains(mouseX, mouseY);
        }

        public double ratio() {
            return element.fillRatio(value, maximum);
        }

        public ElementRenderAccess access() {
            return Objects.requireNonNull(access,
                "This render context has no presentation services");
        }
    }

    public <E extends Element> void register(Class<E> type,
        Renderer<E> renderer) {
        register(Pass.BACKGROUND, type, Clip.ELEMENT, renderer);
    }

    public <E extends Element> void registerOverlay(Class<E> type,
        Renderer<E> renderer) {
        register(Pass.OVERLAY, type, Clip.ELEMENT, renderer);
    }

    public <E extends Element> void registerForeground(Class<E> type,
        Renderer<E> renderer) {
        register(Pass.FOREGROUND, type, Clip.VIEWPORT, renderer);
    }

    public <E extends Element> void registerTop(Class<E> type,
        Renderer<E> renderer) {
        register(Pass.TOP, type, Clip.ELEMENT, renderer);
    }

    /**
     * Exact type wins; otherwise the most specific registered
     * superclass/interface wins.
     */
    public synchronized <E extends Element> void register(Pass pass,
        Class<E> type, Clip clip, Renderer<E> renderer) {
        var entry = entry(type, clip, renderer);
        if (entries.get(pass).putIfAbsent(type, entry) != null)
            throw new IllegalArgumentException("Duplicate element renderer for "
                + pass + ": " + type.getName());
        resolved.get(pass).clear();
    }

    /**
     * Intentional replacement of an existing registration; ordinary register
     * rejects duplicates.
     */
    public <E extends Element> void replace(Class<E> type,
        Renderer<E> renderer) {
        replace(Pass.BACKGROUND, type, Clip.ELEMENT, renderer);
    }

    public synchronized <E extends Element> void replace(Pass pass,
        Class<E> type, Clip clip, Renderer<E> renderer) {
        var entry = entry(type, clip, renderer);
        if (!entries.get(pass).containsKey(type))
            throw new IllegalArgumentException(
                "No renderer registered for " + type.getName());
        entries.get(pass).put(type, entry);
        resolved.get(pass).clear();
    }

    private static <E extends Element> Entry entry(Class<E> type, Clip clip,
        Renderer<E> renderer) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(clip);
        Objects.requireNonNull(renderer);
        return new Entry(c -> renderer.render(type.cast(c.element()), c),
            e -> renderer.validate(type.cast(e)), clip);
    }

    private synchronized Optional<Entry> resolve(Pass pass,
        Class<? extends Element> type) {
        var cache = resolved.get(pass);
        if (cache.containsKey(type))
            return cache.get(type);
        var registrations = entries.get(pass);
        var exact = registrations.get(type);
        if (exact != null) {
            var result = Optional.of(exact);
            cache.put(type, result);
            return result;
        }
        var candidates = registrations.keySet().stream()
            .filter(key -> key.isAssignableFrom(type)).toList();
        var specific = candidates.stream().filter(key -> candidates.stream()
            .noneMatch(other -> key != other && key.isAssignableFrom(other)))
            .toList();
        if (specific.size() > 1)
            throw new IllegalArgumentException(
                "Ambiguous element renderers for " + pass + " " + type.getName()
                    + ": "
                    + specific.stream().map(Class::getName).sorted().toList()
                    + "; register its exact class");
        var result = specific.isEmpty()
            ? Optional.<Entry>empty()
            : Optional.of(registrations.get(specific.getFirst()));
        cache.put(type, result);
        return result;
    }

    public Consumer<Context> require(Class<? extends Element> type) {
        return require(Pass.BACKGROUND, type);
    }

    public Consumer<Context> require(Pass pass, Class<? extends Element> type) {
        return resolve(pass, type)
            .orElseThrow(() -> new IllegalArgumentException(
                "Missing element renderer for " + pass + ": " + type.getName()))
            .renderer();
    }

    /**
     * Fail at screen creation rather than halfway through a frame. Optional
     * passes may be absent.
     */
    public void validate(Screen screen) {
        validate(screen.elements(), element -> screen.source(element.common()));
    }

    /**
     * Validates a host's complete traversal, with a source name for each
     * element's diagnostics.
     */
    public void validate(Iterable<? extends Element> elements,
        Function<Element, String> source) {
        for (var element : elements) {
            try {
                require(element.getClass());
                for (var pass : Pass.values())
                    resolve(pass, element.getClass())
                        .ifPresent(entry -> entry.validator().accept(element));
            } catch (IllegalArgumentException failure) {
                throw new ScreenException(source.apply(element),
                    element.common().line(), failure.getMessage(), failure);
            }
        }
    }

    public void render(Context context) {
        render(Pass.BACKGROUND, context);
    }

    public void renderOverlay(Context context) {
        render(Pass.OVERLAY, context);
    }

    public void render(Pass pass, Context context) {
        var entry = resolve(pass, context.element().getClass());
        if (entry.isEmpty()) {
            if (pass == Pass.BACKGROUND)
                require(context.element().getClass());
            return;
        }
        var graphics = context.graphics();
        var clip = entry.get().clip() == Clip.ELEMENT
            ? context.bounds()
            : context.access().viewport();
        graphics.pose().pushPose();
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            entry.get().renderer().accept(context);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            graphics.disableScissor();
            graphics.pose().popPose();
        }
    }

    @EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void complete(FMLLoadCompleteEvent event) {
            event.enqueueWork(() -> Screens.all().values()
                .forEach(bound -> GLOBAL.validate(bound.screen())));
        }
    }
}
