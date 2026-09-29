package top.ajitech.modrinthpublish;

public enum DependencyType {

    REQUIRED("required"),
    OPTIONAL("optional"),
    INCOMPATIBLE("incompatible"),
    EMBEDDED("embedded");

    private final String apiValue;

    DependencyType(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }
}