package zone.moddev.mc.lib.gui.load;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.xml.namespace.QName;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.automation.FilterControl;
import zone.moddev.mc.lib.gui.ElementType;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.PopoutKind;
import zone.moddev.mc.lib.gui.model.ProgressDirection;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.ViewRef;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement;
import zone.moddev.mc.lib.gui.model.containers.ContainerInventoryElement;
import zone.moddev.mc.lib.gui.model.containers.ContextElement;
import zone.moddev.mc.lib.gui.model.containers.GroupElement;
import zone.moddev.mc.lib.gui.model.containers.InventoryControlsElement;
import zone.moddev.mc.lib.gui.model.containers.InventoryElement;
import zone.moddev.mc.lib.gui.model.containers.NavigableElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerArmourElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerOffhandElement;
import zone.moddev.mc.lib.gui.model.containers.PlayerSlotsElement;
import zone.moddev.mc.lib.gui.model.containers.RadioGroupElement;
import zone.moddev.mc.lib.gui.model.containers.SideTabElement;
import zone.moddev.mc.lib.gui.model.containers.SpacerElement;
import zone.moddev.mc.lib.gui.model.containers.ViewElement;
import zone.moddev.mc.lib.gui.model.elements.AnimatedSpriteElement;
import zone.moddev.mc.lib.gui.model.elements.ButtonControl;
import zone.moddev.mc.lib.gui.model.elements.ButtonElement;
import zone.moddev.mc.lib.gui.model.elements.CheckboxElement;
import zone.moddev.mc.lib.gui.model.elements.ContainerSlotElement;
import zone.moddev.mc.lib.gui.model.elements.CustomElement;
import zone.moddev.mc.lib.gui.model.elements.EnergyGaugeElement;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.gui.model.elements.GaugeElement;
import zone.moddev.mc.lib.gui.model.elements.GhostSlotElement;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;
import zone.moddev.mc.lib.gui.model.elements.LabelElement;
import zone.moddev.mc.lib.gui.model.elements.MeterElement;
import zone.moddev.mc.lib.gui.model.elements.PopoutElement;
import zone.moddev.mc.lib.gui.model.elements.ProcessorButtonElement;
import zone.moddev.mc.lib.gui.model.elements.ProgressElement;
import zone.moddev.mc.lib.gui.model.elements.ProgressSpriteElement;
import zone.moddev.mc.lib.gui.model.elements.RadioElement;
import zone.moddev.mc.lib.gui.model.elements.SideConfigElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.model.elements.SpriteElement;

/**
 * Common built-in declarations shared by XML loading and client setup; no
 * runtime lookup table.
 */
public final class BuiltinElements {
    private BuiltinElements() {
    }

    public static final ElementType<BoxElement> BOX = builtin("vbox",
        BoxElement.class, x -> box(x, true), "widget")
        .withXml(name("hbox"), ScreenSchemas.builtin("hbox", BoxElement.class,
            x -> box(x, false), ScreenTag.Role.ELEMENT, "widget"));
    public static final ElementType<SpacerElement> SPACER = builtin("spacer",
        SpacerElement.class, BuiltinElements::spacer, "widget");
    public static final ElementType<InventoryControlsElement> INVENTORY_CONTROLS = builtin(
        "inventory-controls", InventoryControlsElement.class,
        BuiltinElements::inventoryControls, "controls");
    public static final ElementType<CustomElement> CUSTOM = builtin("custom",
        CustomElement.class, BuiltinElements::custom, "widget");
    public static final ElementType<GroupElement> GROUP = builtin("group",
        GroupElement.class, BuiltinElements::group, "widget");
    public static final ElementType<InventoryElement> INVENTORY = builtin(
        "inventory", InventoryElement.class, BuiltinElements::inventory,
        "widget");
    public static final ElementType<SlotElement> SLOT = builtin("slot",
        SlotElement.class, x -> slot(x, false), "widget", "slots")
        .withXml(name("item-slot"),
            ScreenSchemas.builtin("item-slot", SlotElement.class,
                x -> slot(x, false), ScreenTag.Role.ELEMENT, "widget",
                "slots"));
    public static final ElementType<GhostSlotElement> GHOST_SLOT = builtin(
        "ghost-slot", GhostSlotElement.class,
        x -> (GhostSlotElement) slot(x, true), "widget", "slots");
    // The legacy gauge tag may construct either GaugeElement or
    // FluidTankElement.
    public static final ElementType<Element> LEGACY_GAUGE = builtin("gauge",
        Element.class, BuiltinElements::gauge, "widget");
    public static final ElementType<EnergyGaugeElement> ENERGY_GAUGE = builtin(
        "energy-gauge", EnergyGaugeElement.class, BuiltinElements::energyGauge,
        "widget");
    public static final ElementType<MeterElement> METER = builtin("meter",
        MeterElement.class, BuiltinElements::meter, "widget");
    public static final ElementType<SpriteElement> SPRITE = builtin("sprite",
        SpriteElement.class, BuiltinElements::sprite, "widget");
    public static final ElementType<AnimatedSpriteElement> ANIMATED_SPRITE = builtin(
        "animated-sprite", AnimatedSpriteElement.class,
        BuiltinElements::animatedSprite, "widget");
    public static final ElementType<ProgressSpriteElement> PROGRESS_SPRITE = builtin(
        "progress-sprite", ProgressSpriteElement.class,
        BuiltinElements::progressSprite, "widget");
    public static final ElementType<ColouredGroupElement> COLOURED_GROUP = builtin(
        "coloured-group", ColouredGroupElement.class,
        BuiltinElements::colouredGroup, "widget");
    public static final ElementType<ProgressElement> PROGRESS = builtin(
        "progress", ProgressElement.class, BuiltinElements::progress, "widget");
    public static final ElementType<LabelElement> LABEL = builtin("label",
        LabelElement.class, BuiltinElements::label, "widget");
    public static final ElementType<ProcessorButtonElement> PROCESSOR_BUTTON = builtin(
        "processor-button", ProcessorButtonElement.class,
        BuiltinElements::processorButton, "widget", "buttons");
    public static final ElementType<ButtonElement> BUTTON = builtin("button",
        ButtonElement.class, BuiltinElements::button, "widget", "buttons");
    public static final ElementType<CheckboxElement> CHECKBOX = builtin(
        "checkbox", CheckboxElement.class, BuiltinElements::checkbox, "widget");
    public static final ElementType<RadioGroupElement> RADIO_GROUP = builtin(
        "radio-group", RadioGroupElement.class, BuiltinElements::radioGroup,
        "widget");
    public static final ElementType<RadioElement> RADIO = builtin("radio",
        RadioElement.class, BuiltinElements::radio, "radios");
    public static final ElementType<ContextElement> CONTEXT = builtin("context",
        ContextElement.class, BuiltinElements::context, "widget");
    public static final ElementType<ViewElement> VIEW = builtin("view",
        ViewElement.class, BuiltinElements::view, "views");
    public static final ElementType<SideConfigElement> SIDE_CONFIG = builtin(
        "side-config", SideConfigElement.class, BuiltinElements::sideConfig,
        "widget");
    public static final ElementType<PlayerInventoryElement> PLAYER_INVENTORY = builtin(
        "player-inventory", PlayerInventoryElement.class,
        BuiltinElements::playerInventory, "widget");
    public static final ElementType<PlayerArmourElement> PLAYER_ARMOUR = builtin(
        "player-armour", PlayerArmourElement.class,
        x -> new PlayerArmourElement(x.common()), "widget");
    public static final ElementType<PlayerOffhandElement> PLAYER_OFFHAND = builtin(
        "player-offhand", PlayerOffhandElement.class,
        x -> new PlayerOffhandElement(x.common()), "widget");
    public static final ElementType<SideTabElement> SIDE_TAB = builtin(
        "side-tab", SideTabElement.class, BuiltinElements::sideTab, "dock");
    public static final ElementType<PopoutElement> POPOUT = builtin("popout",
        PopoutElement.class, BuiltinElements::popout, "dock");
    public static final ElementType<FluidTankElement> FLUID_TANK = builtin(
        "fluid-tank", FluidTankElement.class, BuiltinElements::fluidTank,
        "widget");
    public static final ElementType<GaugeElement> GAUGE = ElementType
        .of(GaugeElement.class);
    public static final ElementType<ButtonControl> BUTTON_CONTROL = ElementType
        .of(ButtonControl.class);
    public static final ElementType<NavigableElement> NAVIGABLE = ElementType
        .of(NavigableElement.class);
    public static final ElementType<PlayerSlotsElement> PLAYER_SLOTS = ElementType
        .of(PlayerSlotsElement.class);
    public static final ElementType<ContainerInventoryElement> CONTAINER_INVENTORY = ElementType
        .of(ContainerInventoryElement.class);
    public static final ElementType<ContainerSlotElement> CONTAINER_SLOT = ElementType
        .of(ContainerSlotElement.class);
    public static final ElementType<FilterControl.Box> FILTER_BOX = ElementType
        .of(FilterControl.Box.class);
    public static final ElementType<FilterControl.Toggle> FILTER_TOGGLE = ElementType
        .of(FilterControl.Toggle.class);
    public static final ElementType<FilterControl.Number> FILTER_NUMBER = ElementType
        .of(FilterControl.Number.class);
    public static final ElementType<FilterControl.Samples> FILTER_SAMPLES = ElementType
        .of(FilterControl.Samples.class);
    public static final ElementType<FilterControl.Text> FILTER_TEXT = ElementType
        .of(FilterControl.Text.class);
    public static final ElementType<FilterControl.Custom> FILTER_CUSTOM = ElementType
        .of(FilterControl.Custom.class);

    private static QName name(String local) {
        return new QName(ScreenReader.NAMESPACE, local);
    }

    private static <E extends Element> ElementType<E> builtin(String local,
        Class<E> type, Function<ScreenXml, E> factory, String... groups) {
        return ElementType.xml(name(local), ScreenSchemas.builtin(local, type,
            factory, ScreenTag.Role.ELEMENT, groups));
    }

    static void register(ScreenTags tags) {
        for (var element : List.of(BOX, SPACER, INVENTORY_CONTROLS, CUSTOM,
            GROUP, INVENTORY, SLOT, GHOST_SLOT, LEGACY_GAUGE, ENERGY_GAUGE,
            METER, SPRITE, ANIMATED_SPRITE, PROGRESS_SPRITE, COLOURED_GROUP,
            PROGRESS, LABEL, PROCESSOR_BUTTON, BUTTON, CHECKBOX, RADIO_GROUP,
            RADIO, CONTEXT, VIEW, SIDE_CONFIG, PLAYER_INVENTORY, PLAYER_ARMOUR,
            PLAYER_OFFHAND, SIDE_TAB, POPOUT, FLUID_TANK))
            element.register(tags);
        tags.register("screen", ScreenSchemas.builtin("screen", Screen.class,
            BuiltinElements::screen, ScreenTag.Role.DOCUMENT));
        tags.register("property",
            ScreenSchemas.builtin("property", Property.class,
                x -> new Property(x.attribute("name"), x.attribute("value")),
                ScreenTag.Role.DATA));
        tags.register("placeholder",
            ScreenSchemas.builtin("placeholder", Object.class, x -> {
                throw x.error("Unexpanded placeholder");
            }, ScreenTag.Role.PLACEHOLDER, "widget"));
        tags.register("fill", ScreenSchemas.builtin("fill", Object.class, x -> {
            throw x.error("Unexpanded fill");
        }, ScreenTag.Role.FILL));
    }

    private record Property(String name, String value) {
    }

    private static Screen screen(ScreenXml x) {
        var body = new ArrayList<Element>();
        var tabs = new ArrayList<Element>();
        boolean ordinary = false;
        for (var child : x.node().children()) {
            var tag = x.tags().require(child);
            var element = x.read(child, Element.class);
            if (tag.groups().contains("dock"))
                tabs.add(element);
            else {
                body.add(element);
                if (!tag.groups().contains("controls"))
                    ordinary = true;
            }
        }
        if (!ordinary)
            throw x.error("A screen requires at least one body element");
        return new Screen(x.screenId(), x.node().source(),
            x.text("title").orElseThrow(), x.attribute("block-entity"),
            !x.optional("inventory").orElse("default").equals("none"), body,
            tabs, x.origins());
    }

    private static FluidTankElement fluidTank(ScreenXml x) {
        if (x.optional("color").isPresent())
            throw x.error("A fluid tank uses its fluid texture");
        return new FluidTankElement(x.common(), x.integer("tank"),
            x.enumValue(Orientation.class, "orientation"), x.length(8));
    }

    private static CustomElement customElement(ScreenXml x) {
        var properties = new LinkedHashMap<String, String>();
        for (var child : x.node().children()) {
            var property = x.read(child, Property.class);
            if (properties.putIfAbsent(property.name(),
                property.value()) != null)
                throw new ScreenException(child.source(), child.line(),
                    "Duplicate widget property: " + property.name());
        }
        return new CustomElement(x.common(), x.id("type").orElseThrow(),
            x.size("width", 1), x.size("height", 1), properties);
    }

    private static BoxElement box(ScreenXml x, boolean vertical) {
        var node = x.node();
        var c = x.common();
        return new BoxElement(c, vertical, x.size("padding", 0),
            x.size("spacing", 0), x.enumValue(Align.class, "align"),
            x.children());
    }

    private static SpacerElement spacer(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new SpacerElement(c);
    }

    private static InventoryControlsElement inventoryControls(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new InventoryControlsElement(c, x.children());
    }

    private static CustomElement custom(ScreenXml x) {
        var node = x.node();
        return customElement(x);
    }

    private static GroupElement group(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new GroupElement(c, x.text("label"), x.color("tint"),
            x.size("padding", 0), x.children());
    }

    private static InventoryElement inventory(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new InventoryElement(c, node.attributes().get("storage"),
            x.integer("columns"), x.size("spacing", 0), x.children());
    }

    private static SlotElement slot(ScreenXml x, boolean ghost) {
        var node = x.node();
        var c = x.common();
        var appearance = new SlotElement.Appearance(
            x.enumValue(SlotSize.class, "size"), x.color("tint"), x.id("icon"),
            x.id("background").orElse(ResourceLocation.parse("mmdlib:slot")),
            Float.parseFloat(
                node.attributes().getOrDefault("ghost-alpha", ".42")),
            x.id("icon-atlas"), x.size("item-size", 16));
        var kind = x.enumValue(SlotKind.class, "kind");
        return ghost || kind == SlotKind.GHOST
            ? new GhostSlotElement(c, x.integer("index"), appearance)
            : new ItemSlotElement(c, x.integer("index"), kind, appearance);
    }

    private static Element gauge(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        if (node.attributes().containsKey("tank"))
            return fluidTank(x);
        return new GaugeElement(c,
            x.enumValue(Orientation.class, "orientation"), x.length(8),
            x.color("color"));
    }

    private static EnergyGaugeElement energyGauge(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new EnergyGaugeElement(c,
            x.enumValue(Orientation.class, "orientation"), x.length(8),
            x.color("color").orElseThrow());
    }

    private static MeterElement meter(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new MeterElement(c,
            x.enumValue(Orientation.class, "orientation"),
            x.integer("thickness"), x.length(4), x.color("color").orElseThrow(),
            x.size("segment", 1), x.size("gap", 0), x.color("gap-color"));
    }

    private static SpriteElement sprite(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new SpriteElement(c, x.id("sprite").orElseThrow(),
            x.size("width", 1), x.size("height", 1),
            x.color("tint").orElse(0xffffffff));
    }

    private static AnimatedSpriteElement animatedSprite(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new AnimatedSpriteElement(c,
            x.words("frames").stream().map(ResourceLocation::parse).toList(),
            x.size("width", 1), x.size("height", 1),
            x.color("tint").orElse(0xffffffff), x.integer("ticks-per-frame"),
            Set.of("true", "1").contains(node.attributes().get("loop")));
    }

    private static ProgressSpriteElement progressSprite(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new ProgressSpriteElement(c, x.id("background").orElseThrow(),
            x.id("foreground").orElseThrow(), x.size("width", 1),
            x.size("height", 1),
            x.enumValue(ProgressDirection.class, "direction"),
            x.color("tint").orElse(0xffffffff),
            x.color("background-tint").orElse(0xffffffff), false);
    }

    private static ColouredGroupElement colouredGroup(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new ColouredGroupElement(c, x.color("color").orElseThrow(),
            x.read(node.children().getFirst(), Element.class));
    }

    private static ProgressElement progress(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new ProgressElement(c, x.id("icon"),
            x.enumValue(ProgressDirection.class, "direction"));
    }

    private static LabelElement label(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new LabelElement(c, x.text("text").orElseThrow());
    }

    private static ProcessorButtonElement processorButton(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new ProcessorButtonElement(c,
            Optional.ofNullable(node.attributes().get("processor-key")),
            x.text("text"), Set.of("true", "1")
                .contains(node.attributes().getOrDefault("flat", "true")));
    }

    private static ButtonElement button(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new ButtonElement(c, x.text("text"), x.id("icon"),
            Optional.ofNullable(node.attributes().get("shows"))
                .map(ViewRef::parse),
            x.words("icons").stream().map(ResourceLocation::parse).toList(),
            x.words("value-labels").stream()
                .map(key -> new Text(x.translationKey(key), Optional.empty()))
                .toList(),
            Set.of("true", "1")
                .contains(node.attributes().getOrDefault("flat", "false")));
    }

    private static CheckboxElement checkbox(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new CheckboxElement(c, x.text("text").orElseThrow());
    }

    private static RadioGroupElement radioGroup(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new RadioGroupElement(c, x.text("label"),
            x.children(RadioElement.class));
    }

    private static RadioElement radio(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new RadioElement(c, x.text("text").orElseThrow());
    }

    private static ContextElement context(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new ContextElement(c, x.children(ViewElement.class));
    }

    private static ViewElement view(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new ViewElement(c, x.text("title"), x.children());
    }

    private static SideConfigElement sideConfig(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new SideConfigElement(c,
            List.of(node.attributes().get("groups").trim().split("\\s+")));
    }

    private static PlayerInventoryElement playerInventory(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new PlayerInventoryElement(c);
    }

    private static SideTabElement sideTab(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new SideTabElement(c, x.enumValue(Edge.class, "edge"),
            x.children());
    }

    private static PopoutElement popout(ScreenXml x) {
        var node = x.node();
        var c = x.common();
        return new PopoutElement(c, x.enumValue(PopoutKind.class, "kind"),
            x.id("icon"), x.enumValue(Edge.class, "edge"), x.text("text"));
    }
}
