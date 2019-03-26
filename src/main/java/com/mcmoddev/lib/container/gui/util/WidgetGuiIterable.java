package com.mcmoddev.lib.container.gui.util;

import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;
import com.google.common.collect.Queues;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetLayout;

/**
 * Iterable implementation used to enumerate the entire sub-tree of an {@link IWidgetGui}.
 */
public class WidgetGuiIterable implements Iterable<IWidgetGui> {
    private final IWidgetGui root;

    /**
     * Initializes a new instance of WidgetGuiIterable.
     * @param root The root widget.
     */
    public WidgetGuiIterable(final IWidgetGui root) {
        this.root = root;
    }

    @Override
    public Iterator<IWidgetGui> iterator() {
        return new WidgetGuiIterator(this.root);
    }

    private class WidgetGuiIterator implements Iterator<IWidgetGui> {
        private final Deque<IWidgetGui> pieces = Queues.newArrayDeque();

        WidgetGuiIterator(final IWidgetGui root) {
            this.pieces.push(root);
        }

        @Override
        public boolean hasNext() {
            return (this.pieces.size() > 0);
        }

        @Override
        public IWidgetGui next() {
            final IWidgetGui piece = this.pieces.pop();

            if (piece instanceof IWidgetLayout) {
                final IWidgetLayout layout = IWidgetLayout.class.cast(piece);
                for(final IWidgetGui child : layout.getChildren()) {
                    this.pieces.push(child);
                }
            }

            return piece;
        }
    }

    /**
     * Executes the specified consumer for every widget in the sub tree, including the root one.
     * @param root The root widget to start iteration at.
     * @param action The consumer to apply to all widgets in tree.
     */
    public static void forEach(final IWidgetGui root, final Consumer<IWidgetGui> action) {
        for(final IWidgetGui widget: new WidgetGuiIterable(root)) {
            action.accept(widget);
        }
    }
}
