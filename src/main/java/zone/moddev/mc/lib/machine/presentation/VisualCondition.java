package zone.moddev.mc.lib.machine.presentation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Small typed expressions for model selection. No reflection, calls, or mutable
 * state.
 */
public final class VisualCondition {
    private record Term(VisualVariables.Kind kind,
        Function<Map<String, Object>, Object> evaluate) {
    }

    private final String source;
    private final Term root;
    private final Set<String> variables;

    private VisualCondition(String source, Term root, Set<String> variables) {
        this.source = source;
        this.root = root;
        this.variables = Set.copyOf(variables);
    }

    public static VisualCondition compile(String source,
        Map<String, Object> defaults) {
        if (source.length() > 2048)
            throw new IllegalArgumentException("Visual condition is too long");
        var parser = new Parser(source, defaults);
        var root = parser.expression(0);
        parser.space();
        if (parser.pos != source.length())
            throw parser.error("Unexpected token");
        parser.expect(root, VisualVariables.Kind.BOOLEAN);
        return new VisualCondition(source, root, parser.variables);
    }

    public boolean test(Map<String, Object> snapshot) {
        return (Boolean) root.evaluate().apply(snapshot);
    }

    public Set<String> variables() {
        return variables;
    }

    @Override
    public String toString() {
        return source;
    }

    private static final class Parser {
        private final String text;
        private final Map<String, Object> defaults;
        private final Set<String> variables = new HashSet<>();
        private int pos;
        private int depth;

        private Parser(String text, Map<String, Object> defaults) {
            this.text = text;
            this.defaults = defaults;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(
                message + " at " + pos + " in " + text);
        }

        private void space() {
            while (pos < text.length()
                && Character.isWhitespace(text.charAt(pos)))
                pos++;
        }

        private boolean take(String token) {
            space();
            if (!text.startsWith(token, pos))
                return false;
            pos += token.length();
            return true;
        }

        private void expect(Term term, VisualVariables.Kind kind) {
            if (term.kind() != kind)
                throw error("Expected " + kind + ", got " + term.kind());
        }

        private Term expression(int minimum) {
            if (++depth > 64)
                throw error("Condition nesting is too deep");
            Term left = atom();
            while (true) {
                space();
                String op = null;
                for (String candidate : new String[]{"||", "&&", "==", "!=",
                    "<=", ">=", "<", ">", "+", "-", "*", "/"}) {
                    if (text.startsWith(candidate, pos)) {
                        op = candidate;
                        break;
                    }
                }
                int precedence = op == null ? -1 : switch (op) {
                    case "||" -> 0;
                    case "&&" -> 1;
                    case "==", "!=" -> 2;
                    case "<", ">", "<=", ">=" -> 3;
                    case "+", "-" -> 4;
                    default -> 5;
                };
                if (precedence < minimum)
                    break;
                pos += op.length();
                left = binary(op, left, expression(precedence + 1));
            }
            depth--;
            return left;
        }

        private Term atom() {
            if (take("!")) {
                Term value = expression(6);
                expect(value, VisualVariables.Kind.BOOLEAN);
                return new Term(VisualVariables.Kind.BOOLEAN,
                    vars -> !(Boolean) value.evaluate().apply(vars));
            }
            if (take("-")) {
                Term value = expression(6);
                expect(value, VisualVariables.Kind.NUMBER);
                return new Term(VisualVariables.Kind.NUMBER,
                    vars -> -number(value, vars));
            }
            if (take("(")) {
                Term value = expression(0);
                if (!take(")"))
                    throw error("Expected )");
                return value;
            }
            if (take("$")) {
                String name;
                if (take("{")) {
                    int end = text.indexOf('}', pos);
                    if (end < 0)
                        throw error("Expected }");
                    name = text.substring(pos, end);
                    pos = end + 1;
                } else {
                    int start = pos;
                    while (pos < text.length()
                        && (Character.isLetterOrDigit(text.charAt(pos))
                            || text.charAt(pos) == '_'
                            || text.charAt(pos) == '.'))
                        pos++;
                    name = text.substring(start, pos);
                }
                if (!defaults.containsKey(name))
                    throw error("Unknown visual variable " + name);
                variables.add(name);
                Object fallback = defaults.get(name);
                return new Term(VisualVariables.Kind.of(fallback),
                    vars -> vars.getOrDefault(name, fallback));
            }
            space();
            if (pos < text.length()
                && (text.charAt(pos) == '\'' || text.charAt(pos) == '"')) {
                char quote = text.charAt(pos++);
                int start = pos;
                while (pos < text.length() && text.charAt(pos) != quote)
                    pos++;
                if (pos == text.length())
                    throw error("Unterminated string");
                String value = text.substring(start, pos++);
                return new Term(VisualVariables.Kind.TEXT, vars -> value);
            }
            if (take("true"))
                return new Term(VisualVariables.Kind.BOOLEAN, vars -> true);
            if (take("false"))
                return new Term(VisualVariables.Kind.BOOLEAN, vars -> false);
            space();
            int start = pos;
            while (pos < text.length() && (Character.isDigit(text.charAt(pos))
                || text.charAt(pos) == '.'))
                pos++;
            if (start == pos)
                throw error("Expected value");
            try {
                double value = Double.parseDouble(text.substring(start, pos));
                if (!Double.isFinite(value))
                    throw error("Non-finite number");
                return new Term(VisualVariables.Kind.NUMBER, vars -> value);
            } catch (NumberFormatException ex) {
                throw error("Invalid number");
            }
        }

        private static double number(Term term, Map<String, Object> vars) {
            return ((Number) term.evaluate().apply(vars)).doubleValue();
        }

        private Term binary(String op, Term left, Term right) {
            if (op.equals("&&") || op.equals("||")) {
                expect(left, VisualVariables.Kind.BOOLEAN);
                expect(right, VisualVariables.Kind.BOOLEAN);
                return new Term(VisualVariables.Kind.BOOLEAN,
                    vars -> op.equals("&&")
                        ? (Boolean) left.evaluate().apply(vars)
                            && (Boolean) right.evaluate().apply(vars)
                        : (Boolean) left.evaluate().apply(vars)
                            || (Boolean) right.evaluate().apply(vars));
            }
            if (op.equals("==") || op.equals("!=")) {
                expect(right, left.kind());
                return new Term(VisualVariables.Kind.BOOLEAN, vars -> {
                    boolean equal = left.kind() == VisualVariables.Kind.NUMBER
                        ? number(left, vars) == number(right, vars)
                        : left.evaluate().apply(vars)
                            .equals(right.evaluate().apply(vars));
                    return op.equals("==") == equal;
                });
            }
            expect(left, VisualVariables.Kind.NUMBER);
            expect(right, VisualVariables.Kind.NUMBER);
            if (Set.of("<", "<=", ">", ">=").contains(op))
                return new Term(VisualVariables.Kind.BOOLEAN,
                    vars -> switch (op) {
                        case "<" -> number(left, vars) < number(right, vars);
                        case "<=" -> number(left, vars) <= number(right, vars);
                        case ">" -> number(left, vars) > number(right, vars);
                        default -> number(left, vars) >= number(right, vars);
                    });
            return new Term(VisualVariables.Kind.NUMBER, vars -> {
                double a = number(left, vars), b = number(right, vars);
                return switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    default -> b == 0 ? 0.0 : a / b;
                };
            });
        }
    }
}
