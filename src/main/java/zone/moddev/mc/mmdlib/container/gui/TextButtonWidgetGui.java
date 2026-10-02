package zone.moddev.mc.mmdlib.container.gui;

import zone.moddev.mc.mmdlib.container.gui.util.Size2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

public class TextButtonWidgetGui extends BaseButtonWidgetGui {
    private String text;

    public TextButtonWidgetGui(String text) {
        this(text, Minecraft.getMinecraft().fontRenderer.getStringWidth(text) + 10);
    }

    public TextButtonWidgetGui(String text, int width) {
        super(width, STANDARD_HEIGHT);

        this.text = text;
    }

    // Button labels use Minecraft's default font at its native scale.

    //#region RENDER

    @Override
    public void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        if ((this.text == null) || this.text.isEmpty()) {
            return;
        }

        Size2D size = this.getSize();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        Size2D textSize = new Size2D(font.getStringWidth(this.text), font.FONT_HEIGHT);

        font.drawString(this.text, (size.width - textSize.width) / 2, (size.height - textSize.height) / 2, 0);
    }

    //#endregion
}
