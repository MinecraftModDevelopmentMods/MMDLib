package zone.moddev.mc.lib.gui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.runtime.ScreenClick;

/**
 * Shared click payload and callback behavior; domain actions belong to their
 * elements.
 */
public final class ElementActions {
    private ElementActions() {
    }

    public record Click(int button, int modifiers, int option) {
        public Click {
            if (button < 0 || button > 1 || modifiers < 0 || modifiers > 7
                || option < 0 || option > 4095)
                throw new IllegalArgumentException(
                    "Click outside protocol limits");
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Click> CODEC = StreamCodec
            .of((b, v) -> {
                b.writeByte(v.button());
                b.writeByte(v.modifiers());
                b.writeVarInt(v.option());
            }, b -> new Click(b.readUnsignedByte(), b.readUnsignedByte(),
                b.readVarInt()));
    }

    public static final ElementAction<Click> CLICK = new ElementAction<>(
        ResourceLocation.parse("mmdlib:click"), Click.CODEC, 8,
        (context, value) -> {
            var element = context.element();
            if (!element.validClickOption(value.option()))
                return false;
            var click = new ScreenClick(context.player(), context.menu(),
                element, value.button(), (value.modifiers() & 1) != 0,
                (value.modifiers() & 2) != 0, (value.modifiers() & 4) != 0,
                value.option());
            if (element.interact(click))
                return true;
            var clicks = element.common().clicks();
            var normal = value.button() == 0
                ? clicks.click()
                : clicks.rightClick();
            var shifted = value.button() == 0
                ? clicks.shiftClick()
                : clicks.shiftRightClick();
            var handler = click.shift() ? shifted.or(() -> normal) : normal;
            handler
                .ifPresent(h -> zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL
                    .events().dispatch(h,
                        zone.moddev.mc.lib.api.MaterialEventTypes.GUI_CLICK,
                        click));
            return handler.isPresent();
        });
}
