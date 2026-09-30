package top.ajitech.modrinthpublish;

import top.ajitech.modrinthpublish.util.StringUtil;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParsedArtifact {
    public static final String FILE_NAME_PATTERN = "^(.+)-v(\\d+\\.\\d+\\.\\d+)-mc(\\d+(?:\\.\\d+)*)\\.jar$";
    private static final Pattern PATTERN = Pattern.compile(FILE_NAME_PATTERN);

    private final File file;
    private final String modVersion;
    private final McVersion minecraftVersion;

    private ParsedArtifact(File file, String modVersion, McVersion minecraftVersion) {
        this.file = file;
        this.modVersion = modVersion;
        this.minecraftVersion = minecraftVersion;
    }

    public static boolean matches(String fileName) {
        return PATTERN.matcher(fileName).matches();
    }

    public static ParsedArtifact parse(File file) {
        Matcher matcher = PATTERN.matcher(file.getName());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("File name \"" + file.getName() + "\" does not match the rule " + FILE_NAME_PATTERN);
        }
        return new ParsedArtifact(file, matcher.group(2), McVersion.parse(matcher.group(3)));
    }

    public File getFile() {
        return file;
    }

    public String getModVersion() {
        return modVersion;
    }

    public McVersion getMinecraftVersion() {
        return minecraftVersion;
    }

    public String versionNumber() {
        return "v" + modVersion + "-mc" + minecraftVersion;
    }

    public String displayName(String modName) {
        return StringUtil.isEmpty(modName) ? versionNumber() : modName.trim() + " " + versionNumber();
    }
}
