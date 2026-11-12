package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.List;
import java.util.Set;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.modules.DataModules;
import zone.moddev.mc.lib.common.types.PartMetadata;

/**
 * Defines a new item module, a kind of item, from a {@code #loader mmdlib}
 * script, like a {@code data/<namespace>/item} file. Once built it can be used
 * like any other module: materials select it as a part (by its name, in script
 * materials and material files) and {@link ScriptItemBuilder} builds standalone
 * items with it.
 *
 * <p>
 * A module starts as a copy of the module it is based on: its Java factory,
 * factory data, template and companion entity, but not its aliases. Change
 * {@code factory} to use another Java implementation. Its tags are given to
 * every material's part, such as {@code copper_heavy_plate}, which gets
 * generated art from the template of the module it is based on, or else its
 * factory's.
 *
 * @docParam this new ItemModule("widget")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/definitions/ItemModule")
@ZenCodeType.Name("mods.mmdlib.ItemModule")
public final class ScriptItemModule extends ScriptItemProperties {
    private final ResourceLocation id;
    private final ResourceLocation baseId;
    private final DataModules.Item base;
    private ResourceLocation factory;

    /**
     * Starts module {@code name} (a path such as {@code "widget"}) as a copy of
     * {@code mmdlib:ingot}.
     *
     * @param name the module's path; its ID is {@code mmdlib:<name>}
     * @docParam name "widget"
     */
    @ZenCodeType.Constructor
    public ScriptItemModule(String name) {
        this(name, "mmdlib:ingot");
    }

    /**
     * Starts module {@code name} as a copy of module {@code basedOn}, such as
     * {@code mmdlib:sword}.
     *
     * @param name the module's path; its ID is {@code mmdlib:<name>}
     * @param basedOn the item module to copy
     * @docParam name "widget"
     * @docParam basedOn "mmdlib:sword"
     */
    @ZenCodeType.Constructor
    public ScriptItemModule(String name, String basedOn) {
        this(mmdlibId(name, "item module"), ResourceLocation.parse(basedOn));
    }

    /**
     * Starts module {@code name} as a copy of another item module, from a
     * reference.
     *
     * @param name the module's path; its ID is {@code mmdlib:<name>}
     * @param basedOn the item module to copy
     * @docParam name "widget"
     * @docParam basedOn new ItemModuleReference("mmdlib:sword")
     */
    @ZenCodeType.Constructor
    public ScriptItemModule(String name, ScriptItemModuleReference basedOn) {
        this(name, basedOn.getId());
    }

    /**
     * A reference to this module, for builders that take a module.
     *
     * @return the reference
     */
    @ZenCodeType.Getter("reference")
    public ScriptItemModuleReference getReference() {
        return new ScriptItemModuleReference(id.toString());
    }

    /**
     * Copies this module's current configuration to a new module; this one need
     * not be built yet. Later changes to either do not affect the other.
     *
     * @param name the new module's path
     * @return the copy
     * @docParam name "large_widget"
     */
    @ZenCodeType.Method
    public ScriptItemModule copy(String name) {
        var targetId = mmdlibId(name, "item module");
        if (targetId.equals(id))
            throw new IllegalArgumentException(
                "Copy needs a different module ID");
        var snapshot = new DataModules.Item(factory, base.requiredMod(),
            factoryData(), template(), base.part());
        var target = new ScriptItemModule(targetId, baseId, snapshot);
        target.removeTags("all");
        target.addTags(
            tags().stream().map(Object::toString).toArray(String[]::new));
        target.resourceOptions = resourceOptions;
        copyHooksTo(target);
        return target;
    }

    private ScriptItemModule(ResourceLocation id, ResourceLocation baseId) {
        this(id, baseId, ScriptDefinitions.catalog().itemModule(baseId));
    }

    private ScriptItemModule(ResourceLocation id, ResourceLocation baseId,
        DataModules.Item base) {
        super(base.template(), base.factoryData(),
            ScriptDefinitions.itemTags().getOrDefault(baseId, Set.of()));
        this.id = id;
        resourceOptions = ScriptDefinitions.resourceOptions("item_module",
            baseId);
        this.baseId = baseId;
        this.base = base;
        factory = base.factory();
    }

    /**
     * The module's ID, such as {@code mmdlib:widget}.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("name")
    public String getName() {
        return id.toString();
    }

    /**
     * The Java implementation that builds the items, such as {@code
     * mmdlib:sword}.
     *
     * @return the implementation's ID
     */
    @ZenCodeType.Getter("factory")
    public String getFactory() {
        return factory.toString();
    }

    /**
     * Sets the Java implementation that builds the items.
     *
     * @param factory the implementation's ID
     * @docParam factory "mmdlib:sword"
     */
    @ZenCodeType.Setter("factory")
    public void setFactory(String factory) {
        var id = ResourceLocation.parse(factory);
        ScriptDefinitions.catalog().itemImplementation(id);
        this.factory = id;
    }

    /**
     * Registers the module. Script lines after this, and every material file,
     * can use it.
     *
     * @throws IllegalArgumentException if a value is invalid or the name is
     *             taken
     */
    @Override
    ResourceLocation definitionId() {
        return id;
    }

    /**
     * Finishes the module and registers it, so later script lines and material
     * files can use it. Call it once.
     */
    @ZenCodeType.Method
    @Override
    public void build() {
        var part = base.part();
        ScriptDefinitions.catalog().registerItemModule(id,
            new DataModules.Item(factory, base.requiredMod(), factoryData(),
                template(), new PartMetadata(List.of(), part.entityFactory(),
                    part.entityData(), part.blockEntity())));
        ScriptDefinitions.resources("item_module", id, resourceOptions);
        ScriptDefinitions.module(id, baseId, tags());
    }
}
