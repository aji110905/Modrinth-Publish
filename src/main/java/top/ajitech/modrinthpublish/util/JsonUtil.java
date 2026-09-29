package top.ajitech.modrinthpublish.util;

import com.google.gson.JsonArray;

public final class JsonUtil {
    private JsonUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static JsonArray stringListToJsonArray(Iterable<String> list) {
        JsonArray jsonArray = new JsonArray();
        for (String string : list) {
            jsonArray.add(string);
        }
        return jsonArray;
    }
}
