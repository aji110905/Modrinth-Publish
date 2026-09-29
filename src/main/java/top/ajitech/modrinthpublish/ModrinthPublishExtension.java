package top.ajitech.modrinthpublish;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;

public class ModrinthPublishExtension {
    private final DependencyContainer dependencyContainer = new DependencyContainer();

    private String projectId;
    private String token;
    private List<String> loaders = new ArrayList<>();
    private String environment;
    private String modName;

    private String changelog;
    private String versionType;
    private String status;
    private String requestedStatus;

    private String maxMinecraftVersion;
    private String fileDirectory = "build/libs";

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<String> getLoaders() {
        return loaders;
    }

    public void setLoaders(List<String> loaders) {
        this.loaders = loaders;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getModName() {
        return modName;
    }

    public void setModName(String modName) {
        this.modName = modName;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public String getVersionType() {
        return versionType;
    }

    public void setVersionType(String versionType) {
        this.versionType = versionType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestedStatus() {
        return requestedStatus;
    }

    public void setRequestedStatus(String requestedStatus) {
        this.requestedStatus = requestedStatus;
    }

    public String getMaxMinecraftVersion() {
        return maxMinecraftVersion;
    }

    public void setMaxMinecraftVersion(String maxMinecraftVersion) {
        this.maxMinecraftVersion = maxMinecraftVersion;
    }

    public void dependencies(Action<? super DependencyContainer> action) {
        action.execute(dependencyContainer);
    }

    public List<Dependency> getDependencies() {
        return dependencyContainer.getDependencies();
    }

    public String getFileDirectory() {
        return fileDirectory;
    }

    public void setFileDirectory(String fileDirectory) {
        this.fileDirectory = fileDirectory;
    }
}
