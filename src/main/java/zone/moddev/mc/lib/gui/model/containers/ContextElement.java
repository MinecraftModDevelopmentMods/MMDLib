package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.LayoutContext;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementStates;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;

/**
 * {@code context}: an area whose content buttons swap in place. The first view
 * is the default.
 *
 * <p>
 * {@code views}: the alternative contents
 */
public class ContextElement
    implements SynchronizedElement, SelectiveContainer, NavigableElement {
    private final Common common;
    private final List<ViewElement> views;

    public static final ElementSync<Integer> SELECTED = ElementSync
        .field(ResourceLocation.parse("mmdlib:context_view"),
            StreamCodec.of((buffer, value) -> buffer.writeVarInt(value),
                buffer -> buffer.readVarInt()),
            5, element -> 0)
        .validate((element, value) -> value >= 0
            && value < ((ContextElement) element).views().size())
        .affectsLayout().build();

    public List<ElementSync<?>> synchronizedValues() {
        return List.of(SELECTED);
    }

    public String selected(ElementStateView state) {
        return views.get(state.get(this, SELECTED)).common().id().orElseThrow();
    }

    public String defaultSelection() {
        return defaultView().common().id().orElseThrow();
    }

    public boolean hasSelection(String selection) {
        return views.stream()
            .anyMatch(view -> view.common().id().orElse("").equals(selection));
    }

    public boolean select(ElementStates state, String selection) {
        for (int i = 0; i < views.size(); i++)
            if (views.get(i).common().id().orElseThrow().equals(selection))
                return state.set(this, SELECTED, i);
        return false;
    }

    public boolean childVisible(Element child, ElementStateView state) {
        return child == views.get(state.get(this, SELECTED));
    }

    /** Copies the views. */
    public ContextElement(Common common, List<ViewElement> views) {
        views = List.copyOf(views);

        this.common = common;
        this.views = views;
    }

    @Override
    public List<Element> children() {
        return List.copyOf(views);
    }

    /** The view shown when the screen opens. */
    public ViewElement defaultView() {
        return views.getFirst();
    }

    public void prepareLayout(LayoutContext context) {
        views.stream().skip(1).forEach(context::reserveHeader);
    }

    public ViewElement selected(TextMeasure text) {
        String selected = null;
        if (text instanceof LayoutContext context) {
            selected = context.selected(common.id().orElse(""));
            if (selected == null)
                return views.get(context.state().get(this, SELECTED));
        }
        final String selection = selected;
        return views.stream()
            .filter(view -> view.common().id().orElse("").equals(selection))
            .findFirst().orElse(defaultView());
    }

    public int[] measure(TextMeasure text) {
        return ElementLayouts.size(selected(text), text);
    }

    public boolean stretchX() {
        return true;
    }

    public Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
        var result = new java.util.IdentityHashMap<Element, Rect>();
        var selected = selected(text);
        for (var view : views) {
            var size = ElementLayouts.size(view, text);
            result.put(view, new Rect(0, 0, Math.max(bounds.width(), size[0]),
                view == selected ? bounds.height() : size[1]));
        }
        return result;
    }

    public void validate() {
        SynchronizedElement.super.validate();
        if (common.id().isEmpty())
            throw new IllegalArgumentException(
                "<context> needs an id for buttons to name");
        if (views.isEmpty())
            throw new IllegalArgumentException("<context> needs a view");
    }

    public void validateScreen(Screen screen) {
        for (var view : views)
            if (view.common().id().isEmpty())
                throw new ScreenException(screen.source(view.common()),
                    view.common().line(),
                    "<view> needs an id for buttons to name");
    }

    public static final ElementAction<ElementActions.Click> BACK = new ElementAction<>(
        ResourceLocation.parse("mmdlib:context_back"),
        ElementActions.Click.CODEC, 8, (context, click) -> {
            if (click.option() != 0)
                return false;
            var element = (ContextElement) context.element();
            context.menu().navigate(element.common().id().orElseThrow(),
                element.defaultView().common().id().orElseThrow());
            return true;
        });

    public List<? extends ElementAction<?>> actions() {
        return List.of(ElementActions.CLICK, BACK);
    }

    public Optional<ElementAction<ElementActions.Click>> legacyAction(
        int kind) {
        return kind == 2
            ? Optional.of(BACK)
            : SynchronizedElement.super.legacyAction(kind);
    }

    public Common common() {
        return common;
    }

    public List<ViewElement> views() {
        return views;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (ContextElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(views, other.views);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(views);
        return hash;
    }

    @Override
    public String toString() {
        return "ContextElement[common=" + common + ", views=" + views + "]";
    }
}
