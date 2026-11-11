package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.types.CreativeTabDefinition;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.StandaloneBlock;
import zone.moddev.mc.lib.common.types.StandaloneItem;

/**
 * Runs the {@code #loader mmdlib} scripts once, as MMDLib's definition source,
 * and collects what they build. Block and item modules are registered on the
 * catalog as soon as a script builds them, so later script lines and every
 * material file can use them. Building is only accepted while that run is in
 * progress: registries are filled from its result, so anything built later
 * could never be registered.
 */
final class ScriptDefinitions {
    /** The loader scripts name with {@code #loader mmdlib}. */
    static final String LOADER = MMDLib.MODID;

    /** Identifies MMDLib's startup run to CraftTweaker. */
    static final ResourceLocation LOAD_SOURCE = ResourceLocation
        .fromNamespaceAndPath(MMDLib.MODID, "item_generation");

    private static final Map<ResourceLocation, MMDMaterial> materials = new LinkedHashMap<>();
    private static final Map<ResourceLocation, StandaloneBlock> blocks = new LinkedHashMap<>();
    private static final Map<ResourceLocation, StandaloneItem> items = new LinkedHashMap<>();
    private static final Map<ResourceLocation, CreativeTabDefinition> tabs = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Set<ResourceLocation>> itemTags = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> basedOn = new LinkedHashMap<>();
    private static volatile Set<ResourceLocation> materialIds = Set.of();
    private static volatile Set<ResourceLocation> blockIds = Set.of();
    private static volatile Set<ResourceLocation> itemIds = Set.of();
    private static volatile Map<ResourceLocation, Set<ResourceLocation>> builtItemTags = Map
        .of();
    private static volatile Map<ResourceLocation, ResourceLocation> builtBasedOn = Map
        .of();
    private static final Map<String, ScriptResourceOptions> resources = new LinkedHashMap<>();
    private static volatile Map<String, ScriptResourceOptions> builtResources = Map
        .of();
    private static MaterialExtensions catalog;

    static ScriptResourceOptions resourceOptions(String kind,
        ResourceLocation id) {
        return resourceOptions().getOrDefault(
            ScriptResourceOptions.key(kind, id), ScriptResourceOptions.DEFAULT);
    }

    static Map<String, ScriptResourceOptions> resourceOptions() {
        return catalog == null ? builtResources : Map.copyOf(resources);
    }

    static void resources(String kind, ResourceLocation id,
        ScriptResourceOptions options) {
        catalog();
        resources.put(ScriptResourceOptions.key(kind, id), options);
    }

    private ScriptDefinitions() {
    }

    /** Runs the scripts against {@code api} and returns what they built. */
    static synchronized MaterialExtensions.Definitions load(
        MaterialExtensions api) {
        var logger = CraftTweakerAPI.getLogger(MMDLib.MODID);
        logger.info("Running MMDLib definition scripts");
        materials.clear();
        blocks.clear();
        items.clear();
        tabs.clear();
        itemTags.clear();
        basedOn.clear();
        resources.clear();
        catalog = api;
        try {
            CraftTweakerAPI.getScriptRunManager()
                .createScriptRun(new ScriptRunConfiguration(LOADER, LOAD_SOURCE,
                    ScriptRunConfiguration.RunKind.EXECUTE))
                .execute();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable ex) {
            // execute() declares Throwable. CraftTweaker reports script errors
            // itself; keep
            // whatever
            // was built before the failure.
            logger.error("Error running MMDLib definition scripts", ex);
        } finally {
            catalog = null;
        }
        logger.info("MMDLib scripts built {} materials, {} blocks and {} items",
            materials.size(), blocks.size(), items.size());
        materialIds = Set.copyOf(materials.keySet());
        blockIds = Set.copyOf(blocks.keySet());
        itemIds = Set.copyOf(items.keySet());
        builtItemTags = Map.copyOf(itemTags);
        builtBasedOn = Map.copyOf(basedOn);
        builtResources = Map.copyOf(resources);
        var result = new MaterialExtensions.Definitions(materials.values(),
            blocks, items, tabs);
        materials.clear();
        blocks.clear();
        items.clear();
        tabs.clear();
        return result;
    }

    /**
     * The catalog being loaded.
     *
     * @throws IllegalStateException
     *             outside the startup run
     */
    static synchronized MaterialExtensions catalog() {
        if (catalog == null)
            throw new IllegalStateException(
                "MMDLib definitions can only be built from #loader " + LOADER
                    + " scripts at startup");
        return catalog;
    }

    /**
     * Records a built material.
     *
     * @throws IllegalStateException
     *             outside the startup run, or if the name was already built
     */
    static synchronized void add(MMDMaterial material) {
        catalog();
        var id = material.name().orElseThrow();
        if (materials.putIfAbsent(id, material) != null)
            throw new IllegalStateException(
                "Material " + id + " is already built");
    }

    /**
     * Records a built standalone block.
     *
     * @throws IllegalStateException
     *             outside the startup run, or if the ID was already built
     */
    /**
     * Queues a built creative tab; a tab declared in a file is replaced by it.
     *
     * @throws IllegalStateException
     *             if a script already built {@code id}
     */
    static synchronized void add(ResourceLocation id,
        CreativeTabDefinition tab) {
        catalog();
        if (tabs.putIfAbsent(id, tab) != null)
            throw new IllegalStateException(
                "Creative tab " + id + " is already built");
    }

    /** The creative tabs declared in {@code mmdlib/creative_tab/} files. */
    static Map<ResourceLocation, CreativeTabDefinition> creativeTabFiles() {
        return catalog().creativeTabFiles();
    }

    static synchronized void add(ResourceLocation id, StandaloneBlock block) {
        catalog();
        if (blocks.putIfAbsent(id, block) != null)
            throw new IllegalStateException(
                "Block " + id + " is already built");
    }

    /**
     * Records a built standalone item and its item tags.
     *
     * @throws IllegalStateException
     *             outside the startup run, or if the ID was already built
     */
    static synchronized void add(ResourceLocation id, StandaloneItem item,
        Set<ResourceLocation> tags) {
        catalog();
        if (items.putIfAbsent(id, item) != null)
            throw new IllegalStateException("Item " + id + " is already built");
        itemTags.put(id, Set.copyOf(tags));
    }

    /**
     * Records a script module: the module it copies, whose templates its parts
     * use, and for item modules the item tags its parts get.
     */
    static synchronized void module(ResourceLocation id, ResourceLocation base,
        Set<ResourceLocation> tags) {
        catalog();
        basedOn.put(id, base);
        if (!tags.isEmpty())
            itemTags.put(id, Set.copyOf(tags));
    }

    /** Item tags of script items and item modules, by item or module ID. */
    static Map<ResourceLocation, Set<ResourceLocation>> itemTags() {
        return catalog == null ? builtItemTags : Map.copyOf(itemTags);
    }

    /** The module each script module was copied from. */
    static Map<ResourceLocation, ResourceLocation> basedOn() {
        return builtBasedOn;
    }

    /** The loaded results of script items; empty before definitions load. */
    static List<MaterialFactory.Result> itemResults() {
        var loaded = MMDLib.standaloneItems();
        return itemIds.stream().sorted().map(loaded::get)
            .filter(Objects::nonNull).toList();
    }

    /**
     * The loaded results of script materials; empty before definitions load.
     */
    static List<MaterialFactory.Result> materialResults() {
        var loaded = MMDLib.loadedMaterials();
        return materialIds.stream().sorted().map(loaded::get)
            .filter(Objects::nonNull).toList();
    }

    /** The loaded results of script blocks; empty before definitions load. */
    static List<MaterialFactory.Result> blockResults() {
        var loaded = MMDLib.standaloneBlocks();
        return blockIds.stream().sorted().map(loaded::get)
            .filter(Objects::nonNull).toList();
    }
}
