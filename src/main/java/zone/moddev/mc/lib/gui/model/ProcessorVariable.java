package zone.moddev.mc.lib.gui.model;

import java.util.Map;

import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.Processor;

/**
 * A named, client-visible stored processor value shared by ordinary GUI
 * elements.
 */
public record ProcessorVariable(String processor, String name) {
    public static ProcessorVariable parse(String reference) {
        if (reference == null || !reference.matches(
            "\\$processors\\.[a-zA-Z0-9_:/-]+\\.[a-zA-Z_][a-zA-Z0-9_]*"))
            throw new IllegalArgumentException(
                "Expected $processors.<processor>.<variable>");
        int split = reference.lastIndexOf('.');
        return new ProcessorVariable(
            reference.substring("$processors.".length(), split),
            reference.substring(split + 1));
    }
    public Object read(ScreenHost host) {
        var features = host.screenFeatures();
        if (features == null)
            throw new IllegalArgumentException(
                "Element requires processor state");
        return features.findProcessor(processor)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown processor: " + processor))
            .variable(name);
    }
    public Object defaultValue(Map<String, Processor<?>> processors) {
        var target = processors.get(processor);
        if (target == null)
            throw new IllegalArgumentException(
                "Unknown processor: " + processor);
        var value = target.variableDefaults().get(name);
        if (value == null)
            throw new IllegalArgumentException(
                "Unknown client-visible variable: " + this);
        return value;
    }
}
