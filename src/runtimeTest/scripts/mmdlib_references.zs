#loader mmdlib
import mods.mmdlib.ItemBuilder;
import mods.mmdlib.ItemModule;
import mods.mmdlib.BlockModule;
import mods.mmdlib.BlockBuilder;
import mods.mmdlib.ItemReference;
import mods.mmdlib.BlockReference;
import mods.mmdlib.ItemModuleReference;
import mods.mmdlib.BlockModuleReference;
import mods.mmdlib.BlockTagReference;
import mods.mmdlib.ItemUseContext;
import mods.mmdlib.ClickContext;

var source = new ItemModule("phase3_source", new ItemModuleReference("mmdlib:ingot"));
source.stackSize = 1;
source.useDuration = 12;
source.addTags("c:phase3_source");
source.onUseStart((ctx as ItemUseContext) => { ctx.duration = 6; });
var copied = source.copy("phase3_copy");
copied.removeTags("all").addTags("c:phase3_copy");
var edited = copied.copy("phase3_edited");
edited.useDuration = 15;
edited.onUseStart((ctx as ItemUseContext) => { ctx.duration = 3; });
source.onUseStart((ctx as ItemUseContext) => { ctx.duration = 9; });
source.useDuration = 20;
source.build();
copied.build();
edited.build();
new ItemBuilder("phase3_source_item", source.reference).build();
new ItemBuilder("phase3_copy_item", copied.reference).build();
new ItemBuilder("phase3_edited_item", edited.reference).build();

// Store a reference before its target item is built. Resolution stays in the existing registrar.
var remainder = new ItemBuilder("phase3_remainder");
remainder.remainder(new ItemReference("mmdlib:phase3_later"));
remainder.build();
new ItemBuilder("phase3_later").build();

var blocks = new BlockModule("phase3_blocks", new BlockModuleReference("mmdlib:block"));
blocks.redstoneOutput = true;
blocks.onRightClick((ctx as ClickContext) => { ctx.output = 4; });
var blockCopy = blocks.copy("phase3_blocks_copy");
blocks.onRightClick((ctx as ClickContext) => { ctx.output = 8; });
blockCopy.hardness = 5;
blockCopy.addTag(new BlockTagReference("minecraft:mineable/axe"));
blocks.build();
blockCopy.build();
new BlockBuilder("phase3_block", blocks.reference).build();
var copiedBlock = new BlockBuilder("phase3_copy_block", blockCopy.reference);
copiedBlock.baseBlockFrom(new BlockReference("minecraft:stone"));
copiedBlock.build();
