package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.builders.MaterialBuilder;
import zone.moddev.mc.lib.common.types.MaterialPart;
import zone.moddev.mc.lib.common.types.MaterialProperties;
import zone.moddev.mc.lib.common.types.MaterialRuntimeSettings;
import zone.moddev.mc.lib.common.types.MaterialSelection;
import zone.moddev.mc.lib.common.types.PartSelection;

/**
 * Builds an MMDLib material from a {@code #loader mmdlib} script. Plain values
 * are properties; collections have an add and a remove function, where the name
 * {@code "all"} means every entry. Nothing is registered until
 * {@link #build()}.
 *
 * <p>
 * A new builder matches a material file that sets nothing: every item and block
 * part, and a fluid. Content is registered as {@code mmdlib:<name>_<part>}.
 *
 * @docParam this new MaterialBuilder("bronze")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/definitions/MaterialBuilder")
@ZenCodeType.Name("mods.mmdlib.MaterialBuilder")
public final class ScriptMaterialBuilder {
    private static final String ALL = "all";

    private final ResourceLocation name;
    private final Set<MaterialPart> availableItems;
    private final Set<MaterialPart> availableBlocks;
    private final Set<MaterialPart> availableEntities;
    private final Set<MaterialPart> items;
    private final Set<MaterialPart> blocks;
    private final Set<ResourceLocation> harvestTags = new LinkedHashSet<>();
    private final Map<String, Double> special = new LinkedHashMap<>();
    private String displayName = "";
    private String type = MaterialProperties.DEFAULT.type();
    private int color = MaterialProperties.DEFAULT.color();
    private double hardness;
    private double strength;
    private double magic;
    private double damage;
    private boolean hasFluid;
    private ResourceLocation fluid;

    private java.util.Optional<ResourceLocation> repairItem = MaterialRuntimeSettings.DEFAULT
        .repairItem();
    private ResourceLocation incorrectBlocks = MaterialRuntimeSettings.DEFAULT
        .incorrectBlocks();
    private ResourceLocation baseBlock = MaterialRuntimeSettings.DEFAULT
        .baseBlock();
    private ResourceLocation blockSetType = MaterialRuntimeSettings.DEFAULT
        .blockSetType();
    private java.util.Optional<String> blockSound = MaterialRuntimeSettings.DEFAULT
        .blockSound();
    private java.util.Optional<String> mapColor = MaterialRuntimeSettings.DEFAULT
        .mapColor();

    /**
     * Starts a material. The name is a path such as {@code "copper"}; {@code
     * "mmdlib:copper"} is also accepted, but no other namespace.
     *
     * @param name the material's path, such as {@code "copper"}; its ID is
     *            {@code mmdlib:<name>}
     * @docParam name "bronze"
     */
    @ZenCodeType.Constructor
    public ScriptMaterialBuilder(String name) {
        var id = ResourceLocation
            .parse(name.contains(":") ? name : MMDLib.MODID + ":" + name);
        if (!id.getNamespace().equals(MMDLib.MODID))
            throw new IllegalArgumentException(
                "Script materials are registered in the " + MMDLib.MODID
                    + " namespace; use \"" + id.getPath() + "\" instead of \""
                    + name + "\"");
        this.name = id;
        var snapshot = ScriptDefinitions.catalog().snapshot();
        var parts = snapshot.parts();
        var defaults = MaterialSelection.defaults(snapshot);
        availableItems = parts.items();
        availableBlocks = parts.blocks();
        availableEntities = parts.entities();
        items = new LinkedHashSet<>(defaults.items());
        blocks = new LinkedHashSet<>(defaults.blocks());
        hasFluid = defaults.hasFluid();
        fluid = defaults.fluidModule();
    }

    /**
     * The material's ID, such as {@code mmdlib:copper}.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("name")
    public String getName() {
        return name.toString();
    }

    /**
     * The classification, such as {@code metal} or {@code gem}; default {@code
     * generic}.
     *
     * @return the classification
     */
    @ZenCodeType.Getter("type")
    public String getType() {
        return type;
    }

    /**
     * Sets the classification, such as {@code metal} or {@code gem}.
     *
     * @param type the classification
     * @docParam type "metal"
     */
    @ZenCodeType.Setter("type")
    public void setType(String type) {
        this.type = type;
    }

    /**
     * The ARGB color used to tint textures; default opaque white.
     *
     * @return the color, as ARGB
     */
    @ZenCodeType.Getter("color")
    public int getColor() {
        return color;
    }

    /**
     * Sets the ARGB color used to tint textures.
     *
     * @param color the color, as ARGB
     * @docParam color 0xFFCD7F32
     */
    @ZenCodeType.Setter("color")
    public void setColor(int color) {
        this.color = color;
    }

    /**
     * Mining speed, armor toughness and related values; default 0.
     *
     * @return the hardness
     */
    @ZenCodeType.Getter("hardness")
    public double getHardness() {
        return hardness;
    }

    /**
     * Sets the hardness, which drives mining speed, armor toughness and related
     * values.
     *
     * @param hardness the hardness
     * @docParam hardness 6
     */
    @ZenCodeType.Setter("hardness")
    public void setHardness(double hardness) {
        this.hardness = hardness;
    }

    /**
     * Tool and armor durability; default 0.
     *
     * @return the strength
     */
    @ZenCodeType.Getter("strength")
    public double getStrength() {
        return strength;
    }

    /**
     * Sets the strength, which drives tool and armor durability.
     *
     * @param strength the strength
     * @docParam strength 5
     */
    @ZenCodeType.Setter("strength")
    public void setStrength(double strength) {
        this.strength = strength;
    }

    /**
     * Magic affinity, which drives enchantability; default 0.
     *
     * @return the magic affinity
     */
    @ZenCodeType.Getter("magic")
    public double getMagic() {
        return magic;
    }

    /**
     * Sets the magic affinity, which drives enchantability.
     *
     * @param magic the magic affinity
     * @docParam magic 4
     */
    @ZenCodeType.Setter("magic")
    public void setMagic(double magic) {
        this.magic = magic;
    }

    /**
     * The tool tier's base attack-damage bonus; default 0.
     *
     * @return the bonus
     */
    @ZenCodeType.Getter("damage")
    public double getDamage() {
        return damage;
    }

    /**
     * Sets the tool tier's base attack-damage bonus.
     *
     * @param damage the bonus
     * @docParam damage 2
     */
    @ZenCodeType.Setter("damage")
    public void setDamage(double damage) {
        this.damage = damage;
    }

    /**
     * Whether the material gets a fluid; default true.
     *
     * @return true if it gets a fluid
     */
    @ZenCodeType.Getter("hasFluid")
    public boolean getHasFluid() {
        return hasFluid;
    }

    /**
     * Sets whether the material gets a fluid.
     *
     * @param hasFluid true for a fluid
     * @docParam hasFluid false
     */
    @ZenCodeType.Setter("hasFluid")
    public void setHasFluid(boolean hasFluid) {
        this.hasFluid = hasFluid;
    }

    /**
     * The fluid module that builds the fluid; default {@code mmdlib:molten}.
     *
     * @return the fluid module's ID
     */
    @ZenCodeType.Getter("fluid")
    public String getFluid() {
        return fluid.toString();
    }

    /**
     * Sets the fluid module that builds the material's fluid, such as {@code
     * mmdlib:molten} or {@code mmdlib:fluid}.
     *
     * @param fluid the fluid module's ID
     * @docParam fluid "mmdlib:fluid"
     */
    @ZenCodeType.Setter("fluid")
    public void setFluid(String fluid) {
        this.fluid = ResourceLocation.parse(fluid);
    }

    /**
     * The selected item parts, by short ID.
     *
     * @return the item part names
     */
    @ZenCodeType.Getter("items")
    public String[] getItems() {
        return ids(items);
    }

    /**
     * Adds item parts by name, such as {@code "ingot"}; {@code "all"} adds
     * every item part.
     *
     * @param names the item part names, or {@code "all"}
     * @return this builder
     * @docParam names "ingot", "nugget"
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder addItems(String... names) {
        items.addAll(resolve(names, availableItems, "item"));
        return this;
    }

    /**
     * Removes item parts by name; {@code "all"} removes every item part.
     *
     * @param names the item part names, or {@code "all"}
     * @return this builder
     * @docParam names "all"
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder removeItems(String... names) {
        items.removeAll(resolve(names, availableItems, "item"));
        return this;
    }

    /**
     * The selected block parts, by short ID.
     *
     * @return the block part names
     */
    @ZenCodeType.Getter("blocks")
    public String[] getBlocks() {
        return ids(blocks);
    }

    /**
     * Adds block parts by name, such as {@code "block"}; {@code "all"} adds
     * every block part.
     *
     * @param names the block part names, or {@code "all"}
     * @return this builder
     * @docParam names "block"
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder addBlocks(String... names) {
        blocks.addAll(resolve(names, availableBlocks, "block"));
        return this;
    }

    /**
     * Removes block parts by name; {@code "all"} removes every block part.
     *
     * @param names the block part names, or {@code "all"}
     * @return this builder
     * @docParam names "all"
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder removeBlocks(String... names) {
        blocks.removeAll(resolve(names, availableBlocks, "block"));
        return this;
    }

    /**
     * The harvest tags, such as {@code minecraft:needs_iron_tool}.
     *
     * @return the tag IDs
     */
    @ZenCodeType.Getter("harvestTags")
    public String[] getHarvestTags() {
        return harvestTags.stream().map(ResourceLocation::toString)
            .toArray(String[]::new);
    }

    /**
     * Adds block tags the material's blocks are put in, such as {@code
     * "minecraft:needs_iron_tool"}.
     *
     * @param tags the block tag IDs
     * @return this builder
     * @docParam tags "minecraft:needs_iron_tool"
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder addHarvestTags(String... tags) {
        for (var tag : tags) {
            if (tag.equals(ALL))
                throw new IllegalArgumentException(
                    "\"all\" can only remove harvest tags, not add them");
            harvestTags.add(ResourceLocation.parse(tag));
        }
        return this;
    }

    /**
     * Removes harvest tags; {@code "all"} removes every tag.
     *
     * @param tags the block tag IDs, or {@code "all"}
     * @return this builder
     * @docParam tags "all"
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder removeHarvestTags(String... tags) {
        for (var tag : tags)
            if (tag.equals(ALL))
                harvestTags.clear();
            else
                harvestTags.remove(ResourceLocation.parse(tag));
        return this;
    }

    /**
     * Sets a special stat, such as {@code blast_resistance}, {@code
     * temperature} or {@code density}; an existing value is replaced.
     *
     * @param key the stat's name
     * @param value the value
     * @return this builder
     * @docParam key "blast_resistance"
     * @docParam value 12
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder addSpecial(String key, double value) {
        if (key.equals(ALL))
            throw new IllegalArgumentException(
                "\"all\" is not a special stat name");
        special.put(key, value);
        return this;
    }

    /**
     * Removes special stats by key; {@code "all"} removes every one.
     *
     * @param keys the stat names, or {@code "all"}
     * @return this builder
     * @docParam keys "all"
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder removeSpecial(String... keys) {
        for (var key : keys)
            if (key.equals(ALL))
                special.clear();
            else
                special.remove(key);
        return this;
    }

    /**
     * Finishes the material and queues it for registration. Call it once, from
     * a {@code #loader mmdlib} script; later changes to this builder have no
     * effect. It is an error if a value is invalid, such as a negative
     * hardness, or if the name was already built.
     */
    @ZenCodeType.Method
    public void build() {
        var material = MaterialBuilder.create().setName(name)
            .setProperties(
                new MaterialProperties(type, color, hardness, strength, magic,
                    damage, Set.copyOf(harvestTags), Map.copyOf(special)))
            // Projectile entities follow their items: a selected arrow always
            // gets its entity.
            .setSelection(new MaterialSelection(items, blocks,
                availableEntities, hasFluid, Set.of(), Set.of(), fluid))
            // Generated textures are colored already (see
            // ScriptMaterialResources), and armor
            // layers are generated under the material's own name.
            .setRuntime(runtime()).build();
        ScriptDefinitions.add(material);
        if (!displayName.isEmpty())
            ScriptDefinitions.resources("material", name,
                ScriptResourceOptions.DEFAULT.named(displayName));
    }

    /**
     * The item that repairs the material's tools and armor; empty for the
     * normal choice.
     *
     * @return the item's ID, or an empty string
     */
    @ZenCodeType.Getter("repairItem")
    public String getRepairItem() {
        return repairItem.map(Object::toString).orElse("");
    }

    /**
     * Sets the item that repairs the material's tools and armor; empty restores
     * the normal choice.
     *
     * @param value the item's ID
     * @docParam value "minecraft:copper_ingot"
     */
    @ZenCodeType.Setter("repairItem")
    public void setRepairItem(String value) {
        repairItem = value.isEmpty()
            ? java.util.Optional.empty()
            : java.util.Optional.of(ResourceLocation.parse(value));
    }

    /**
     * The block tag the material's tools cannot harvest.
     *
     * @return the block tag's ID
     */
    @ZenCodeType.Getter("incorrectBlocks")
    public String getIncorrectBlocks() {
        return incorrectBlocks.toString();
    }

    /**
     * Sets the block tag the material's tools cannot harvest; default {@code
     * minecraft:incorrect_for_iron_tool}.
     *
     * @param value the block tag's ID
     * @docParam value "minecraft:incorrect_for_stone_tool"
     */
    @ZenCodeType.Setter("incorrectBlocks")
    public void setIncorrectBlocks(String value) {
        incorrectBlocks = ResourceLocation.parse(value);
    }

    /**
     * The block parts such as stairs copy their behavior from.
     *
     * @return the block's ID
     */
    @ZenCodeType.Getter("baseBlock")
    public String getBaseBlock() {
        return baseBlock.toString();
    }

    /**
     * Sets the block parts such as stairs copy their behavior from; default
     * {@code minecraft:iron_block}.
     *
     * @param value the block's ID
     * @docParam value "minecraft:copper_block"
     */
    @ZenCodeType.Setter("baseBlock")
    public void setBaseBlock(String value) {
        baseBlock = ResourceLocation.parse(value);
    }

    /**
     * How the material's doors, buttons and pressure plates open and sound.
     *
     * @return the block set type's ID
     */
    @ZenCodeType.Getter("blockSetType")
    public String getBlockSetType() {
        return blockSetType.toString();
    }

    /**
     * Sets how the material's doors, buttons and pressure plates open and
     * sound; default {@code minecraft:iron}.
     *
     * @param value the block set type's ID
     * @docParam value "minecraft:copper"
     */
    @ZenCodeType.Setter("blockSetType")
    public void setBlockSetType(String value) {
        blockSetType = ResourceLocation.parse(value);
    }

    /**
     * The sound set of the material's blocks; empty keeps each module's.
     *
     * @return the sound set, or an empty string
     */
    @ZenCodeType.Getter("blockSound")
    public String getBlockSound() {
        return blockSound.map(Object::toString).orElse("");
    }

    /**
     * A vanilla sound set's name, such as {@code "metal"}, or a sound set's ID
     * from {@code mmdlib/sound_type/} or a {@code SoundTypeBuilder}; an unknown
     * ID fails at startup, naming the material.
     *
     * @param value a vanilla sound set's name or a sound set's ID, or an empty
     *            string
     * @docParam value "metal"
     */
    @ZenCodeType.Setter("blockSound")
    public void setBlockSound(String value) {
        if (value.indexOf(':') >= 0)
            ResourceLocation.parse(value);
        else if (!value.isEmpty()) {
            try {
                net.minecraft.world.level.block.SoundType.class
                    .getField(value.toUpperCase(java.util.Locale.ROOT));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalArgumentException(
                    "Unknown blockSound: " + value, ex);
            }
        }
        blockSound = value.isEmpty()
            ? java.util.Optional.empty()
            : java.util.Optional.of(value);
    }

    /**
     * The map color of the material's blocks; empty keeps each module's.
     *
     * @return the map color's name, or an empty string
     */
    @ZenCodeType.Getter("mapColor")
    public String getMapColor() {
        return mapColor.map(Object::toString).orElse("");
    }

    /**
     * Sets the map color of the material's blocks, such as {@code gold}; empty
     * keeps each module's.
     *
     * @param value the map color's name
     * @docParam value "gold"
     */
    @ZenCodeType.Setter("mapColor")
    public void setMapColor(String value) {
        if (!value.isEmpty()) {
            try {
                net.minecraft.world.level.material.MapColor.class
                    .getField(value.toUpperCase(java.util.Locale.ROOT));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalArgumentException("Unknown mapColor: " + value,
                    ex);
            }
        }
        mapColor = value.isEmpty()
            ? java.util.Optional.empty()
            : java.util.Optional.of(value);
    }

    /**
     * Sets the tool durability special stat. It must be at least 1.
     *
     * @param value the durability
     * @return this builder
     * @docParam value 200
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder toolDurability(int value) {
        if (value < 1)
            throw new IllegalArgumentException(
                "Invalid toolDurability: " + value);
        return addSpecial("tool_durability", value);
    }

    /**
     * Sets the tool efficiency (mining speed) special stat. It must not be
     * negative.
     *
     * @param value the efficiency
     * @return this builder
     * @docParam value 8
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder toolEfficiency(double value) {
        if (!Double.isFinite(value) || value < 0 || value > Float.MAX_VALUE)
            throw new IllegalArgumentException(
                "Invalid toolEfficiency: " + value);
        return addSpecial("tool_efficiency", value);
    }

    /**
     * Sets the base attack damage special stat; it may be negative.
     *
     * @param value the damage
     * @return this builder
     * @docParam value 3
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder attackDamage(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE)
            throw new IllegalArgumentException(
                "Invalid attackDamage: " + value);
        return addSpecial("base_attack_damage", value);
    }

    /**
     * Sets the enchantability special stat. It must not be negative.
     *
     * @param value the enchantability
     * @return this builder
     * @docParam value 17
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder enchantability(int value) {
        if (value < 0)
            throw new IllegalArgumentException(
                "Invalid enchantability: " + value);
        return addSpecial("enchantability", value);
    }

    /**
     * Sets the item that repairs the material's tools and armor, from a
     * reference.
     *
     * @param item the item
     * @return this builder
     * @docParam item new ItemReference("minecraft:copper_ingot")
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder repairWith(ScriptItemReference item) {
        setRepairItem(item.getId());
        return this;
    }

    /**
     * Sets the block parts such as stairs copy their behavior from, from a
     * reference.
     *
     * @param block the block
     * @return this builder
     * @docParam block new BlockReference("minecraft:copper_block")
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder baseBlockFrom(ScriptBlockReference block) {
        setBaseBlock(block.getId());
        return this;
    }

    /**
     * Sets the block tag the material's tools cannot harvest, from a reference.
     *
     * @param tag the block tag
     * @return this builder
     * @docParam tag new BlockTagReference("minecraft:incorrect_for_stone_tool")
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder incorrectBlocksTag(
        ScriptBlockTagReference tag) {
        setIncorrectBlocks(tag.getId());
        return this;
    }

    /**
     * Adds a harvest tag, from a reference.
     *
     * @param tag the block tag
     * @return this builder
     * @docParam tag new BlockTagReference("minecraft:needs_iron_tool")
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder addHarvestTag(ScriptBlockTagReference tag) {
        return addHarvestTags(tag.getId());
    }

    /**
     * Selects an item part by its module, from a reference.
     *
     * @param module the item module
     * @return this builder
     * @docParam module new ItemModuleReference("mmdlib:gear")
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder addItemModule(
        ScriptItemModuleReference module) {
        return addItems(module.getId());
    }

    /**
     * Selects a block part by its module, from a reference.
     *
     * @param module the block module
     * @return this builder
     * @docParam module new BlockModuleReference("mmdlib:stairs")
     */
    @ZenCodeType.Method
    public ScriptMaterialBuilder addBlockModule(
        ScriptBlockModuleReference module) {
        return addBlocks(module.getId());
    }

    /**
     * The English name used for the material's generated parts; empty for one
     * made from its ID.
     *
     * @return the name, or an empty string
     */
    @ZenCodeType.Getter("displayName")
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the English name used for the material's generated parts, instead of
     * one made from its ID. It must not be blank.
     *
     * @param value the name
     * @docParam value "Bronze"
     */
    @ZenCodeType.Setter("displayName")
    public void setDisplayName(String value) {
        ScriptResourceOptions.DEFAULT.named(value);
        displayName = value;
    }

    private MaterialRuntimeSettings runtime() {
        return new MaterialRuntimeSettings(repairItem, incorrectBlocks,
            baseBlock, blockSetType, name, blockSound, mapColor, false, false);
    }

    private static Set<MaterialPart> resolve(String[] names,
        Set<MaterialPart> available, String section) {
        for (var name : names)
            if (name.startsWith("!"))
                throw new IllegalArgumentException("Use remove instead of \""
                    + name + "\" to leave out a " + section + " part");
        try {
            return PartSelection.parse(String.join(" ", names), available);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "Unknown " + section + " part: " + ex.getMessage(), ex);
        }
    }

    private static String[] ids(Set<MaterialPart> parts) {
        return parts.stream().map(MaterialPart::id).sorted()
            .toArray(String[]::new);
    }
}
