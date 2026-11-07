#loader mmdlib
import mods.mmdlib.BlockBuilder;
import mods.mmdlib.ItemBuilder;
import mods.mmdlib.ItemReference;

var plain = new BlockBuilder("phase4_default");
plain.displayName = "Default Named Block";
plain.strength(2, 6).requiresToolToDrop();
plain.dropsNormally();
plain.build();
var self = new BlockBuilder("phase4_self");
self.destroyTime(2).explosionResistance(6).dropsSelf();
self.build();
var empty = new BlockBuilder("phase4_none");
empty.noDrops();
empty.build();
var referenced = new BlockBuilder("phase4_reference");
referenced.dropsFrom("minecraft:blocks/stone");
referenced.build();
var overridden = new BlockBuilder("phase4_override");
overridden.noDrops();
overridden.build();
var itemless = new BlockBuilder("phase4_itemless");
itemless.noCorrespondingItem().noCollisions().noOcclusion();
itemless.build();
var item = new ItemBuilder("phase4_item");
item.stacksTo(16).craftRemainder(new ItemReference("minecraft:bucket"));
item.displayName = "Named Widget";
item.build();
