package zone.moddev.mc.companion.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

@EventBusSubscriber(modid = CompanionMod.ID, bus = EventBusSubscriber.Bus.MOD)
@GameTestHolder("mmdlib_porting")
@PrefixGameTestTemplate(false)
public final class LegacyMachineEntityTests {
    @SubscribeEvent
    public static void register(FMLConstructModEvent event) {
        MachineContent.registerEntityFactory(
            CompanionMod.id("test/upgraded_fluid_still"), LegacyStill::new);
    }

    public static final class LegacyStill extends DefinedMachineEntity {
        private int migrations;

        public LegacyStill(BlockEntityType<?> type, BlockPos pos,
            BlockState state, MachineDefinition definition,
            MachineRecipeType recipes) {
            super(type, pos, state, definition, recipes);
        }

        @Override
        protected CompoundTag migrateMachineData(CompoundTag tag,
            HolderLookup.Provider registries) {
            if (tag.contains("energy")
                || !tag.contains("StillFE", Tag.TAG_ANY_NUMERIC))
                return tag;
            migrations++;
            var converted = tag.copy();
            converted.putLong("energy", tag.getLong("StillFE"));
            converted.remove("StillFE");
            return converted;
        }
    }

    @GameTest(template = "empty")
    public static void legacyEnergySurvivesAnXmlMachineRoundTrip(
        GameTestHelper h) {
        var placed = PortingHooksTests.place(h, "test/upgraded_fluid_still");
        h.assertTrue(placed instanceof LegacyStill,
            "Placement uses the registered subclass");
        var registries = h.getLevel().registryAccess();
        var pos = placed.getBlockPos();
        var state = placed.getBlockState();
        var legacy = new CompoundTag();
        legacy.putString("id", "mmdlib_companion:test/upgraded_fluid_still");
        legacy.putInt("StillFE", 625);
        var imported = (LegacyStill) BlockEntity.loadStatic(pos, state, legacy,
            registries);
        h.assertTrue(
            imported != null && imported.migrations == 1
                && imported.energy.stored() == 625,
            "World load translates the legacy value before XML storage reads it");
        var saved = imported.saveWithFullMetadata(registries);
        h.assertTrue(
            saved.getLong("energy") == 625 && !saved.contains("StillFE"),
            "Saving writes current XML storage");
        var restored = (LegacyStill) BlockEntity.loadStatic(pos, state, saved,
            registries);
        h.assertTrue(
            restored != null && restored.migrations == 0
                && restored.energy.stored() == 625,
            "Current saves retain the imported energy without a second migration");
        var update = restored.getUpdateTag(registries);
        update.putInt("StillFE", 999);
        restored.loadWithComponents(update, registries);
        h.assertTrue(
            restored.migrations == 0 && restored.energy.stored() == 625,
            "Client updates bypass the persistence hook");
        h.succeed();
    }
}
