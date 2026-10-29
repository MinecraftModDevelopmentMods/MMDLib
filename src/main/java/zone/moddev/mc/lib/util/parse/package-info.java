/**
 * A small fluent library for walking Gson JSON with checked types and readable errors, e.g. {@code
 * JParse.begin(json).key("count", v -> v.intValue().min(1).handle(...))}. Errors are {@link
 * zone.moddev.mc.lib.util.parse.JParseException}s that name the JSON path of the bad value. Kept
 * from the earlier hand-written loaders; current definition loading uses DFU codecs instead.
 */
package zone.moddev.mc.lib.util.parse;
