package top.ajitech.modrinthpublish.util;

public final class StringUtil {
    private StringUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static boolean isEmpty(String string) {
        return string == null || string.isEmpty();
    }
}
