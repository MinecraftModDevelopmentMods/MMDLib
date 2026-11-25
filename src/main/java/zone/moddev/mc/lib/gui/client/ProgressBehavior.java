package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.model.elements.ProgressElement;

final class ProgressBehavior extends DefaultElementBehavior {
    private static final ResourceLocation WARNING = ResourceLocation
        .parse("mmdlib:icon_error");
    private final ProgressElement element;

    ProgressBehavior(ProgressElement element, ElementAccess access) {
        super(access);
        this.element = element;
    }

    static void badge(ProgressElement element, ElementRenderers.Context c) {
        if (element.processorKey().isPresent()
            && element.outputBlocked(c.access().state())) {
            var bounds = c.bounds();
            c.graphics().blitSprite(WARNING, bounds.right() - 8,
                bounds.bottom() - 8, 8, 8);
        }
    }

    @Override
    public List<Component> tooltip(int x, int y) {
        return element.outputBlocked(access.presentation().state())
            ? List.of(
                Component.translatable("screen.mmdlib.progress.output_blocked")
                    .withStyle(ChatFormatting.RED))
            : List.of();
    }
}
