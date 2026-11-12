package zone.moddev.mc.lib.gui;

import java.util.Objects;
import java.util.function.BiPredicate;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import zone.moddev.mc.lib.gui.model.elements.CustomElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

/**
 * Compatibility specialization of the common element-action transport for
 * custom widgets.
 */
public final class WidgetAction<T> extends ElementAction<T> {
    private final BiPredicate<Context, T> widgetHandler;

    public WidgetAction(ResourceLocation id,
        StreamCodec<RegistryFriendlyByteBuf, T> codec, int maxBytes,
        BiPredicate<Context, T> handler) {
        super(id, codec, maxBytes,
            (context, value) -> handler.test(new Context(context.player(),
                context.menu(), (CustomElement) context.element()), value));
        widgetHandler = Objects.requireNonNull(handler);
    }

    public BiPredicate<Context, T> handler() {
        return widgetHandler;
    }

    public boolean dispatch(Context context, byte[] data) {
        return super.dispatch(new ElementAction.Context(context.player(),
            context.menu(), context.widget()), data);
    }

    public record Context(ServerPlayer player, MachineMenu menu,
        CustomElement widget) {
    }
}
