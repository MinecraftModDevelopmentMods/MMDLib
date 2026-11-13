package zone.moddev.mc.lib.gui.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.ElementInteractions;
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.LayoutContext;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ResourceValidationContext;
import zone.moddev.mc.lib.gui.load.ScreenValidationContext;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypeRegistry;

/**
 * Common contract for built-in and mod-defined UI elements. Shared attributes
 * come from {@link #common()}; containers expose their children in document
 * order.
 */
public interface Element {
    /** The attributes every element takes. */
    Common common();

    /** Size in GUI pixels, shared by built-in and mod-defined elements. */
    default int[] measure(TextMeasure text) {
        throw new IllegalStateException(
            "Element must implement measure: " + getClass().getName());
    }

    /**
     * Measure content that wraps within an available width, such as a reusable
     * editor fragment.
     */
    default int[] measure(TextMeasure text, int availableWidth) {
        return measure(text);
    }

    /**
     * Child rectangles relative to this element, for built-in and mod-defined
     * containers.
     */
    default Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
        if (!children().isEmpty())
            throw new IllegalStateException(
                "Container must implement childLayout: "
                    + getClass().getName());
        return Map.of();
    }

    /**
     * Contribute contextual layout metadata before measuring the element tree.
     */
    default void prepareLayout(LayoutContext context) {
    }

    /**
     * Optional per-layout plan for footer placement and shared named regions.
     */
    default Optional<zone.moddev.mc.lib.gui.layout.LayoutContribution> layoutContribution(
        zone.moddev.mc.lib.gui.layout.LayoutContribution.Context context) {
        return Optional.empty();
    }

    /**
     * Fill the available horizontal space when the parent stacks vertically.
     */
    default boolean stretchX() {
        return false;
    }

    /**
     * Fill the available vertical space when the parent stacks horizontally.
     */
    default boolean stretchY() {
        return false;
    }

    /** Fill the panel width when placed directly in the screen body. */
    default boolean stretchToPanel() {
        return stretchX();
    }

    /** Share a box's remaining main-axis space with the other spacers. */
    default boolean spacer() {
        return false;
    }

    /** Validate the declared host class before a runtime host exists. */
    default void validateHostType(Class<?> type) {
    }

    /** Validate host-owned services before constructing its menu. */
    default void validateHost(ScreenHost host) {
    }

    /**
     * Host validation with screen context, including shared claims across all
     * elements.
     */
    default void validateHost(ScreenValidationContext context,
        ScreenHost host) {
        validateHost(host);
    }

    /** Validate named definition bindings, such as available processor keys. */
    default void validateBindings(Map<String, Set<String>> bindings) {
    }

    /**
     * Additional translated placeholders evaluated from synchronized values.
     */
    default Map<String, Text> tooltipArguments(Object value, Object maximum) {
        return Map.of();
    }

    /**
     * Additional client-local tooltip lines evaluated from synchronized values.
     */
    default List<Component> valueTooltip(Object value, Object maximum) {
        return List.of();
    }

    /**
     * Standard behavior available on any element, independently of how the
     * screen was built.
     */
    default Optional<Interaction> interaction() {
        return common().interaction();
    }

    /**
     * Optional navigation binding, independent of this element's rendering or
     * control type.
     */
    default Optional<Navigation> navigation() {
        return Optional.empty();
    }

    /** Whether the client should send ordinary clicks to this element. */
    default boolean interactive() {
        return interaction().isPresent();
    }

    /** Validate the element's configuration before binding a screen. */
    default void validate() {
        interaction().ifPresent(binding -> {
            ElementInteractions.require(binding.type())
                .validate(binding.target());
        });
    }

    /** Validate processor bindings against a machine's declared schema. */
    default void validateDefinition(
        Map<String, zone.moddev.mc.lib.machine.feature.Processor<?>> processors) {
    }

    /** Includes visual defaults for elements with conditional bindings. */
    default void validateDefinition(
        Map<String, zone.moddev.mc.lib.machine.feature.Processor<?>> processors,
        Map<String, Object> defaults) {
        validateDefinition(processors);
    }

    /** The host is null for screens bound to ordinary block entities. */
    default boolean serverEnabled(
        @org.jetbrains.annotations.Nullable ScreenHost host,
        net.minecraft.world.entity.player.Player player) {
        return true;
    }

    /**
     * Click handlers that require registration in the material event system.
     */
    default java.util.List<net.minecraft.resources.ResourceLocation> clickCallbacks() {
        var clicks = common().clicks();
        return java.util.stream.Stream.of(clicks.click(), clicks.rightClick(),
            clicks.shiftClick(), clicks.shiftRightClick())
            .flatMap(Optional::stream).toList();
    }

    /** Resource identity used by generic validation and highlighting. */
    default Optional<ResourceBinding> resourceBinding() {
        return interaction().flatMap(binding -> ElementInteractions
            .require(binding.type()).resourceBinding(binding.target()));
    }

    /**
     * Definition-time validation. Defaults preserve existing binding and
     * resource hooks.
     */
    default void validateResources(ResourceValidationContext context) {
        validateBindings(context.bindings());
        validateResources(context.resources());
    }

    /**
     * Validates both the displayed resource and any independently attached
     * interaction.
     */
    default void validateResources(Map<String, Integer> counts) {
        resourceBinding().ifPresent(binding -> binding.validate(counts));
        interaction()
            .flatMap(binding -> ElementInteractions.require(binding.type())
                .resourceBinding(binding.target()))
            .ifPresent(binding -> binding.validate(counts));
    }

    /** Runs after the menu validates the player and click packet. */
    default boolean interact(ScreenClick click) {
        return interaction().map(binding -> ElementInteractions
            .require(binding.type()).click(click, binding.target()))
            .orElse(false);
    }

    /** Optional element-owned values, sampled only on the server. */
    default Optional<ElementSnapshot> snapshot(ScreenHost host) {
        return Optional.empty();
    }

    /**
     * Converts a synchronized value for presentation without changing the
     * stored snapshot.
     */
    default Object displayValue(Object value) {
        return value;
    }

    /**
     * Formats a synchronized current or maximum value; detailed means exact
     * rather than abbreviated.
     */
    default Component displayText(Object value, boolean detailed) {
        return Component
            .literal(DisplayValues.number(displayValue(value), detailed));
    }

    /**
     * Fill amount for shared rendering. Implementations return a finite value
     * in the range 0..1.
     */
    default double fillRatio(Object value, Object maximum) {
        return DisplayValues.ratio(displayValue(value), displayValue(maximum));
    }

    /** Tooltip text evaluated on the client against synchronized values. */
    default Optional<Text> tooltipText() {
        return common().tooltip();
    }

    /** Per-viewer tooltip, sampled only on the server. */
    default List<Component> tooltip(ScreenHost host, ItemStack carried) {
        return List.of();
    }

    /**
     * Own tooltip content. Override to replace it, or call the default to
     * extend it.
     */
    default void appendTooltip(TooltipContext context, List<Component> rows) {
        context.appendDefaults(rows);
    }

    /**
     * Optional rows for a hovered descendant; the container's own tooltip is
     * not repeated.
     */
    default void appendChildTooltip(TooltipContext context,
        List<Component> rows) {
    }

    /**
     * Group IDs associated with this element, in preference order. The default
     * matches explicit element anchors in group declaration order; subclasses
     * may extend or replace those matches.
     */
    default List<String> associatedGroups(Screen screen,
        List<ElementGroup> groups) {
        return groups.stream().filter(group -> common().id()
            .filter(group.anchors()::contains).isPresent()
            || (group.anchors().isEmpty()
                && resourceBinding().filter(binding -> group.members()
                    .filter(
                        members -> members.type().equals(binding.groupType())
                            && members.indices().contains(binding.index()))
                    .isPresent()).isPresent()))
            .map(ElementGroup::id).toList();
    }

    /**
     * Groups whose decoration and selection highlight should include this
     * element.
     */
    default List<String> highlightedGroups(Screen screen,
        List<ElementGroup> groups) {
        return associatedGroups(screen, groups);
    }

    /**
     * Whether this selected container represents a descendant's highlight for
     * the same group.
     */
    default boolean replacesChildHighlight(Screen screen, Element descendant,
        String group, List<ElementGroup> groups) {
        return false;
    }

    /** Optional declared anchor whose visibility an implicit root follows. */
    default Optional<Element> visibilityAnchor(Screen screen) {
        return Optional.empty();
    }

    /**
     * Pixels in one slot: the unit of {@code s}-suffixed sizes such as
     * {@code length="3s"}.
     */
    int SLOT = 18;

    /**
     * A {@code length} of {@code fill}: as long as the box around the element
     * is across. Layout replaces it with the real length.
     */
    int FILL = -1;

    /** The element's children; empty for leaves. */
    default List<Element> children() {
        return List.of();
    }

    /** Value contracts are common-side and can be supplied by mod elements. */
    default Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
        return ValueTypeRegistry.GLOBAL.bindingTypes();
    }

    default Set<ValueType<?>> maximumKinds() {
        return ValueContract.NUMBERS;
    }

    default void validate(WidgetTypes widgets) {
        validate();
    }

    /**
     * Definition-specific references/structure, checked for XML and
     * Java-created screens.
     */
    default void validateScreen(Screen screen) {
    }

    /**
     * Screen-wide validation with shared claims; existing screen-only hooks are
     * also invoked.
     */
    default void validateScreen(ScreenValidationContext context) {
        validateScreen(context.screen());
        navigation().ifPresent(binding -> binding.validate(context.screen()));
    }

    default Optional<Orientation> fillOrientation() {
        return Optional.empty();
    }

    default boolean verticalChildren(boolean verticalParent) {
        return true;
    }

    default boolean acceptsSpacers() {
        return false;
    }

    /** The complete allowlist of typed server actions for this definition. */
    default List<? extends ElementAction<?>> actions() {
        return navigation().isPresent()
            ? List.of(ElementActions.CLICK, Navigation.NAVIGATE)
            : List.of(ElementActions.CLICK);
    }

    default Optional<? extends ElementAction<?>> action(ResourceLocation id) {
        return actions().stream().filter(action -> action.id().equals(id))
            .findFirst();
    }

    default boolean validClickOption(int option) {
        return option == 0;
    }

    /**
     * Compatibility for old packed button packets; new clients use named typed
     * actions.
     */
    default Optional<ElementAction<ElementActions.Click>> legacyAction(
        int kind) {
        if (kind == 1 && navigation().isPresent())
            return Optional.of(Navigation.NAVIGATE);
        return kind == 0 ? Optional.of(ElementActions.CLICK) : Optional.empty();
    }

    /** Optional definition name included in validation diagnostics. */
    default String validationName() {
        return "";
    }
}
