package top.ajitech.modrinthpublish;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.gradle.api.GradleException;
import top.ajitech.modrinthpublish.util.JsonUtil;
import top.ajitech.modrinthpublish.util.StringUtil;

public class ModrinthClient {

    public static final String API_BASE_URL = "https://api.modrinth.com/v2/";

    private static final String USER_AGENT = "aji/modrinth-publish-plugin/" + resolvePluginVersion() + " (Gradle plugin)";

    public static final String FILE_PART_NAME = "file";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final MediaType JAR_MEDIA_TYPE = MediaType.parse("application/java-archive");

    private final String token;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    public ModrinthClient(String token) {
        this.token = token;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 从构建期注入的资源文件读取插件版本号，用于生成 User-Agent；读取失败时回退为 unknown。
     */
    private static String resolvePluginVersion() {
        try (InputStream in = ModrinthClient.class.getResourceAsStream("plugin-version.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }

    public List<McVersion> fetchReleaseGameVersions() {
        //构建请求
        Request request = new Request.Builder()
                .url(API_BASE_URL + "tag/game_version")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build();

        //发送请求并获取响应
        String responseBody = execute(request, "Fetching Modrinth game version tags");

        // 解析 JSON 响应
        JsonArray tags = gson.fromJson(responseBody, JsonArray.class);
        if (tags == null) {
            throw new GradleException("Unexpected Modrinth game version tag response format: " + responseBody);
        }

        // 解析版本号为List
        Set<String> seen = new LinkedHashSet<>();
        List<McVersion> versions = new ArrayList<>();
        for (JsonElement item : tags) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject tag = item.getAsJsonObject();
            JsonElement version = tag.get("version");
            JsonElement versionType = tag.get("version_type");
            if (version == null || version.isJsonNull() || !McVersion.isNumeric(version.getAsString())) {
                continue;
            }
            if (versionType == null || !"release".equals(versionType.getAsString())) {
                continue;
            }
            // 去重
            if (seen.add(version.getAsString())) {
                versions.add(McVersion.parse(version.getAsString()));
            }
        }
        versions.sort(null);
        return versions;
    }

    public String createVersion(VersionRequest request) {
        //组装请求 JSON
        JsonObject data = new JsonObject();

        data.addProperty("name", request.name());
        data.addProperty("version_number", request.versionNumber());
        data.add("game_versions", JsonUtil.stringListToJsonArray(request.gameVersions()));
        data.addProperty("project_id", request.projectId());
        data.add("loaders", JsonUtil.stringListToJsonArray(request.loaders()));
        data.addProperty("environment", request.environment());
        data.addProperty("featured", false);
        JsonArray fileParts = new JsonArray();
        fileParts.add(FILE_PART_NAME);
        data.add("file_parts", fileParts);
        data.addProperty("primary_file", FILE_PART_NAME);

        //可选参数仅当显式配置时才发送
        if (!StringUtil.isEmpty(request.changelog())) {
            data.addProperty("changelog", request.changelog());
        }
        if (!StringUtil.isEmpty(request.versionType())) {
            data.addProperty("version_type", request.versionType());
        }
        if (!StringUtil.isEmpty(request.status())) {
            data.addProperty("status", request.status());
        }
        if (!StringUtil.isEmpty(request.requestedStatus())) {
            data.addProperty("requested_status", request.requestedStatus());
        }
        if (request.dependencies() != null && !request.dependencies().isEmpty()) {
            JsonArray dependenciesArray = new JsonArray();
            for (Dependency dependency : request.dependencies()) {
                JsonObject dependencyObject = new JsonObject();
                dependencyObject.addProperty("project_id", dependency.getProjectId());
                dependencyObject.addProperty("dependency_type", dependency.getType().getApiValue());
                dependenciesArray.add(dependencyObject);
            }
            data.add("dependencies", dependenciesArray);
        }

        //构建 multipart 请求体
        RequestBody dataBody = RequestBody.create(gson.toJson(data), JSON_MEDIA_TYPE);
        RequestBody fileBody = RequestBody.create(request.file(), JAR_MEDIA_TYPE);
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", null, dataBody)
                .addFormDataPart(FILE_PART_NAME, request.file().getName(), fileBody)
                .build();

        //构建 HTTP 请求
        Request httpRequest = new Request.Builder()
                .url(API_BASE_URL + "version")
                .header("Authorization", token)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .post(body)
                .build();

        //发送请求并解析响应，返回新版本 ID
        String responseBody = execute(httpRequest, "Publishing version " + request.versionNumber());

        // 解析 JSON 响应并提取版本 ID
        return gson.fromJson(responseBody, JsonObject.class).get("id").getAsString();
    }

    private String execute(Request request, String description) {
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new GradleException(description + " failed: HTTP " + response.code() + "\nResponse body: " + responseBody);
            }
            return responseBody;
        } catch (IOException e) {
            throw new GradleException(description + " failed: network error - " + e.getMessage(), e);
        }
    }

    public record VersionRequest(
            String name,
            String versionNumber,
            List<String> gameVersions,
            String projectId,
            List<String> loaders,
            String environment,
            String changelog,
            String versionType,
            String status,
            String requestedStatus,
            List<Dependency> dependencies,
            File file
    ) {
    }
}
