package zone.moddev.mc.lib.client.jei;

import java.util.List;
import java.util.function.BiConsumer;

import com.mojang.blaze3d.systems.RenderSystem;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import org.joml.Vector3f;

import zone.moddev.mc.lib.gui.client.GhostItems;

/** Faded samples retain JEI's original ingredient tooltips and navigation. */
final class JeiGhostRenderer<T> implements IIngredientRenderer<T> {
    private final IIngredientRenderer<T> delegate;
    private final BiConsumer<GuiGraphics, T> draw;

    private JeiGhostRenderer(IIngredientRenderer<T> delegate, BiConsumer<GuiGraphics, T> draw) {
        this.delegate = delegate;
        this.draw = draw;
    }

    static IIngredientRenderer<ItemStack> items(IIngredientRenderer<ItemStack> delegate) {
        var ghosts = new GhostItems();
        return new JeiGhostRenderer<>(delegate, (graphics, stack) -> {
            ghosts.render(graphics, stack, 0, 0);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        });
    }

    static IIngredientRenderer<FluidStack> fluids(IIngredientRenderer<FluidStack> delegate,
        long capacity) {
        return new JeiGhostRenderer<>(delegate, (graphics, fluid) -> {
            if (fluid.isEmpty())
                return;
            int height = (int) Math.max(1, Math.min(16, 16L * fluid.getAmount() / capacity));
            var extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
            var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(extensions.getStillTexture(fluid));
            int tint = extensions.getTintColor(fluid);
            graphics.flush();
            graphics.setColor((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f,
                (tint & 255) / 255f, (tint >>> 24) / 255f * .5f);
            // GuiGraphics scissor coordinates are screen-relative, whereas JEI translates the
            // pose into each slot. Convert both corners before clipping the bottom-filled sprite.
            var matrix = graphics.pose().last().pose();
            var top = matrix.transformPosition(new Vector3f(0, 16 - height, 0));
            var bottom = matrix.transformPosition(new Vector3f(16, 16, 0));
            graphics.enableScissor((int) Math.floor(top.x()), (int) Math.floor(top.y()),
                (int) Math.ceil(bottom.x()), (int) Math.ceil(bottom.y()));
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            try {
                graphics.blit(0, 0, 0, 16, 16, sprite);
                graphics.flush();
            } finally {
                graphics.disableScissor();
                graphics.setColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, T ingredient) {
        draw.accept(graphics, ingredient);
    }

    @Override
    @SuppressWarnings("removal")
    public List<Component> getTooltip(T ingredient, TooltipFlag flag) {
        return delegate.getTooltip(ingredient, flag);
    }

    @Override
    public List<Component> getTooltip(T ingredient, Item.TooltipContext context,
        @Nullable Player player, TooltipFlag flag) {
        return delegate.getTooltip(ingredient, context, player, flag);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, T ingredient, Item.TooltipContext context,
        @Nullable Player player, TooltipFlag flag) {
        delegate.getTooltip(tooltip, ingredient, context, player, flag);
    }
}
