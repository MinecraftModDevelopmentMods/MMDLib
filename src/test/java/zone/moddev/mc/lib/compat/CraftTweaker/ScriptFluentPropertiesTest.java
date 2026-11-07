package zone.moddev.mc.lib.compat.CraftTweaker;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import zone.moddev.mc.lib.common.codecs.BlockTemplate;
import zone.moddev.mc.lib.common.codecs.ItemTemplate;
import zone.moddev.mc.lib.common.types.FactoryData;

final class ScriptFluentPropertiesTest {
  private static final class Block extends ScriptBlockProperties {
    Block() { super(BlockTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow(), FactoryData.EMPTY); }
    ResourceLocation definitionId() { return ResourceLocation.parse("mmdlib:test"); }
    ResourceLocation factoryId() { return ResourceLocation.parse("mmdlib:block"); }
    public void build() { validateBlock(); template().toBuilder(); }
  }
  private static final class Item extends ScriptItemProperties {
    Item() { super(ItemTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow(), FactoryData.EMPTY, java.util.Set.of()); }
    ResourceLocation definitionId() { return ResourceLocation.parse("mmdlib:test"); }
    public void build() { template().toBuilder().setName("mmdlib:test").forMaterial(ResourceLocation.parse("mmdlib:test")).build(); }
  }

  @Test
  void fluentAndPropertyFormsProduceEquivalentDefinitionsAndErrors() {
    var fluent = new Block(); var properties = new Block();
    fluent.strength(2, 6).noCollisions().noOcclusion().requiresToolToDrop().noCorrespondingItem();
    properties.setHardness(2); properties.setBlastResistance(6); properties.setCollision(false);
    properties.setOcclusion(false); properties.setRequiresCorrectTool(true); properties.setHasItem(false);
    assertEquals(properties.template(), fluent.template());
    fluent.destroyTime(-2); properties.setHardness(-2);
    assertEquals(assertThrows(IllegalArgumentException.class, properties::build).getMessage(),
        assertThrows(IllegalArgumentException.class, fluent::build).getMessage());
    var a = new Item(); var b = new Item();
    a.stacksTo(16).craftRemainder(new ScriptItemReference("minecraft:bucket"));
    b.setStackSize(16); b.setContainer("minecraft:bucket");
    assertEquals(b.template(), a.template());
    a.stacksTo(0); b.setStackSize(0);
    assertEquals(assertThrows(IllegalArgumentException.class, b::build).getMessage(),
        assertThrows(IllegalArgumentException.class, a::build).getMessage());
  }

  @Test
  void rejectsAmbiguousOrInvalidResourceRequests() {
    var block = new Block();
    assertThrows(IllegalArgumentException.class, () -> block.setDisplayName(" "));
    assertThrows(IllegalArgumentException.class, () -> block.dropsFrom("mmdlib:blocks/test"));
    block.noCorrespondingItem().dropsSelf();
    assertThrows(IllegalArgumentException.class, block::build);
    block.noDrops();
    assertDoesNotThrow(block::build);
  }
}
