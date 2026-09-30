package top.ajitech.modrinthpublish;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import top.ajitech.modrinthpublish.util.StringUtil;

@DisableCachingByDefault(because = "The publish task performs network requests, so its results cannot be cached")
public class PublishToModrinthTask extends DefaultTask {
    private final Property<String> modName;
    private final Property<String> projectId;
    private final Property<String> token;
    private final ListProperty<String> loaders;
    private final Property<String> environment;
    private final Property<String> changelog;
    private final Property<String> versionType;
    private final Property<String> status;
    private final Property<String> requestedStatus;
    private final ListProperty<Dependency> dependencies;
    private final Property<String> fileDirectory;
    private final Property<String> maxMinecraftVersion;
    private final DirectoryProperty projectDirectory;

    @Inject
    public PublishToModrinthTask(ObjectFactory objects) {
        this.modName = objects.property(String.class);
        this.projectId = objects.property(String.class);
        this.token = objects.property(String.class);
        this.loaders = objects.listProperty(String.class);
        this.environment = objects.property(String.class);
        this.changelog = objects.property(String.class);
        this.versionType = objects.property(String.class);
        this.status = objects.property(String.class);
        this.requestedStatus = objects.property(String.class);
        this.dependencies = objects.listProperty(Dependency.class);
        this.fileDirectory = objects.property(String.class);
        this.maxMinecraftVersion = objects.property(String.class);
        this.projectDirectory = objects.directoryProperty();
        // 发布任务每次都必须真正执行，不做增量跳过。
        getOutputs().upToDateWhen(task -> false);
    }

    @Optional
    @Input
    public Property<String> getModName() {
        return modName;
    }

    @Input
    public Property<String> getProjectId() {
        return projectId;
    }

    @Internal
    public Property<String> getToken() {
        return token;
    }

    @Input
    public ListProperty<String> getLoaders() {
        return loaders;
    }

    @Input
    public Property<String> getEnvironment() {
        return environment;
    }

    @Optional
    @Input
    public Property<String> getChangelog() {
        return changelog;
    }

    @Optional
    @Input
    public Property<String> getVersionType() {
        return versionType;
    }

    @Optional
    @Input
    public Property<String> getStatus() {
        return status;
    }

    @Optional
    @Input
    public Property<String> getRequestedStatus() {
        return requestedStatus;
    }

    @Internal
    public ListProperty<Dependency> getDependencies() {
        return dependencies;
    }

    @Input
    public Property<String> getFileDirectory() {
        return fileDirectory;
    }

    @Optional
    @Input
    public Property<String> getMaxMinecraftVersion() {
        return maxMinecraftVersion;
    }

    @Internal
    public DirectoryProperty getProjectDirectory() {
        return projectDirectory;
    }

    @TaskAction
    public void publishToModrinth() {
        // 校验必填参数
        String projectId = requireConfigured(getProjectId(), "projectId (Modrinth project ID)");
        String token = requireConfigured(getToken(), "token (Modrinth access token)");
        String environment = requireConfigured(getEnvironment(), "environment");
        List<String> loaders = getLoaders().getOrElse(Collections.emptyList());
        if (loaders.isEmpty()) {
            throw new GradleException("loaders (mod loaders) is not configured, e.g.: loaders = ['fabric']");
        }

        // 扫描文件目录，获取排序后的待发布文件列表
        File directory = getProjectDirectory().dir(getFileDirectory().get()).get().getAsFile();
        if (!directory.exists()) {
            throw new GradleException("File path does not exist: " + directory.getAbsolutePath());
        }
        if (!directory.isDirectory()) {
            throw new GradleException("File path is not a directory: " + directory.getAbsolutePath());
        }
        File[] entries = directory.listFiles();
        if (entries == null) {
            entries = new File[0];
        }
        List<String> directories = new ArrayList<>();
        List<String> invalidFiles = new ArrayList<>();
        List<ParsedArtifact> artifacts = new ArrayList<>();
        for (File entry : entries) {
            if (entry.isDirectory()) {
                directories.add(entry.getName());
                continue;
            }
            if (!ParsedArtifact.matches(entry.getName())) {
                invalidFiles.add(entry.getName());
                continue;
            }
            artifacts.add(ParsedArtifact.parse(entry));
        }
        boolean directoriesEmpty = directories.isEmpty();
        boolean invalidFilesEmpty = invalidFiles.isEmpty();
        if (!directoriesEmpty || !invalidFilesEmpty) {
            StringBuilder message = new StringBuilder("Validation failed for path " + directory.getAbsolutePath() + ":");
            if (!directoriesEmpty) {
                message.append("\n  Folders are not allowed, but found: ").append(String.join(", ", directories));
            }
            if (!invalidFilesEmpty) {
                message.append("\n  The following files do not match the naming rule ").append(ParsedArtifact.FILE_NAME_PATTERN)
                        .append(": ").append(String.join(", ", invalidFiles));
            }
            throw new GradleException(message.toString());
        }
        if (artifacts.isEmpty()) {
            throw new GradleException("No file matching the naming rule (" + ParsedArtifact.FILE_NAME_PATTERN + ") was found under path " + directory.getAbsolutePath() + ".");
        }

        // 校验所有文件的 ModVersion 必须一致
        String modVersion = artifacts.get(0).getModVersion();
        for (ParsedArtifact artifact : artifacts) {
            if (!modVersion.equals(artifact.getModVersion())) {
                throw new GradleException("All files must have the same ModVersion, but found \"" + modVersion + "\" and \"" + artifact.getModVersion() + "\" (file: " + artifact.getFile().getName() + ").");
            }
        }

        artifacts.sort(Comparator.comparing(ParsedArtifact::getMinecraftVersion));

        // 获取所有文件的 Minecraft 版本号并排序
        List<McVersion> versions = new ArrayList<>();
        for (ParsedArtifact artifact : artifacts) {
            if (!versions.contains(artifact.getMinecraftVersion())) {
                versions.add(artifact.getMinecraftVersion());
            }
        }
        versions.sort(null);

        // 创建 Modrinth 客户端
        ModrinthClient client = new ModrinthClient(token);

        // 获取已知的 Minecraft 正式版列表
        List<McVersion> knownVersions = client.fetchReleaseGameVersions();
        if (knownVersions.isEmpty()) {
            throw new GradleException("Failed to fetch any release Minecraft version tag from Modrinth.");
        }

        // 确定最大 Minecraft 版本号（末位版本的上界）
        String configuredMax = getMaxMinecraftVersion().getOrNull();
        boolean isMaxConfigured = !StringUtil.isEmpty(configuredMax);
        McVersion maxVersion = isMaxConfigured ? McVersion.parse(configuredMax) : knownVersions.get(knownVersions.size() - 1);
        getLogger().lifecycle("[modrinth-publish] Max Minecraft version: {} ({})", maxVersion, isMaxConfigured ? "from configuration" : "from Modrinth API");

        // 计算每个文件的 Minecraft 版本范围
        Map<McVersion, VersionRange> ranges = new LinkedHashMap<>();
        for (int i = 0; i < versions.size(); i++) {
            McVersion current = versions.get(i);
            McVersion upper = (i + 1 < versions.size()) ? versions.get(i + 1).previousOfLastSegment() : maxVersion;
            ranges.put(current, new VersionRange(current, upper));
        }
        getLogger().lifecycle("[modrinth-publish] Minecraft version range calculation result:");
        for (Map.Entry<McVersion, VersionRange> entry : ranges.entrySet()) {
            getLogger().lifecycle("  mc{}  ->  {}", entry.getKey(), entry.getValue());
        }

        // 逐文件发布
        int published = 0;
        for (ParsedArtifact artifact : artifacts) {
            // 确定文件的 Minecraft 版本范围，并映射为 game_versions
            VersionRange range = ranges.get(artifact.getMinecraftVersion());
            List<String> gameVersions = new ArrayList<>();
            for (McVersion known : knownVersions) {
                if (range.contains(known)) {
                    gameVersions.add(known.toString());
                }
            }
            if (gameVersions.isEmpty()) {
                throw new GradleException("The version range " + range + " of file " + artifact.getFile().getName()
                        + " contains no release Minecraft version known by Modrinth. Check the maxMinecraftVersion setting and the Minecraft version in the file name.");
            }

            ModrinthClient.VersionRequest request = new ModrinthClient.VersionRequest(
                    artifact.displayName(getModName().getOrNull()),
                    artifact.versionNumber(),
                    gameVersions,
                    projectId,
                    loaders,
                    environment,
                    getChangelog().getOrNull(),
                    getVersionType().getOrNull(),
                    getStatus().getOrNull(),
                    getRequestedStatus().getOrNull(),
                    getDependencies().getOrNull(),
                    artifact.getFile()
            );

            getLogger().lifecycle("[modrinth-publish] Publishing {}.", artifact.getFile().getName());
            String versionId = client.createVersion(request);
            getLogger().lifecycle("[modrinth-publish] Published {}, version Id {}.", artifact.getFile().getName(), versionId);
            published++;
        }

        getLogger().lifecycle("[modrinth-publish] Done, published {} version(s).", published);
    }

    private static String requireConfigured(Property<String> value, String name) {
        String string = value.getOrNull();
        if (StringUtil.isEmpty(string)) {
            throw new GradleException("The required value " + name + " is not configured.");
        }
        return string.trim();
    }

}
