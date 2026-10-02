package dev.testbuild;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MiniJson {
    static Object parse(String text) {
        Parser p = new Parser(text);
        Object value = p.value();
        p.ws();
        if (!p.end()) throw p.error("trailing JSON content");
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("expected JSON object");
        return (Map<String, Object>) map;
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) { this.text = text; }

        Object value() {
            ws();
            if (end()) throw error("unexpected end of JSON");
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || Character.isDigit(c)) yield number();
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        Map<String, Object> object() {
            expect('{');
            ws();
            Map<String, Object> out = new LinkedHashMap<>();
            if (take('}')) return out;
            while (true) {
                ws();
                String key = string();
                ws();
                expect(':');
                Object value = value();
                out.put(key, value);
                ws();
                if (take('}')) return out;
                expect(',');
            }
        }

        List<Object> array() {
            expect('[');
            ws();
            List<Object> out = new ArrayList<>();
            if (take(']')) return out;
            while (true) {
                out.add(value());
                ws();
                if (take(']')) return out;
                expect(',');
            }
        }

        String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!end()) {
                char c = text.charAt(pos++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (end()) throw error("unfinished escape");
                char e = text.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw error("unfinished unicode escape");
                        String hex = text.substring(pos, pos + 4);
                        try { out.append((char) Integer.parseInt(hex, 16)); }
                        catch (NumberFormatException ex) { throw error("bad unicode escape"); }
                        pos += 4;
                    }
                    default -> throw error("bad escape '\\" + e + "'");
                }
            }
            throw error("unterminated string");
        }

        Object number() {
            int start = pos;
            if (text.charAt(pos) == '-') pos++;
            while (!end() && Character.isDigit(text.charAt(pos))) pos++;
            if (!end() && text.charAt(pos) == '.') {
                pos++;
                while (!end() && Character.isDigit(text.charAt(pos))) pos++;
            }
            if (!end() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                pos++;
                if (!end() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) pos++;
                while (!end() && Character.isDigit(text.charAt(pos))) pos++;
            }
            String value = text.substring(start, pos);
            try {
                if (value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0) {
                    return Long.parseLong(value);
                }
                return Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                throw error("invalid number");
            }
        }

        Object literal(String literal, Object value) {
            if (!text.startsWith(literal, pos)) throw error("expected " + literal);
            pos += literal.length();
            return value;
        }

        void ws() {
            while (!end() && Character.isWhitespace(text.charAt(pos))) pos++;
        }

        boolean take(char c) {
            if (!end() && text.charAt(pos) == c) { pos++; return true; }
            return false;
        }

        void expect(char c) {
            if (end() || text.charAt(pos) != c) throw error("expected '" + c + "'");
            pos++;
        }

        boolean end() { return pos >= text.length(); }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + pos);
        }
    }

    private MiniJson() {}
}
