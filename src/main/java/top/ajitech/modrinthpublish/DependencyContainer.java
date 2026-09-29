package top.ajitech.modrinthpublish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class DependencyContainer {
    private final List<Dependency> dependencies = new ArrayList<>();

    public void required(String projectId) {
        add(projectId, DependencyType.REQUIRED);
    }

    public void optional(String projectId) {
        add(projectId, DependencyType.OPTIONAL);
    }

    public void incompatible(String projectId) {
        add(projectId, DependencyType.INCOMPATIBLE);
    }

    public void embedded(String projectId) {
        add(projectId, DependencyType.EMBEDDED);
    }

    public void add(String projectId, DependencyType type) {
        if (projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalArgumentException("The projectId of a dependency must not be empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("The dependency type of a dependency must not be empty");
        }
        dependencies.add(new Dependency(projectId.trim(), type));
    }

    public List<Dependency> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }
}