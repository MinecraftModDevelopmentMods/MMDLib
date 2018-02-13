package com.mcmoddev.lib.container.parser;

import java.util.List;
import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.widget.IWidget;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IGuiParser {
    void initialize(GuiParsingContext context, JsonElement json) throws JsonParseException;

    @Nullable
    default List<IWidget> createWidgets() { return null; }

    default void createWidgetsToList(List<IWidget> widgets) {
        List<IWidget> mine = this.createWidgets();
        if ((mine != null) && !mine.isEmpty()) {
            widgets.addAll(mine);
        }
    }

    @Nullable
    @SideOnly(Side.CLIENT)
    IWidgetGui createRootWidgetGui(GuiContext context);
}
