#loader mmdlib
// Game-test content for the script hooks (ScriptHookSmoke). Each handler leaves a mark the test
// checks: fire ticks on an entity, an extra mined block, a kept block or a redstone output.
import mods.mmdlib.ItemBuilder;
import mods.mmdlib.BlockBuilder;
import mods.mmdlib.ItemUseContext;
import mods.mmdlib.HitContext;
import mods.mmdlib.BreakContext;
import mods.mmdlib.BlockEventContext;
import mods.mmdlib.ItemClickContext;
import mods.mmdlib.ClickContext;

var tool = new ItemBuilder("script_hook_tool");
tool.stackSize = 1;
tool.useDuration = 10;
tool.useAnimation = "bow";
tool.onUseStart((ctx as ItemUseContext) => { ctx.duration = 3; });
tool.onUseFinish((ctx as ItemUseContext) => { ctx.entity.setRemainingFireTicks(77); });
tool.onRightClick((ctx as ItemClickContext) => {
    if (ctx.target == "air") ctx.player.setRemainingFireTicks(33);
});
tool.onHit((ctx as HitContext) => { ctx.target.setRemainingFireTicks(100); });
tool.onBreakBlock((ctx as BreakContext) => { ctx.mine(ctx.x, ctx.y + 1, ctx.z); });
tool.build();

var block = new BlockBuilder("script_hook_block");
block.redstoneOutput = true;
block.onPlaced((ctx as BlockEventContext) => { ctx.output = 4; });
block.onSteppedOn((ctx as BlockEventContext) => { ctx.entity.setRemainingFireTicks(60); });
block.onRandomTick((ctx as BlockEventContext) => { ctx.output = 9; });
block.onRightClick((ctx as ClickContext) => { ctx.output = ctx.output + 1; });
block.onBroken((ctx as BreakContext) => {
    if (ctx.player.isShiftKeyDown()) ctx.cancel();
});
block.build();

// The 3x3 hammer from docs/crafttweaker.md, checked by ScriptHookSmoke.hammer.
var hammer = new ItemBuilder("script_hammer");
hammer.durability = 500;
hammer.onBreakBlock((ctx as BreakContext) => {
    if (ctx.player.isShiftKeyDown()) return;
    for a in -1 .. 2 {
        for b in -1 .. 2 {
            if (ctx.face == "up" || ctx.face == "down") {
                ctx.mine(ctx.x + a, ctx.y, ctx.z + b);
            } else if (ctx.face == "north" || ctx.face == "south") {
                ctx.mine(ctx.x + a, ctx.y + b, ctx.z);
            } else {
                ctx.mine(ctx.x, ctx.y + a, ctx.z + b);
            }
        }
    }
});
hammer.build();
