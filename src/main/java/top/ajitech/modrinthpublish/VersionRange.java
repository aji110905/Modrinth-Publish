package top.ajitech.modrinthpublish;

public final class VersionRange {
    private final McVersion lower;
    private final McVersion upper;

    public VersionRange(McVersion lower, McVersion upper) {
        this.lower = lower;
        this.upper = upper;
    }

    public boolean contains(McVersion version) {
        return lower.compareTo(version) <= 0 && upper.compareTo(version) >= 0;
    }

    @Override
    public String toString() {
        return lower.toString() + " ~ " + upper.toString();
    }
}
