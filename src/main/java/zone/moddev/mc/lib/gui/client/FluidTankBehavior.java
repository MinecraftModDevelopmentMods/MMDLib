package zone.moddev.mc.lib.gui.client;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;

final class FluidTankBehavior extends DefaultElementBehavior {
    private static final int DRAIN_WIDTH = 11;
    private static final int DRAIN_HEIGHT = 10;

    private final FluidTankElement tank;

    FluidTankBehavior(FluidTankElement tank, ElementAccess access) {
        super(access);
        this.tank = tank;
    }

    private static Rect drainBounds(Rect gauge) {
        return new Rect(gauge.x() + (gauge.width() - DRAIN_WIDTH) / 2,
            gauge.bottom(), DRAIN_WIDTH, DRAIN_HEIGHT);
    }

    static void drawDrain(FluidTankElement tank,
        ElementRenderers.Context context) {
        if (!tank.drainButton())
            return;
        var valve = drainBounds(context.bounds());
        boolean open = tank.drainOpen(context.access().state());
        boolean hovered = valve.contains(context.mouseX(), context.mouseY());
        float opacity = open ? 1f : hovered ? 0.8f : 0.35f;
        var graphics = context.graphics();
        graphics.flush();
        graphics.setColor(1, 1, 1, opacity);
        try {
            ElementDrawing.sprite(graphics, "icon_tank_drain", valve);
        } finally {
            graphics.setColor(1, 1, 1, 1);
        }
    }

    @Override
    public Optional<Rect> additionalHitBounds() {
        return tank.drainButton()
            ? Optional.of(drainBounds(access.bounds()))
            : Optional.empty();
    }

    @Override
    public Click mouseClicked(double x, double y, int button) {
        if (!overDrain(x, y))
            return super.mouseClicked(x, y, button);
        if (button == 0 && access.value() instanceof FluidStack fluid
            && !fluid.isEmpty())
            access.click(ElementActions.CLICK, button,
                FluidTankElement.DRAIN_OPTION);
        return Click.CONSUME;
    }

    @Override
    public List<Component> tooltip(int x, int y) {
        if (!overDrain(x, y))
            return List.of();
        var presentation = access.presentation();
        var tankRows = presentation.serverTooltip(tank);
        var name = tankRows.isEmpty()
            ? tank.defaultTankName().copy().withStyle(ChatFormatting.GOLD)
            : tankRows.getFirst();
        if (!(access.value() instanceof FluidStack fluid) || fluid.isEmpty())
            return List.of(name,
                Component.translatable("screen.mmdlib.tank.drain.empty"));
        return List.of(name,
            Component.translatable("screen.mmdlib.tank.contents",
                fluid.getAmount(), fluid.getHoverName()),
            Component
                .translatable(tank.drainOpen(presentation.state())
                    ? "screen.mmdlib.tank.drain.close"
                    : "screen.mmdlib.tank.drain.open")
                .withStyle(ChatFormatting.GRAY));
    }

    private boolean overDrain(double x, double y) {
        return tank.drainButton()
            && drainBounds(access.bounds()).contains(x, y);
    }
}
