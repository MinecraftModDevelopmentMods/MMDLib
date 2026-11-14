package zone.moddev.mc.lib.machine.feature;

import java.util.Optional;
import java.util.function.BiPredicate;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** An explicitly declared, argument-free machine command. */
public record ProcessorAction(BiPredicate<ProcessorState, Context> available,
    Handler handler) {
    public record Context(ProcessorMachine machine, Player player) {
    }

    @FunctionalInterface
    public interface Handler {
        Result execute(ProcessorState state, Context context);
    }

    public record Result(boolean accepted, Optional<Component> message) {
        public static Result success() {
            return new Result(true, Optional.empty());
        }
        public static Result unavailable() {
            return new Result(false, Optional.empty());
        }
        public static Result failure(Component message) {
            return new Result(false, Optional.of(message));
        }
    }
}
