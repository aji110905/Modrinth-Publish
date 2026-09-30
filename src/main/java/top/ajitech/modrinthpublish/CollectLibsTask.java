package top.ajitech.modrinthpublish;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "This task moves files on disk, so its result cannot be cached.")
public class CollectLibsTask extends DefaultTask {

    public CollectLibsTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    @TaskAction
    public void collectLibs() {
        //获取根项目
        Project rootProject = getProject().getRootProject();

        //获取根项目build/libs目录
        File rootLibs = new File(rootProject.getLayout().getBuildDirectory().get().getAsFile(), "libs");

        //确保根项目build/libs目录存在
        if (!rootLibs.exists()) {
            boolean created = rootLibs.mkdirs();
            if (!created && !rootLibs.exists()) {
                throw new GradleException("Failed to create directory: " + rootLibs.getAbsolutePath());
            }
        }
        if (!rootLibs.isDirectory()) {
            throw new GradleException("Path is not a directory: " + rootLibs.getAbsolutePath());
        }

        //没有子项目直接返回
        Set<Project> subprojects = rootProject.getSubprojects();
        if (subprojects.isEmpty()) {
            return;
        }

        //扫描根项目build/libs目录下已存在的条目名
        Set<String> occupied = new HashSet<>();
        File[] existing = rootLibs.listFiles();
        if (existing != null) {
            for (File entry : existing) {
                occupied.add(entry.getName());
            }
        }

        //计划移动的条目
        List<MoveEntry> planned = new ArrayList<>();
        for (Project subproject : subprojects) {
            File libs = new File(subproject.getLayout().getBuildDirectory().get().getAsFile(), "libs");
            if (!libs.exists()) {
                continue;
            }
            if (!libs.isDirectory()) {
                throw new GradleException("build/libs is not a directory: " + libs.getAbsolutePath());
            }
            File[] entries = libs.listFiles();
            if (entries == null) {
                continue;
            }
            for (File entry : entries) {
                String name = entry.getName();
                if (occupied.contains(name)) {
                    throw new GradleException("Name conflict: '" + name + "' already exists in " + rootLibs.getAbsolutePath() + ". No content has been moved.");
                }
                occupied.add(name);
                planned.add(new MoveEntry(entry, new File(rootLibs, name)));
            }
        }

        //执行移动
        List<MoveEntry> completed = new ArrayList<>();
        try {
            for (MoveEntry entry : planned) {
                Files.move(entry.source.toPath(), entry.target.toPath());
                completed.add(entry);
            }
        } catch (IOException e) {
            // 回滚已移动的内容
            for (MoveEntry entry : completed) {
                try {
                    Files.move(entry.target.toPath(), entry.source.toPath());
                } catch (IOException ignored) {
                    // 回滚尽力而为
                }
            }
            throw new GradleException("Failed to move build/libs contents, all moved content has been rolled back: " + e.getMessage(), e);
        }
    }


    private record MoveEntry(File source, File target) {
    }
}
