package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void parsesPrimitives() {
        assertNull(Json.parse("null"));
        assertEquals(Boolean.TRUE, Json.parse("true"));
        assertEquals(Boolean.FALSE, Json.parse("false"));
        assertEquals(123, Json.parse("123"));
        assertEquals(-456, Json.parse("-456"));
        assertEquals(3.1415, (Double) Json.parse("3.1415"), 0.0001);
        assertEquals("hello world\n\t\"quote\"", Json.parse("\"hello world\\n\\t\\\"quote\\\"\""));
        assertEquals("unicode \u0041\u0042", Json.parse("\"unicode \\u0041\\u0042\""));
    }

    @Test
    void parsesObjectsAndArrays() {
        String json = """
            {
                // Line comment
                "name": "IMUL",
                "family": "F-02",
                /* Block
                   comment */
                "sizes": [8, 16, 32],
                "active": true,
                "nullVal": null,
                "nested": {
                    "sub": "val"
                }
            }
            """;
        Map<String, Object> map = Json.parseObject(json);
        assertEquals("IMUL", map.get("name"));
        assertEquals("F-02", map.get("family"));
        assertEquals(Boolean.TRUE, map.get("active"));
        assertNull(map.get("nullVal"));
        assertEquals(List.of(8, 16, 32), map.get("sizes"));
        Map<String, Object> nested = Json.getObject(map, "nested");
        assertNotNull(nested);
        assertEquals("val", nested.get("sub"));
    }

    @Test
    void stringifiesAndRoundTrips() {
        Map<String, Object> obj = Map.of(
                "key", "val",
                "num", 42,
                "list", List.of("a", "b", "c")
        );
        String s = Json.stringify(obj);
        Map<String, Object> parsed = Json.parseObject(s);
        assertEquals("val", parsed.get("key"));
        assertEquals(42, parsed.get("num"));
        assertEquals(List.of("a", "b", "c"), parsed.get("list"));
    }
}
