package zone.moddev.mc.companion.test;

import java.util.List;
import java.util.Set;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypeRegistry;

/**
 * Common-only fixture registers before screen loading, exactly as an external
 * mod would.
 */
@EventBusSubscriber(modid = CompanionMod.ID, bus = EventBusSubscriber.Bus.MOD)
public final class ValueTypeFixture {
    public static final class Reading {
        int amount;

        Reading(int amount) {
            this.amount = amount;
        }
    }

    static Reading live = new Reading(3);
    static final ValueType<Reading> TYPE = ValueType
        .<Reading>builder(
            ResourceLocation.parse("mmdlib_companion:test_reading"),
            Reading.class,
            () -> StreamCodec.of((b, v) -> b.writeInt(v.amount),
                b -> new Reading(b.readInt())),
            4)
        .validate(v -> v.amount >= 0).equality((a, b) -> a.amount == b.amount)
        .build();

    @SubscribeEvent
    public static void register(FMLConstructModEvent event) {
        ValueTypeRegistry.GLOBAL.register(TYPE);
    }

    public static Reading reading(BlockEntity ignored) {
        return live;
    }

    static final ElementAction<Integer> EDIT = new ElementAction<>(
        ResourceLocation.parse("mmdlib_companion:test_reading_edit"),
        StreamCodec.of((b, v) -> b.writeInt(v), b -> b.readInt()), 4,
        (context, value) -> {
            if (value < 0)
                return false;
            live.amount = value;
            return true;
        });

    record Display(Common common) implements Element {
        public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
            return Set.of(TYPE);
        }

        public List<? extends ElementAction<?>> actions() {
            return List.of(EDIT);
        }

        public int[] measure(TextMeasure text) {
            return new int[]{140, 14};
        }
    }
}
