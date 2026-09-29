package top.ajitech.modrinthpublish;

import org.gradle.api.tasks.Input;

public class Dependency {
    private final String projectId;
    private final DependencyType type;

    public Dependency(String projectId, DependencyType type) {
        this.projectId = projectId;
        this.type = type;
    }

    @Input
    public String getProjectId() {
        return projectId;
    }

    @Input
    public DependencyType getType() {
        return type;
    }
}