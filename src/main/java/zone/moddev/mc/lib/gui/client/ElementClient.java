package zone.moddev.mc.lib.gui.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import zone.moddev.mc.lib.gui.ElementType;
import zone.moddev.mc.lib.gui.model.Element;

/** Client-only registration bundle. Delegates to the existing renderer and behavior registries. */
public final class ElementClient<E extends Element> {
    private record Drawing<E extends Element>(
        ElementRenderers.Clip clip, ElementRenderers.Renderer<E> renderer) {
    }

    private final ElementType<E> element;
    private final Map<ElementRenderers.Pass, Drawing<E>> drawings = new EnumMap<>(
        ElementRenderers.Pass.class);
    private ElementBehaviors.Factory<E> behavior;

    private ElementClient(ElementType<E> element) {
        this.element = Objects.requireNonNull(element);
    }

    public static <E extends Element> ElementClient<E> of(ElementType<E> element) {
        return new ElementClient<>(element);
    }

    public ElementClient<E> renderer(ElementRenderers.Renderer<E> renderer) {
        return renderer(ElementRenderers.Pass.BACKGROUND, ElementRenderers.Clip.ELEMENT, renderer);
    }

    public ElementClient<E> overlay(ElementRenderers.Renderer<E> renderer) {
        return renderer(ElementRenderers.Pass.OVERLAY, ElementRenderers.Clip.ELEMENT, renderer);
    }

    public ElementClient<E> foreground(ElementRenderers.Renderer<E> renderer) {
        return renderer(ElementRenderers.Pass.FOREGROUND, ElementRenderers.Clip.VIEWPORT, renderer);
    }

    public ElementClient<E> top(ElementRenderers.Renderer<E> renderer) {
        return renderer(ElementRenderers.Pass.TOP, ElementRenderers.Clip.VIEWPORT, renderer);
    }

    public ElementClient<E> renderer(
        ElementRenderers.Pass pass,
        ElementRenderers.Clip clip,
        ElementRenderers.Renderer<E> renderer) {
        Objects.requireNonNull(pass);
        var drawing = new Drawing<>(Objects.requireNonNull(clip), Objects.requireNonNull(renderer));
        if (drawings.putIfAbsent(pass, drawing) != null)
            throw new IllegalArgumentException(
                "Duplicate " + pass + " renderer for " + element.type().getName());
        return this;
    }

    public ElementClient<E> behavior(ElementBehaviors.Factory<E> factory) {
        Objects.requireNonNull(factory);
        if (behavior != null)
            throw new IllegalArgumentException(
                "Duplicate behavior for " + element.type().getName());
        behavior = factory;
        return this;
    }

    /**
     * Register during client setup. Omitted passes/behavior retain the normal inheritance rules.
     */
    public void register() {
        register(ElementRenderers.GLOBAL, ElementBehaviors.GLOBAL);
    }

    public void register(ElementRenderers renderers, ElementBehaviors behaviors) {
        registerRenderers(renderers);
        registerBehaviors(behaviors);
    }

    /** Separate registry bootstrap and isolated tests need not initialize the other registry. */
    public void registerRenderers(ElementRenderers registry) {
        Objects.requireNonNull(registry);
        drawings.forEach(
            (pass, drawing) -> registry.register(pass, element.type(), drawing.clip(),
                drawing.renderer()));
    }

    public void registerBehaviors(ElementBehaviors registry) {
        Objects.requireNonNull(registry);
        if (behavior != null)
            registry.register(element.type(), behavior);
    }
}
