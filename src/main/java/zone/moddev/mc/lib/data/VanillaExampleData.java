package zone.moddev.mc.lib.data;

import com.google.common.hash.Hashing;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Registers every example output with Minecraft's hash cache, so datagen owns rather than deletes it. */
@EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD)
public final class VanillaExampleData implements DataProvider {
    private final Path project;
    private final Path output;
    private VanillaExampleData(Path project, Path output) {
        this.project = project; this.output = output;
    }
    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.getMods().contains("mmdlib")) return;
        var projectDirectory = System.getProperty("mmdlib.projectDir");
        if (projectDirectory == null) throw new IllegalStateException("Run MMDLib's configured runData task to generate its example resources");
        event.getGenerator().addProvider(event.includeServer(),
                new VanillaExampleData(Path.of(projectDirectory), event.getGenerator().getPackOutput().getOutputFolder()));
    }
    @Override public String getName() { return "MMDLib vanilla extensions"; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.runAsync(() -> {
            Path staging = null;
            try {
                staging = Files.createTempDirectory("mmdlib-example-datagen-");
                var command = new ArrayList<String>();
                command.add(System.getProperty("mmdlib.python", "python3"));
                command.add(project.resolve("scripts/generate_vanilla_example.py").toString());
                command.add("--output"); command.add(staging.toString());
                var assets = System.getProperty("mmdlib.minecraftAssets");
                if (assets != null) { command.add("--minecraft-assets"); command.add(assets); }
                var process = new ProcessBuilder(command).directory(project.toFile()).inheritIO().start();
                try {
                    if (process.waitFor() != 0) throw new IOException("Vanilla example generator failed; see its output above");
                } finally { if (process.isAlive()) process.destroyForcibly(); }
                try (var files = Files.walk(staging)) {
                    for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                        var relative = staging.relativize(file);
                        if (!relative.startsWith("data")) throw new IOException("Artwork must remain in static main resources: " + relative);
                        var bytes = Files.readAllBytes(file);
                        cache.writeIfNeeded(output.resolve(relative), bytes, Hashing.sha1().hashBytes(bytes));
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); throw new CompletionException("Interrupted vanilla example datagen", ex);
            } catch (IOException ex) { throw new CompletionException("Cannot generate vanilla examples (Python 3 required)", ex); }
            finally {
                if (staging != null) try (var files = Files.walk(staging)) {
                    for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
                } catch (IOException ex) { throw new CompletionException("Cannot remove datagen staging directory", ex); }
            }
        });
    }
}
