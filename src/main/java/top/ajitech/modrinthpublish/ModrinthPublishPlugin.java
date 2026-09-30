package top.ajitech.modrinthpublish;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ModrinthPublishPlugin implements Plugin<Project> {
    public static final String EXTENSION_NAME = "modrinthPublish";
    public static final String TASK_NAME = "publishToModrinth";
    public static final String COLLECT_LIBS_TASK_NAME = "collectLibs";
    public static final String CLEAN_ARTIFACTS_TASK_NAME = "cleanArtifacts";

    @Override
    public void apply(Project project) {
        //确保插件只能应用于 root 项目，应用于子项目时在配置阶段直接报错终止。
        if (project != project.getRootProject()) {
            throw new GradleException("The modrinth-publish plugin can only be applied to the root project, but it was applied to subproject \"" + project.getPath() + "\". Apply the plugin in the build script of the root project.");
        }

        ModrinthPublishExtension extension = project.getExtensions().create(EXTENSION_NAME, ModrinthPublishExtension.class);

        project.getTasks().register(TASK_NAME, PublishToModrinthTask.class, task -> {
            task.setGroup("publishing");
            task.setDescription("Publishes the build artifacts under the configured path to Modrinth, one file at a time, via the Create Version API.");
            task.getModName().set(project.provider(extension::getModName));
            task.getToken().set(project.provider(extension::getToken));
            task.getProjectId().set(project.provider(extension::getProjectId));
            task.getLoaders().set(project.provider(extension::getLoaders));
            task.getEnvironment().set(project.provider(extension::getEnvironment));
            task.getChangelog().set(project.provider(extension::getChangelog));
            task.getVersionType().set(project.provider(extension::getVersionType));
            task.getStatus().set(project.provider(extension::getStatus));
            task.getRequestedStatus().set(project.provider(extension::getRequestedStatus));
            task.getDependencies().set(project.provider(extension::getDependencies));
            task.getFileDirectory().set(project.provider(extension::getFileDirectory));
            task.getMaxMinecraftVersion().set(project.provider(extension::getMaxMinecraftVersion));
            task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
        });

        project.getTasks().register(COLLECT_LIBS_TASK_NAME, CollectLibsTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Moves all files and directories under each subproject's build/libs recursively into the root project's build/libs directory.");
        });

        project.getTasks().register(CLEAN_ARTIFACTS_TASK_NAME, CleanArtifactsTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Deletes all subdirectories and non-conforming files under the root project's build/libs, and among the remaining files keeps only those with the latest mod version.");
        });
    }
}
