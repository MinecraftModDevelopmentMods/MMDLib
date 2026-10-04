package zone.moddev.mc.mmdlib.compat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData;

/** The legacy names are aliases for one implementation, never a second registry. */
public final class LegacyNamespaceAliases {
    public static final String OLD_INTERNAL = "com/mcmoddev/lib/";
    public static final String NEW_INTERNAL = "zone/moddev/mc/mmdlib/";
    public static final String OLD_DOTTED = "com.mcmoddev.lib.";
    public static final String NEW_DOTTED = "zone.moddev.mc.mmdlib.";
    public static final String RESOURCE = "META-INF/mmdlib-legacy-names.list";

    private LegacyNamespaceAliases() { }

    public static Map<String, String> load() {
        Map<String, String> result = new LinkedHashMap<>();
        InputStream input = LegacyNamespaceAliases.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing MMDLib legacy API alias inventory");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] names = line.split("=", -1);
                if (names.length != 2 || !names[0].startsWith(OLD_INTERNAL)
                        || !names[1].equals(NEW_INTERNAL + names[0].substring(OLD_INTERNAL.length()))
                        || result.put(names[0], names[1]) != null) {
                    throw new IllegalStateException("Invalid MMDLib legacy API alias: " + line);
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read MMDLib legacy API aliases", error);
        }
        if (result.isEmpty()) throw new IllegalStateException("Empty MMDLib legacy API alias inventory");
        return Collections.unmodifiableMap(result);
    }

    /** Forge scans annotations before class loading, so accept both annotation names. */
    public static Set<ASMData> pluginData(final ASMDataTable table) {
        Set<ASMData> result = new LinkedHashSet<>(table.getAll(NEW_DOTTED + "integration.MMDPlugin"));
        result.addAll(table.getAll(OLD_DOTTED + "integration.MMDPlugin"));
        return result;
    }
}
