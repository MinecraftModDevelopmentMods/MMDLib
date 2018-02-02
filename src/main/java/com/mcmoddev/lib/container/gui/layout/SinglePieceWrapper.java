package com.mcmoddev.lib.container.gui.layout;

import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.util.Padding;
import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SinglePieceWrapper extends BaseLayout {
    private final IWidgetGui inner;

    public SinglePieceWrapper(IWidgetGui inner) {
        this(inner, Padding.EMPTY);
    }

    public SinglePieceWrapper(IWidgetGui inner, int uniformPadding) {
        this(inner, new Padding(uniformPadding));
    }

    public SinglePieceWrapper(IWidgetGui inner, Padding padding) {
        this.inner = inner;
        this.onChildAdded(this.inner);
        this.setPadding(padding);
    }

    @Override
    public List<IWidgetGui> getChildren() {
        return Lists.asList(this.inner, new IWidgetGui[0]);
    }

    @Override
    public Size2D getChildPosition(IWidgetGui child) {
        Padding padding = this.inner.getPadding();
        return new Size2D(padding.left, padding.top);
    }

    @Override
    public Size2D getSize() {
        Padding childPadding = this.inner.getPadding();
        Size2D childSize = this.inner.getSize();
        return new Size2D(
            childSize.width + childPadding.getHorizontal(),
            childSize.height + childPadding.getVertical()
        );
    }
}
