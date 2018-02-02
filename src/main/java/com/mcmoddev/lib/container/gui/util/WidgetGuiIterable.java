package com.mcmoddev.lib.container.gui.util;

import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;
import com.google.common.collect.Queues;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetLayout;

public class WidgetGuiIterable implements Iterable<IWidgetGui> {
    private final IWidgetGui root;

    public WidgetGuiIterable(IWidgetGui root) {
        this.root = root;
    }

    @Override
    public Iterator<IWidgetGui> iterator() {
        return new WidgetGuiIterator(this.root);
    }

    private class WidgetGuiIterator implements Iterator<IWidgetGui> {
        private final Deque<IWidgetGui> pieces = Queues.newArrayDeque();

        WidgetGuiIterator(IWidgetGui root) {
            this.pieces.push(root);
        }

        @Override
        public boolean hasNext() {
            return (this.pieces.size() > 0);
        }

        @Override
        public IWidgetGui next() {
            IWidgetGui piece = this.pieces.pop();

            if (piece instanceof IWidgetLayout) {
                IWidgetLayout layout = IWidgetLayout.class.cast(piece);
                for(IWidgetGui child : layout.getChildren()) {
                    this.pieces.push(child);
                }
            }

            return piece;
        }
    }

    public static void forEach(IWidgetGui root, Consumer<IWidgetGui> action) {
        for(IWidgetGui widget: new WidgetGuiIterable(root)) {
            action.accept(widget);
        }
    }
}
