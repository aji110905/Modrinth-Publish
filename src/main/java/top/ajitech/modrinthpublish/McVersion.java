package top.ajitech.modrinthpublish;

import java.util.Objects;

public final class McVersion implements Comparable<McVersion> {

    private static final long MINOR_BASE = 100L;
    private static final long MAJOR_BASE = MINOR_BASE * MINOR_BASE; // 10000

    private final String raw;
    private final long value;

    private McVersion(String raw, long value) {
        this.raw = raw;
        this.value = value;
    }

    public static McVersion parse(String value) {
        String trimmed = Objects.requireNonNull(value, "version").trim();
        String[] parts = trimmed.split("\\.", -1);
        for (String part : parts) {
            if (!part.matches("\\d+")) {
                throw new IllegalArgumentException("Invalid version segment \"" + part + "\" in version: " + value);
            }
        }
        long major = parts.length > 0 ? Long.parseLong(parts[0]) : 0;
        long minor = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
        long patch = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
        return new McVersion(trimmed, major * MAJOR_BASE + minor * MINOR_BASE + patch);
    }

    public McVersion previousOfLastSegment() {
        long previous = value - 1;
        long major = previous / MAJOR_BASE;
        long minor = (previous / MINOR_BASE) % MINOR_BASE;
        long patch = previous % MINOR_BASE;
        return new McVersion(patch != 0 ? major + "." + minor + "." + patch : major + "." + minor, previous);
    }

    @Override
    public int compareTo(McVersion other) {
        return Long.compare(value, other.value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof McVersion version && value == version.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return raw;
    }

    public static boolean isNumeric(String value) {
        return value != null && value.matches("\\d+(\\.\\d+)*");
    }
}
