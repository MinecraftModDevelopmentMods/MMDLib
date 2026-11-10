package zone.moddev.mc.lib.gui.client;

import static zone.moddev.mc.lib.gui.load.BuiltinElements.BOX;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.BUTTON_CONTROL;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.CHECKBOX;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.COLOURED_GROUP;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.CONTAINER_INVENTORY;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.CONTAINER_SLOT;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.CONTEXT;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.CUSTOM;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.ENERGY_GAUGE;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.FILTER_BOX;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.FILTER_CUSTOM;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.FILTER_NUMBER;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.FILTER_SAMPLES;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.FILTER_TEXT;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.FILTER_TOGGLE;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.FLUID_TANK;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.GAUGE;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.GROUP;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.INVENTORY;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.INVENTORY_CONTROLS;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.LABEL;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.METER;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.NAVIGABLE;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.PLAYER_SLOTS;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.POPOUT;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.PROGRESS;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.RADIO;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.RADIO_GROUP;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.SIDE_CONFIG;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.SIDE_TAB;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.SLOT;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.SPACER;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.SPRITE;
import static zone.moddev.mc.lib.gui.load.BuiltinElements.VIEW;

import java.util.List;

/** One client attachment per built-in type, using the same public API as mods. */
final class BuiltinElementClients {
    private BuiltinElementClients() {
    }

    static void registerRenderers(ElementRenderers registry) {
        declarations().forEach(declaration -> declaration.registerRenderers(registry));
    }

    static void registerBehaviors(ElementBehaviors registry) {
        declarations().forEach(declaration -> declaration.registerBehaviors(registry));
    }

    private static List<ElementClient<?>> declarations() {
        var slots = new SlotRenderer();
        return List.of(
            ElementClient.of(FILTER_BOX).renderer((element, c) -> {
            }),
            ElementClient.of(FILTER_TOGGLE)
                .renderer(FilterToggleBehavior::render)
                .behavior(FilterToggleBehavior::new),
            ElementClient.of(FILTER_NUMBER)
                .renderer(FilterNumberBehavior::render)
                .behavior(FilterNumberBehavior::new),
            ElementClient.of(FILTER_SAMPLES)
                .renderer(FilterSamplesBehavior::render)
                .behavior(FilterSamplesBehavior::new),
            ElementClient.of(FILTER_TEXT)
                .renderer(
                    (element, c) -> c.access()
                        .capability(element, FilterControlPresentation.class)
                        .ifPresent(view -> view.render(c)))
                .behavior(FilterTextBehavior::new),
            ElementClient.of(FILTER_CUSTOM)
                .renderer(
                    (element, c) -> c.access()
                        .capability(element, FilterControlPresentation.class)
                        .ifPresent(view -> view.render(c)))
                .behavior(FilterCustomBehavior::new),
            ElementClient.of(SPRITE).renderer(SpriteRenderer::draw),
            ElementClient.of(PROGRESS)
                .renderer((element, c) -> SpriteRenderer.draw(element.spriteElement(), c)),
            ElementClient.of(COLOURED_GROUP)
                .renderer((element, c) -> {
                })
                .overlay(
                    (element, c) -> ColouredGroupRenderer.draw(element, c.graphics(), c.bounds(),
                        false))
                .behavior(ColouredGroupBehavior::new),
            ElementClient.of(BOX).renderer((element, c) -> {
            }),
            ElementClient.of(SPACER).renderer((element, c) -> {
            }),
            ElementClient.of(INVENTORY_CONTROLS).renderer((element, c) -> {
            }),
            ElementClient.of(PLAYER_SLOTS).renderer((element, c) -> {
            }),
            ElementClient.of(INVENTORY).renderer((element, c) -> {
            }),
            ElementClient.of(CONTAINER_INVENTORY).renderer(ContainerCellRenderer::inventory),
            ElementClient.of(CONTAINER_SLOT).renderer(ContainerCellRenderer::slot),
            ElementClient.of(GROUP)
                .renderer(ContainerElementRenderer::group)
                .behavior(GroupBehavior::new),
            ElementClient.of(RADIO_GROUP)
                .renderer(ContainerElementRenderer::radioGroup)
                .behavior(RadioGroupBehavior::new),
            ElementClient.of(VIEW).renderer(ContainerElementRenderer::view),
            ElementClient.of(CONTEXT)
                .renderer(ContainerElementRenderer::context)
                .behavior(ContextBehavior::new),
            ElementClient.of(SIDE_TAB).renderer(ContainerElementRenderer::sideTab),
            ElementClient.of(BUTTON_CONTROL)
                .renderer(ButtonRenderer::draw)
                .behavior(ButtonBehavior::new),
            ElementClient.of(LABEL).renderer(TextControlRenderer::label),
            ElementClient.of(CHECKBOX).renderer(TextControlRenderer::checkbox),
            ElementClient.of(RADIO).renderer(TextControlRenderer::radio)
                .behavior(RadioBehavior::new),
            ElementClient.of(SLOT)
                .renderer(
                    (element, c) -> slots.draw(
                        c.graphics(),
                        c.bounds(),
                        element,
                        c.access().contents(element),
                        c.access().hint(element)))
                .behavior(SlotBehavior::new),
            ElementClient.of(GAUGE)
                .renderer(
                    (element, c) -> GaugeRenderer.draw(
                        c.graphics(),
                        c.bounds(),
                        element.orientation(),
                        element.fill(c.value()),
                        c.ratio())),
            ElementClient.of(FLUID_TANK)
                .renderer(
                    (element, c) -> GaugeRenderer.draw(
                        c.graphics(),
                        c.bounds(),
                        element.orientation(),
                        element.fill(c.value()),
                        c.ratio())),
            ElementClient.of(ENERGY_GAUGE)
                .renderer(
                    (element, c) -> EnergyGaugeRenderer.draw(c.graphics(), c.bounds(), element,
                        c.ratio())),
            ElementClient.of(METER).renderer(MeterRenderer::draw),
            ElementClient.of(SIDE_CONFIG)
                .renderer(SideConfigRenderer::draw)
                .renderer(
                    ElementRenderers.Pass.OVERLAY,
                    ElementRenderers.Clip.VIEWPORT,
                    SideConfigRenderer::highlight)
                .behavior(SideConfigBehavior::new),
            ElementClient.of(POPOUT)
                .renderer(PopoutRenderer::tab)
                .foreground(PopoutRenderer::body)
                .top(PopoutRenderer::tab),
            ElementClient.of(CUSTOM)
                .renderer(WidgetRenderers.GLOBAL.elementRenderer())
                .behavior(CustomWidgetBehavior::new),
            ElementClient.of(NAVIGABLE).behavior(NavigableBehavior::new));
    }
}
