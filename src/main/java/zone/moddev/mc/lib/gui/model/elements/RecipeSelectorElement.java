package zone.moddev.mc.lib.gui.model.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;

/** A ghost output selector with a paged, server-authorized recipe chooser. */
public class RecipeSelectorElement implements SynchronizedElement {
    public static final int PAGE_SIZE = 3;
    public static final ElementSync<CompoundTag> VIEW = ElementSync
        .field(ResourceLocation.parse("mmdlib:recipe_selector"),
            ByteBufCodecs.COMPOUND_TAG.<RegistryFriendlyByteBuf>cast(), 32768,
            element -> new CompoundTag())
        .sample((source, previous) -> ((RecipeSelectorElement) source.element())
            .sample((ScreenHost) source.machine(), source.machine().getLevel(),
                previous))
        .build();

    public record Request(String operation, String id, int page) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec
            .of((buffer, request) -> {
                buffer.writeUtf(request.operation(), 16);
                buffer.writeUtf(request.id(), 256);
                buffer.writeVarInt(request.page());
            }, buffer -> new Request(buffer.readUtf(16), buffer.readUtf(256),
                buffer.readVarInt()));
    }

    public static final ElementAction<Request> EDIT = new ElementAction<>(
        ResourceLocation.parse("mmdlib:select_recipe"), Request.CODEC, 1024,
        (context, request) -> {
            var element = (RecipeSelectorElement) context.element();
            var host = (ScreenHost) context.menu().blockEntity;
            var processor = element.processor(host);
            var level = context.menu().blockEntity.getLevel();
            var state = context.menu().elementStates().get(element, VIEW);
            if (!processor.selectionEnabled() || request.page() < 0
                || request.page() > 1_000_000)
                return false;
            switch (request.operation()) {
                case "select" -> {
                    var id = request.id().isEmpty()
                        ? null
                        : ResourceLocation.tryParse(request.id());
                    if ((!request.id().isEmpty() && id == null)
                        || !processor.selectRecipe(level, id))
                        return false;
                    state.putBoolean("open", false);
                    host.screenFeatures().markChanged();
                    host.screenFeatures().markSync();
                }
                case "browse" -> {
                    state.putBoolean("open", true);
                    state.putString("filter", "");
                    state.putInt("page", request.page());
                }
                case "page" -> state.putInt("page", request.page());
                case "close" -> state.putBoolean("open", false);
                case "item" -> {
                    var item = ResourceLocation.tryParse(request.id());
                    if (item == null
                        || !BuiltInRegistries.ITEM.containsKey(item))
                        return false;
                    var matches = processor.recipeChoices(level).stream()
                        .filter(holder -> produces(holder, item)).toList();
                    if (matches.isEmpty())
                        return false;
                    if (matches.size() == 1) {
                        if (!processor.selectRecipe(level,
                            matches.getFirst().id()))
                            return false;
                        host.screenFeatures().markChanged();
                        host.screenFeatures().markSync();
                        state.putBoolean("open", false);
                    } else {
                        state.putBoolean("open", true);
                        state.putString("filter", item.toString());
                        state.putInt("page", 0);
                    }
                }
                default -> {
                    return false;
                }
            }
            state.remove("stamp");
            context.menu().elementStates().set(element, VIEW,
                element.sample(host, level, state));
            return true;
        });

    private final Common common;
    private final String processorKey;

    public RecipeSelectorElement(Common common, String processorKey) {
        this.common = Objects.requireNonNull(common);
        this.processorKey = Objects.requireNonNull(processorKey);
    }

    public Common common() {
        return common;
    }
    public int[] measure(TextMeasure text) {
        return new int[]{18, 18};
    }
    public List<ElementSync<?>> synchronizedValues() {
        return List.of(VIEW);
    }
    public List<ElementAction<?>> actions() {
        return List.of(EDIT);
    }
    public void validate() {
        if (processorKey.isBlank() || common.value().isPresent()
            || common.max().isPresent() || common.clicks().any()
            || common.interaction().isPresent())
            throw new IllegalArgumentException(
                "recipe-selector requires processor-key and owns its state/actions");
    }
    public void validateBindings(Map<String, Set<String>> bindings) {
        if (!bindings.getOrDefault("processors", Set.of())
            .contains(processorKey))
            throw new IllegalArgumentException(
                "Unknown processor-key: " + processorKey);
    }
    public void validateHost(ScreenHost host) {
        if (!processor(host).selectionEnabled())
            throw new IllegalArgumentException(
                "recipe-selector requires a selectable processor: "
                    + processorKey);
    }
    public void validateHostType(Class<?> type) {
        if (!ScreenHost.class.isAssignableFrom(type))
            throw new IllegalArgumentException(
                "recipe-selector requires a ScreenHost");
    }
    private RecipeProcessor processor(ScreenHost host) {
        if (host.screenFeatures() == null)
            throw new IllegalArgumentException(
                "recipe-selector requires a recipe processor");
        return host.screenFeatures().processor(processorKey)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown processor-key: " + processorKey));
    }

    private CompoundTag sample(ScreenHost host, Level level,
        CompoundTag previous) {
        var processor = processor(host);
        String selected = processor.selectedRecipe().map(Object::toString)
            .orElse("");
        String active = processor.activeRecipe().map(Object::toString)
            .orElse("");
        long revision = processor.recipeRevision(level);
        long now = level.getGameTime();
        if (previous.contains("stamp")
            && previous.getLong("revision") == revision
            && previous.getString("selected").equals(selected)
            && previous.getString("active").equals(active)
            && previous.getString("status").equals(processor.status().name())
            && now - previous.getLong("stamp") < 20)
            return previous;
        var next = previous.copy();
        next.putLong("stamp", now);
        next.putLong("revision", revision);
        next.putString("selected", selected);
        next.putString("active", active);
        next.putString("status", processor.status().name());
        next.put("chosen", selected.isEmpty()
            ? new CompoundTag()
            : processor.selectedEntry(level, ResourceLocation.parse(selected))
                .map(holder -> row(processor, level, holder))
                .orElseGet(CompoundTag::new));
        next.put("working",
            active.isEmpty()
                ? new CompoundTag()
                : processor.selectedEntry(level, ResourceLocation.parse(active))
                    .map(holder -> row(processor, level, holder))
                    .orElseGet(CompoundTag::new));
        if (next.getBoolean("open")) {
            var filter = ResourceLocation.tryParse(next.getString("filter"));
            var choices = processor.recipeChoices(level).stream()
                .filter(holder -> filter == null || produces(holder, filter))
                .toList();
            int pages = Math.max(1,
                (choices.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = Math.clamp(next.getInt("page"), 0, pages - 1);
            next.putInt("pages", pages);
            next.putInt("page", page);
            var rows = new ListTag();
            choices.stream().skip((long) page * PAGE_SIZE).limit(PAGE_SIZE)
                .forEach(holder -> rows.add(row(processor, level, holder)));
            next.put("rows", rows);
        } else {
            next.remove("rows");
        }
        return next;
    }

    private static boolean produces(RecipeHolder<MachineRecipeEntry> holder,
        ResourceLocation item) {
        return holder.value().recipe().outputs().values().stream()
            .flatMap(List::stream).anyMatch(
                product -> product instanceof MachineProduct.ItemProduct output
                    && BuiltInRegistries.ITEM.getKey(output.stack().getItem())
                        .equals(item));
    }

    private static CompoundTag row(RecipeProcessor processor, Level level,
        RecipeHolder<MachineRecipeEntry> holder) {
        var result = new CompoundTag();
        result.putString("id", holder.id().toString());
        var recipe = holder.value().recipe();
        var icon = recipe.outputs().values().stream().flatMap(List::stream)
            .filter(MachineProduct.ItemProduct.class::isInstance)
            .map(MachineProduct.ItemProduct.class::cast)
            .map(MachineProduct.ItemProduct::stack).findFirst()
            .orElse(ItemStack.EMPTY);
        if (!icon.isEmpty())
            result.put("icon", icon.save(level.registryAccess()));
        var match = processor.recipeAvailability(holder.value());
        result.putString("reason", match.reason().name());
        result.putString("group", match.group() == null ? "" : match.group());
        result.putInt("time", recipe.time());
        long energy = recipe.inputs().values().stream().flatMap(List::stream)
            .filter(MachineIngredient.EnergyPart.class::isInstance)
            .mapToLong(MachineIngredient::amount).sum();
        var costs = costs(recipe);
        result.putLong("energy", energy);
        var details = new ListTag();
        costs.stream().limit(32)
            .forEach(cost -> details.add(StringTag.valueOf(cost)));
        result.put("costs", details);
        return result;
    }

    static List<String> costs(
        zone.moddev.mc.lib.machine.recipe.MachineRecipe recipe) {
        var costs = new ArrayList<String>();
        for (var group : recipe.inputs().entrySet()) {
            for (var input : group.getValue()) {
                if (input instanceof MachineIngredient.EnergyPart) {
                    continue;
                } else if (input instanceof MachineIngredient.FluidPart fluid) {
                    if (!fluid.consumed()) {
                        costs.add(group.getKey() + ": " + fluid.amount()
                            + " mB required, 0 mB consumed");
                        continue;
                    }
                    // A return cannot be subtracted separately from several
                    // input claims.
                    // Describe complex recipes literally instead of inventing a
                    // net cost.
                    if (group.getValue().size() != 1
                        || fluid.fluids().size() != 1) {
                        costs.add(group.getKey() + ": " + fluid.amount()
                            + " mB required");
                        continue;
                    }
                    long returned = recipe.outputs()
                        .getOrDefault(group.getKey(), List.of()).stream()
                        .filter(
                            product -> product instanceof MachineProduct.FluidProduct output
                                && output.chance() == 1
                                && fluid.fluids().size() == 1
                                && fluid.fluids()
                                    .contains(output.fluid().fluid()))
                        .mapToLong(
                            product -> ((MachineProduct.FluidProduct) product)
                                .fluid().amount())
                        .sum();
                    costs.add(group.getKey() + ": " + fluid.amount()
                        + " mB required, "
                        + Math.max(0, fluid.amount() - returned)
                        + " mB consumed");
                } else {
                    costs.add(group.getKey() + ": " + input.amount()
                        + " items required");
                }
            }
        }
        return List.copyOf(costs);
    }

    @Override
    public boolean equals(Object other) {
        return other != null && getClass() == other.getClass()
            && common.equals(((RecipeSelectorElement) other).common)
            && processorKey
                .equals(((RecipeSelectorElement) other).processorKey);
    }
    @Override
    public int hashCode() {
        return Objects.hash(common, processorKey);
    }
    @Override
    public String toString() {
        return "RecipeSelectorElement[" + common + ", " + processorKey + "]";
    }
}
