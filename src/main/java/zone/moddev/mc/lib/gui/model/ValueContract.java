package zone.moddev.mc.lib.gui.model;

import java.util.Set;

import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypes;

/** Reusable checks and kinds; elements declare when they apply. */
public final class ValueContract {
    private ValueContract() {
    }

    public static final Set<ValueType<?>> NUMBERS = Set.of(ValueTypes.WHOLE, ValueTypes.DECIMAL,
        ValueTypes.NUMBER);

    public static void require(Element element, String description, boolean maximum) {
        if (element.common().value().isEmpty())
            throw new IllegalArgumentException(description + " needs a value getter");
        if (maximum && element.common().max().isEmpty())
            throw new IllegalArgumentException(description + " needs a max getter");
    }
}
