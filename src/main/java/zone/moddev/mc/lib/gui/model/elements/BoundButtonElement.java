package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.ViewRef;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;
import zone.moddev.mc.lib.machine.feature.Processor;
import zone.moddev.mc.lib.machine.presentation.VisualCondition;

/**
 * Ordinary button with a processor action and/or an observable enabled
 * condition.
 */
public final class BoundButtonElement extends ButtonElement
    implements SynchronizedElement {
    private final Optional<String> processor;
    private final Optional<String> enabledWhen;

    public static final ElementSync<Boolean> ENABLED = ElementSync
        .field(ResourceLocation.parse("mmdlib:button_enabled"),
            StreamCodec.<RegistryFriendlyByteBuf, Boolean>of(
                (b, v) -> b.writeBoolean(v), b -> b.readBoolean()),
            1, element -> false)
        .sample(
            (source, previous) -> source.machine() instanceof ScreenHost host
                && ((BoundButtonElement) source.element()).serverEnabled(host,
                    source.viewer()))
        .build();

    public BoundButtonElement(Common common, Optional<Text> text,
        Optional<ResourceLocation> icon, Optional<ViewRef> shows,
        List<ResourceLocation> icons, List<Text> labels, boolean flat,
        Optional<String> processor, Optional<String> enabledWhen) {
        super(common, text, icon, shows, icons, labels, flat);
        this.processor = processor;
        this.enabledWhen = enabledWhen;
    }

    public Optional<String> processor() {
        return processor;
    }
    public Optional<String> enabledWhen() {
        return enabledWhen;
    }
    public List<ElementSync<?>> synchronizedValues() {
        return List.of(ENABLED);
    }
    public boolean enabled(ElementStateView state) {
        return state.get(this, ENABLED);
    }

    public void validate() {
        super.validate();
        if (processor.filter(String::isBlank).isPresent()
            || enabledWhen.filter(String::isBlank).isPresent())
            throw new IllegalArgumentException(
                "Empty processor or enabled-when");
        if (processor.isPresent() && (!common().clicks().any()
            || interaction().isPresent() || navigation().isPresent()
            || !common().jeiRecipes().isEmpty()))
            throw new IllegalArgumentException(
                "Processor buttons require click actions without competing interactions");
    }

    public void validateDefinition(Map<String, Processor<?>> processors,
        Map<String, Object> defaults) {
        var processorDefaults = defaults.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("processors."))
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue));
        enabledWhen.ifPresent(expression -> VisualCondition.compile(expression,
            processorDefaults));
        processor.ifPresent(
            key -> validateActions(Optional.ofNullable(processors.get(key))
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unknown processor: " + key))));
    }

    private void validateActions(Processor<?> target) {
        var clicks = common().clicks();
        for (var action : List.of(clicks.click(), clicks.rightClick(),
            clicks.shiftClick(), clicks.shiftRightClick()))
            action.ifPresent(id -> {
                if (!target.actions().containsKey(id))
                    throw new IllegalArgumentException(
                        "Unknown processor action: " + id);
            });
    }

    public void validateHost(ScreenHost host) {
        if (host.screenFeatures() == null)
            throw new IllegalArgumentException(
                "Button binding requires processor state");
        enabledWhen.ifPresent(host.screenFeatures()::condition);
        processor.ifPresent(key -> validateActions(
            host.screenFeatures().findProcessor(key).orElseThrow()));
    }

    public boolean serverEnabled(ScreenHost host, Player player) {
        var features = host.screenFeatures();
        if (features == null)
            return false;
        if (enabledWhen.isPresent() && !features.condition(enabledWhen.get()))
            return false;
        if (processor.isEmpty())
            return true;
        var target = features.findProcessor(processor.get()).orElse(null);
        if (target == null)
            return false;
        var clicks = common().clicks();
        return List
            .of(clicks.click(), clicks.rightClick(), clicks.shiftClick(),
                clicks.shiftRightClick())
            .stream().flatMap(Optional::stream)
            .anyMatch(id -> target.actionAvailable(id, player));
    }

    public boolean interact(ScreenClick click) {
        if (processor.isEmpty())
            return super.interact(click);
        if (!(click.menu().blockEntity instanceof ScreenHost host)
            || !serverEnabled(host, click.player()))
            return true;
        var clicks = common().clicks();
        var normal = click.button() == 0 ? clicks.click() : clicks.rightClick();
        var shifted = click.button() == 0
            ? clicks.shiftClick()
            : clicks.shiftRightClick();
        var action = click.shift() ? shifted.or(() -> normal) : normal;
        action.ifPresent(id -> host.screenFeatures()
            .findProcessor(processor.get()).ifPresent(target -> {
                var result = target.invoke(id, click.player());
                result.message().ifPresent(message -> click.player()
                    .displayClientMessage(message, true));
            }));
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other) && other instanceof BoundButtonElement button
            && processor.equals(button.processor)
            && enabledWhen.equals(button.enabledWhen);
    }
    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), processor, enabledWhen);
    }
    @Override
    public String toString() {
        return super.toString() + "[processor=" + processor + ",enabled-when="
            + enabledWhen + "]";
    }
}
