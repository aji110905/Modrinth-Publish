package top.ajitech.modrinthpublish;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "This task deletes files on disk, so its result cannot be cached.")
public class CleanArtifactsTask extends DefaultTask {

    public CleanArtifactsTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    @TaskAction
    public void cleanArtifacts() {
        // 获取根项目
        Project rootProject = getProject().getRootProject();

        // 获取根项目 build/libs 目录
        File rootLibs = new File(rootProject.getLayout().getBuildDirectory().get().getAsFile(), "libs");

        // 目录不存在时无需清理
        if (!rootLibs.exists()) {
            getLogger().lifecycle("[modrinth-publish] Directory does not exist, nothing to clean: {}", rootLibs.getAbsolutePath());
            return;
        }
        if (!rootLibs.isDirectory()) {
            throw new GradleException("Path is not a directory: " + rootLibs.getAbsolutePath());
        }

        // 获取目录下的所有文件和子目录
        File[] entries = rootLibs.listFiles();
        if (entries == null) {
            return;
        }

        // 暂存符合命名规则的文件
        List<ParsedArtifact> artifacts = new ArrayList<>();
        for (File entry : entries) {
            if (!entry.isDirectory() && ParsedArtifact.matches(entry.getName())) {
                artifacts.add(ParsedArtifact.parse(entry));
            } else {
                deleteRecursively(entry);
            }
        }

        // 如果没有符合命名规则的文件则无需清理
        if (artifacts.isEmpty()) {
            getLogger().lifecycle("[modrinth-publish] No file matching the naming rule ({}) remains under {}.", ParsedArtifact.FILE_NAME_PATTERN, rootLibs.getAbsolutePath());
            return;
        }

        // 确定最新的 mod version
        McVersion latestVersion = artifacts.stream()
                .map(artifact -> McVersion.parse(artifact.getModVersion()))
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new GradleException("Failed to determine the latest mod version."));
        getLogger().lifecycle("[modrinth-publish] Latest mod version: {}", latestVersion);

        // 仅保留最新 mod version 对应的文件，删除其余文件
        for (ParsedArtifact artifact : artifacts) {
            if (McVersion.parse(artifact.getModVersion()).compareTo(latestVersion) != 0) {
                deleteRecursively(artifact.getFile());
            }
        }
    }

    private void deleteRecursively(File file) {
        Path path = file.toPath();
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.walk(path)) {
                    // 确保子项先于父目录被删除
                    List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                    for (Path p : paths) {
                        Files.delete(p);
                    }
                }
            } else {
                Files.delete(path);
            }
            getLogger().lifecycle("[modrinth-publish] Deleted: {}", file.getAbsolutePath());
        } catch (IOException e) {
            throw new GradleException("Failed to delete: " + file.getAbsolutePath() + " (" + e.getMessage() + ")", e);
        }
    }
}
