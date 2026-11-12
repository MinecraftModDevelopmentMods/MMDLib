package zone.moddev.mc.lib.machine.expr;

import java.math.BigDecimal;

import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * A small arithmetic expression over named variables, such as
 * {@code 0.08 * ($material.hardness -
 * 3)}: numbers, {@code + - * /}, parentheses, unary minus, and variables
 * written {@code
 * $name(.segment)*}. Variable names are passed to {@link #eval} without the
 * {@code $}.
 */
public sealed interface Expr {
    /**
     * An expression from JSON: a number, or a string holding an expression. A
     * bad string is a decode error.
     */
    Codec<Expr> CODEC = Codec.either(Codec.DOUBLE, Codec.STRING)
        .comapFlatMap(value -> value
            .map(number -> DataResult.success(constant(number)), text -> {
                try {
                    return DataResult.success(parse(text));
                } catch (IllegalArgumentException ex) {
                    return DataResult.error(ex::getMessage);
                }
            }),
            expr -> expr instanceof Constant constant
                ? Either.left(constant.value())
                : Either.right(expr.toString()));

    /**
     * The value of this expression.
     *
     * @throws IllegalArgumentException
     *             if a variable has no value in {@code vars}
     */
    double eval(Function<String, OptionalDouble> vars);

    /** The names of every variable used, without {@code $}. */
    default Set<String> variables() {
        var names = new TreeSet<String>();
        collect(this, names);
        return Set.copyOf(names);
    }

    /** An expression that is always {@code value}. */
    static Expr constant(double value) {
        return new Constant(value);
    }

    /**
     * Parses {@code text}.
     *
     * @throws IllegalArgumentException
     *             naming the 0-based position of the first character that
     *             doesn't fit the grammar
     */
    static Expr parse(String text) {
        return new Parser(text).parseAll();
    }

    private static void collect(Expr expr, Set<String> names) {
        switch (expr) {
            case Variable variable -> names.add(variable.name());
            case Negate negate -> collect(negate.operand(), names);
            case Binary binary -> {
                collect(binary.left(), names);
                collect(binary.right(), names);
            }
            case Constant constant -> {
            }
        }
    }

    /** A number. */
    record Constant(double value) implements Expr {
        @Override
        public double eval(Function<String, OptionalDouble> vars) {
            return value;
        }

        /**
         * Plain decimal notation, which {@link #parse} reads back (it has no
         * exponents).
         */
        @Override
        public String toString() {
            return BigDecimal.valueOf(value).stripTrailingZeros()
                .toPlainString();
        }
    }

    /** A variable, named without {@code $}. */
    record Variable(String name) implements Expr {
        @Override
        public double eval(Function<String, OptionalDouble> vars) {
            return vars.apply(name).orElseThrow(
                () -> new IllegalArgumentException("No value for $" + name));
        }

        @Override
        public String toString() {
            return "$" + name;
        }
    }

    /** {@code -operand}. */
    record Negate(Expr operand) implements Expr {
        @Override
        public double eval(Function<String, OptionalDouble> vars) {
            return -operand.eval(vars);
        }

        @Override
        public String toString() {
            return "-(" + operand + ")";
        }
    }

    /** {@code left op right}, where {@code op} is one of {@code + - * /}. */
    record Binary(char op, Expr left, Expr right) implements Expr {
        @Override
        public double eval(Function<String, OptionalDouble> vars) {
            double a = left.eval(vars), b = right.eval(vars);
            return switch (op) {
                case '+' -> a + b;
                case '-' -> a - b;
                case '*' -> a * b;
                default -> a / b;
            };
        }

        @Override
        public String toString() {
            return "(" + left + " " + op + " " + right + ")";
        }
    }

    /**
     * Recursive descent: sum := product (('+'|'-') product)*, product := unary
     * (('*'|'/') unary)*.
     */
    final class Parser {
        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text;
        }

        private Expr parseAll() {
            var expr = sum();
            skipSpaces();
            if (pos < text.length())
                throw error("Unexpected '" + text.charAt(pos) + "'");
            return expr;
        }

        private Expr sum() {
            var expr = product();
            for (char op = peek(); op == '+' || op == '-'; op = peek()) {
                pos++;
                expr = new Binary(op, expr, product());
            }
            return expr;
        }

        private Expr product() {
            var expr = unary();
            for (char op = peek(); op == '*' || op == '/'; op = peek()) {
                pos++;
                expr = new Binary(op, expr, unary());
            }
            return expr;
        }

        private Expr unary() {
            char next = peek();
            if (next == '-') {
                pos++;
                return new Negate(unary());
            }
            if (next == '(') {
                pos++;
                var expr = sum();
                if (peek() != ')')
                    throw error("Expected ')'");
                pos++;
                return expr;
            }
            if (next == '$')
                return variable();
            if (Character.isDigit(next) || next == '.')
                return number();
            throw error(pos < text.length()
                ? "Unexpected '" + next + "'"
                : "Expression ends early");
        }

        private Expr variable() {
            int start = ++pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                boolean segmentEnd = c == '.' && pos > start
                    && text.charAt(pos - 1) != '.';
                if (Character.isLetterOrDigit(c) || c == '_' || segmentEnd)
                    pos++;
                else
                    break;
            }
            if (pos == start || text.charAt(pos - 1) == '.')
                throw error("Bad variable name");
            return new Variable(text.substring(start, pos));
        }

        private Expr number() {
            int start = pos;
            while (pos < text.length() && (Character.isDigit(text.charAt(pos))
                || text.charAt(pos) == '.'))
                pos++;
            try {
                return new Constant(
                    Double.parseDouble(text.substring(start, pos)));
            } catch (NumberFormatException ex) {
                pos = start;
                throw error("Bad number");
            }
        }

        /** The next non-space character, without consuming it; 0 at the end. */
        private char peek() {
            skipSpaces();
            return pos < text.length() ? text.charAt(pos) : 0;
        }

        private void skipSpaces() {
            while (pos < text.length()
                && Character.isWhitespace(text.charAt(pos)))
                pos++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos
                + " in expression '" + text + "'");
        }
    }
}
