package zone.moddev.mc.companion.test;

import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.machine.feature.PortableData;
import zone.moddev.mc.lib.machine.feature.PortableVariable;
import zone.moddev.mc.lib.machine.feature.ProcessorState;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class PortableVariableTests {
    @GameTest(template = "empty")
    public static void itemAndProcessorShareSchemaWithoutSharingState(
        GameTestHelper h) {
        var reference = new PortableVariable<>(() -> Items.PAPER, "test",
            "count", Codec.intRange(0, 10), 0,
            UnaryOperator.<Integer>identity());
        var first = new ProcessorState();
        var firstKey = reference.declare(first).synced().define();
        var second = new ProcessorState();
        var secondKey = reference.declare(second).synced().define();
        var item = reference.stack(4);
        first.loadPortable(PortableData.read(item, reference.processor()));
        h.assertTrue(first.get(firstKey) == 4 && second.get(secondKey) == 0,
            "Portable reference declares independent processor state");
        first.update(() -> first.set(firstKey, 7));
        PortableData.write(item, reference.processor(), first.savePortable());
        h.assertTrue(reference.read(item) == 7,
            "Processor writes use the same item schema");
        item.set(DataComponents.CUSTOM_NAME, Component.literal("kept"));
        PortableData.write(item, "other", "flag", Codec.BOOL, true);
        PortableData.write(item, "test", "sibling", Codec.STRING, "kept");
        reference.write(item, 2);
        h.assertTrue(
            item.has(DataComponents.CUSTOM_NAME)
                && PortableData.read(item, "other", "flag", Codec.BOOL, false)
                && PortableData.read(item, "test", "sibling", Codec.STRING, "")
                    .equals("kept"),
            "Writes preserve unrelated components, processors and variables");
        PortableData.write(item, "test", "count", Codec.STRING, "bad");
        h.assertTrue(reference.read(item) == 0 && !reference.hasContent(item),
            "Malformed values use the declared default");
        var wrong = new ItemStack(Items.STONE);
        PortableData.write(wrong, "test", "count", Codec.INT, 4);
        h.assertTrue(reference.read(wrong) == 0,
            "Another item cannot impersonate the portable payload");
        boolean rejected = false;
        try {
            reference.write(wrong, 3);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        h.assertTrue(
            rejected
                && PortableData.read(wrong, "test", "count", Codec.INT, 0) == 4,
            "Wrong-item writes fail without changing the item");
        h.succeed();
    }
}
