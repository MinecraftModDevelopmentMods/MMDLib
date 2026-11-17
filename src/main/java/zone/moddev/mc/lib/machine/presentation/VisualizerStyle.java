package zone.moddev.mc.lib.machine.presentation;

/** Per-machine RGB colors for world-space range and active-work displays. */
public record VisualizerStyle(int rangeColor, int workColor) {
    public static final VisualizerStyle DEFAULT = new VisualizerStyle(0x59ff59,
        0xffb74d);

    public VisualizerStyle {
        if ((rangeColor & 0xff000000) != 0 || (workColor & 0xff000000) != 0)
            throw new IllegalArgumentException(
                "Visualizer colors must be 24-bit RGB");
    }

    /** Implement on a machine host to configure both displays without XML. */
    public interface Provider {
        VisualizerStyle visualizerStyle();
    }

    public static VisualizerStyle of(Object host) {
        return host instanceof Provider provider
            ? provider.visualizerStyle()
            : DEFAULT;
    }
}
