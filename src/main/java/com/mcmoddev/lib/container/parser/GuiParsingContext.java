package com.mcmoddev.lib.container.parser;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import com.mcmoddev.lib.container.widget.IWidget;

public class GuiParsingContext {
    private final List<IWidget> baseWidgets;
    private final boolean forGui;

    public GuiParsingContext(List<IWidget> baseWidgets, boolean forGui) {
        // make a clone because the original will most likely get more widgets along the way
        this.baseWidgets = new ArrayList<>(baseWidgets);

        this.forGui = forGui;
    }

    public boolean isForGui() {
        return this.forGui;
    }

    @Nullable
    public IWidget findWidget(String key) {
        return this.baseWidgets
            .stream()
            .filter(w -> w.getKey().equals(key))
            .findFirst()
            .orElse(null);
    }

    @Nullable
    public <T> T findWidget(Class<T> expected, String key) {
        IWidget widget = this.findWidget(key);
        if ((widget != null) && expected.isInstance(expected)) {
            return expected.cast(widget);
        }
        return null;
    }
}
