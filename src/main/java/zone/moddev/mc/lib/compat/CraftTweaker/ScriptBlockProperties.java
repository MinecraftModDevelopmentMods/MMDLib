package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.codecs.BlockTemplate;
import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MMDBlock;

/**
 * What a script block and a script block module share: the block's properties and whether it has
 * an item, besides the tags, events and factory data of every definition. Values start from the
 * module the builder is based on.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.BlockProperties")
public abstract class ScriptBlockProperties extends ScriptDefinitionProperties {
  private final BlockTemplate base;
  private float hardness;
  private float blastResistance;
  private float friction;
  private float speedFactor;
  private float jumpFactor;
  private int lightLevel;
  private boolean requiresCorrectTool;
  private boolean collision;
  private boolean occlusion;
  private boolean randomTicks;
  private boolean replaceable;
  private String sound;
  private String mapColor;
  private boolean hasItem;

  ScriptBlockProperties(BlockTemplate base, FactoryData factoryData) {
    super(base.tags(), base.events(), factoryData);
    this.base = base;
    var p = base.properties();
    hardness = p.hardness();
    blastResistance = p.blastResistance();
    friction = p.friction();
    speedFactor = p.speedFactor();
    jumpFactor = p.jumpFactor();
    lightLevel = p.lightLevel();
    requiresCorrectTool = p.requiresCorrectTool();
    collision = p.collision();
    occlusion = p.occlusion();
    randomTicks = p.randomTicks();
    replaceable = p.replaceable();
    sound = p.sound();
    mapColor = p.mapColor();
    hasItem = base.hasItem();
  }

  /** Time to mine; -1 makes the block unbreakable. */
  @ZenCodeType.Getter("hardness")
  public float getHardness() {
    return hardness;
  }

  @ZenCodeType.Setter("hardness")
  public void setHardness(float hardness) {
    this.hardness = hardness;
  }

  /** Resistance to explosions. */
  @ZenCodeType.Getter("blastResistance")
  public float getBlastResistance() {
    return blastResistance;
  }

  @ZenCodeType.Setter("blastResistance")
  public void setBlastResistance(float blastResistance) {
    this.blastResistance = blastResistance;
  }

  /** Slipperiness; ice is 0.98, most blocks 0.6. */
  @ZenCodeType.Getter("friction")
  public float getFriction() {
    return friction;
  }

  @ZenCodeType.Setter("friction")
  public void setFriction(float friction) {
    this.friction = friction;
  }

  /** Walking speed multiplier; soul sand is 0.4. */
  @ZenCodeType.Getter("speedFactor")
  public float getSpeedFactor() {
    return speedFactor;
  }

  @ZenCodeType.Setter("speedFactor")
  public void setSpeedFactor(float speedFactor) {
    this.speedFactor = speedFactor;
  }

  /** Jump height multiplier; honey is 0.5. */
  @ZenCodeType.Getter("jumpFactor")
  public float getJumpFactor() {
    return jumpFactor;
  }

  @ZenCodeType.Setter("jumpFactor")
  public void setJumpFactor(float jumpFactor) {
    this.jumpFactor = jumpFactor;
  }

  /** Light emitted, 0 to 15. */
  @ZenCodeType.Getter("lightLevel")
  public int getLightLevel() {
    return lightLevel;
  }

  @ZenCodeType.Setter("lightLevel")
  public void setLightLevel(int lightLevel) {
    this.lightLevel = lightLevel;
  }

  /** Whether the block only drops when mined with the right tool. */
  @ZenCodeType.Getter("requiresCorrectTool")
  public boolean getRequiresCorrectTool() {
    return requiresCorrectTool;
  }

  @ZenCodeType.Setter("requiresCorrectTool")
  public void setRequiresCorrectTool(boolean requiresCorrectTool) {
    this.requiresCorrectTool = requiresCorrectTool;
  }

  /** Whether entities collide with the block. */
  @ZenCodeType.Getter("collision")
  public boolean getCollision() {
    return collision;
  }

  @ZenCodeType.Setter("collision")
  public void setCollision(boolean collision) {
    this.collision = collision;
  }

  /** Whether the block hides the faces of its neighbors; false for glass-like blocks. */
  @ZenCodeType.Getter("occlusion")
  public boolean getOcclusion() {
    return occlusion;
  }

  @ZenCodeType.Setter("occlusion")
  public void setOcclusion(boolean occlusion) {
    this.occlusion = occlusion;
  }

  /** Whether the block receives random ticks. */
  @ZenCodeType.Getter("randomTicks")
  public boolean getRandomTicks() {
    return randomTicks;
  }

  @ZenCodeType.Setter("randomTicks")
  public void setRandomTicks(boolean randomTicks) {
    this.randomTicks = randomTicks;
  }

  /** Whether placing a block replaces it, like grass. */
  @ZenCodeType.Getter("replaceable")
  public boolean getReplaceable() {
    return replaceable;
  }

  @ZenCodeType.Setter("replaceable")
  public void setReplaceable(boolean replaceable) {
    this.replaceable = replaceable;
  }

  /** The sound type, such as {@code stone}, {@code wood} or {@code metal}. */
  @ZenCodeType.Getter("sound")
  public String getSound() {
    return sound;
  }

  @ZenCodeType.Setter("sound")
  public void setSound(String sound) {
    this.sound = sound;
  }

  /** The map color, such as {@code stone}, {@code wood} or {@code metal}. */
  @ZenCodeType.Getter("mapColor")
  public String getMapColor() {
    return mapColor;
  }

  @ZenCodeType.Setter("mapColor")
  public void setMapColor(String mapColor) {
    this.mapColor = mapColor;
  }

  /** Whether the block has an item form. */
  @ZenCodeType.Getter("hasItem")
  public boolean getHasItem() {
    return hasItem;
  }

  @ZenCodeType.Setter("hasItem")
  public void setHasItem(boolean hasItem) {
    this.hasItem = hasItem;
  }

  /** The template these values describe. */
  BlockTemplate template() {
    return new BlockTemplate(
        base.name(),
        base.parent(),
        base.type(),
        new MMDBlock.Properties(
            hardness,
            blastResistance,
            friction,
            speedFactor,
            jumpFactor,
            lightLevel,
            requiresCorrectTool,
            collision,
            occlusion,
            randomTicks,
            replaceable,
            sound,
            mapColor),
        tags(),
        events(),
        hasItem,
        hasItem ? base.item() : Optional.empty());
  }
}
