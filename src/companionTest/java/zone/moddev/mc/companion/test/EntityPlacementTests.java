package zone.moddev.mc.companion.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.api.entity.EntityPlacement;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class EntityPlacementTests {
    @GameTest(template = "empty")
    public static void failedPlacementRetainsEntityAndDuplicateIdentityCannotSpawn(
        GameTestHelper h) {
        var level = h.getLevel();
        var entity = EntityType.CHICKEN.create(level);
        var before = entity.position();
        var blocked = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);
        var blockedResult = EntityPlacement.place(entity, level, List.of(
            new EntityPlacement.Position(Vec3.atBottomCenterOf(blocked), 0)));
        h.assertTrue(blockedResult == EntityPlacement.Result.NO_SPACE,
            "Blocked location is refused");
        h.assertTrue(entity.position().equals(before),
            "Failed placement restores detached position");
        h.assertTrue(level.getEntity(entity.getUUID()) == null,
            "Failure does not spawn");
        var clear = h.absolutePos(new BlockPos(4, 2, 4));
        var positions = List
            .of(new EntityPlacement.Position(Vec3.atBottomCenterOf(clear), 0));
        h.assertTrue(
            EntityPlacement.place(entity, level,
                positions) == EntityPlacement.Result.SUCCESS,
            "Clear location succeeds");
        var duplicate = EntityType.CHICKEN.create(level);
        duplicate.setUUID(entity.getUUID());
        h.assertTrue(
            EntityPlacement.place(duplicate, level,
                positions) == EntityPlacement.Result.DUPLICATE_UUID,
            "Duplicate loaded UUID is refused");
        entity.discard();
        h.succeed();
    }
}
