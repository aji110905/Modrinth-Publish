# Modrinth Publish

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/aji110905/Modrinth-Publish/blob/release/LICENSE)

**中文** | [**English**](https://github.com/aji110905/Modrinth-Publish/blob/release/README_en.md)

一个用于将构建产物发布到[Modrinth](https://modrinth.com/)的Gradle插件。

插件会扫描指定目录下的所有文件，**根据文件名**解析出模组版本与 Minecraft 版本，自动生成版本标题、版本号与 `game_versions`，再逐个文件调用 Modrinth Create Version API 创建版本。

底层使用 **Gson** 解析 / 组装 JSON、**OkHttp** 发送网络请求，并将这些依赖打包为 **fat JAR** 随插件一起分发。

## 在其它项目中引用

`settings.gradle`：

```groovy
pluginManagement {
    repositories {
        maven {
            url = 'https://maven.ajitech.top/releases'
        }
        gradlePluginPortal()
    }
}
```

`build.gradle`：

```groovy
plugins {
    id 'top.ajitech.modrinthpublish' version '1.0.0'
}
```

## 配置项

插件通过 `modrinthPublish` 扩展暴露配置。字段分为四类：**自动生成**、**必填**、**固定值**、**可选**。

### 必填配置

| 配置项 | 类型 | 说明 |
|---|---|---|
| `projectId` | `String` | Modrinth 项目 ID。注意：Modrinth 创建版本接口要求使用 **8 位 base62 项目 ID**（如 `P7dR8mSH`），带连字符的项目 slug 会被接口拒绝 |
| `token` | `String` | Modrinth 访问令牌（PAT）。建议不要硬编码，可从环境变量或 Gradle 属性读取 |
| `loaders` | `List<String>` | 模组加载器列表，如 `['fabric']`、`['forge']`、`['neoforge']`、`['quilt']` |
| `environment` | `String` | 版本支持的环境（见下方允许值），必填 |

`environment` 允许值：`client_and_server`、`client_only`、`client_only_server_optional`、`singleplayer_only`、`server_only`、`server_only_client_optional`、`dedicated_server_only`、`client_or_server`、`client_or_server_prefers_both`。

### 可选配置

这些字段**仅当在配置中显式设置时才随请求发送**，未配置一律不传。

| 配置项 | 类型 | 说明 |
|---|---|---|
| `modName` | `String` | 模组显示名称，用于生成版本标题 `name` 的前缀，例如 `My Mod v1.0.0-mc1.21`。未配置时 `name` 直接使用 `version_number` |
| `changelog` | `String` | 版本更新日志 |
| `versionType` | `String` | 发布通道：`release` / `beta` / `alpha` |
| `status` | `String` | 版本状态：`listed` / `archived` / `draft` / `unlisted` / `scheduled` / `unknown` |
| `requestedStatus` | `String` | 请求的状态：`listed` / `archived` / `draft` / `unlisted` |
| `dependencies { }` | 配置块 | 依赖项列表，每项包含 Project ID 与依赖类型，支持配置多个 |
| `fileDirectory` | `String` | 待发布文件所在目录，默认 `build/libs` |
| `maxMinecraftVersion` | `String` | 版本范围计算中末位版本的上界。未配置时通过 `GET /v2/tag/game_version` 实时获取最新正式版（`release`）版本号 |

依赖类型（`dependency_type`）支持：`required`、`optional`、`incompatible`、`embedded`。

### 自动生成 / 固定字段

以下字段**不可配置**，由插件自动处理（向Modrinth发送请求时的是他参数）：

| 字段 | 处理方式 |
|---|---|
| `name` | 自动生成：`{modName} {version_number}`（无 `modName` 时为 `{version_number}`） |
| `version_number` | 自动生成：`v{模组版本}-mc{MC版本}`，如 `v1.0.0-mc1.21` |
| `game_versions` | 自动生成：按文件名推导的 Minecraft 版本范围映射出的正式版列表 |
| `featured` | 固定发送 `false` |
| `file_parts` / `primary_file` | 固定为单个上传文件（模组 jar），字段名固定为 `file` |

### 完整示例

```groovy
plugins {
    id 'top.ajitech.modrinthpublish'
}

modrinthPublish {
    modName = 'My Mod'                       // 可选，用于生成版本标题前缀
    projectId = 'P7dR8mSH'                   // 必填
    // 推荐从环境变量 / Gradle 属性读取，避免硬编码
    token = System.getenv('MODRINTH_TOKEN') ?: project.findProperty('modrinthToken')
    loaders = ['fabric']                     // 必填
    environment = 'client_and_server'        // 必填

    // 以下均为可选，仅当显式设置时才随请求发送
    //changelog = 'List of changes...'
    versionType = 'release'
    // status = 'listed'
    // requestedStatus = 'listed'
    //fileDirectory = 'build/libs'
    // maxMinecraftVersion = '26.3' // 可选，未配置时自动获取最新正式版

    dependencies {
        required 'fabric-api'      // 必需依赖
        optional 'cloth-config'    // 可选依赖
        incompatible 'sodium'      // 不兼容
        embedded 'my-lib'          // 内嵌依赖
    }
}
```

## 使用方法

### 发布到 Modrinth

```bash
./gradlew publishToModrinth
```

任务执行流程：

1. 校验插件应用于 root 项目；
2. 读取 `fileDirectory`（默认 `build/libs`）；
3. 扫描并校验文件：**不允许存在文件夹**，且**每个文件都必须符合命名规则**，否则报错终止；没有任何合规文件时报错终止；
4. 从文件名解析模组标识、模组版本、Minecraft 版本；
5. 计算每个文件覆盖的游戏版本范围；
6. 逐文件调用 `POST https://api.modrinth.com/v2/version` 创建版本并上传文件。

### collectLibs

```bash
./gradlew collectLibs
```

将每个子项目 `build/libs` 下的所有文件与子目录**移动**到根项目的 `build/libs` 目录，方便后续统一发布。

- 无子项目时直接结束，不做任何操作；
- 移动前会检查根项目 `build/libs` 下已存在的条目名，若发生**重名冲突**则报错终止，且不会移动任何内容；
- 移动过程中若失败，会**回滚**已移动的内容，避免目录被部分修改。

### cleanArtifacts

```bash
./gradlew cleanArtifacts
```

清理根项目 `build/libs` 目录，仅保留符合命名规则且**模组版本最新**的文件：

1. 删除所有子目录；
2. 删除不符合文件命名规则的文件；
3. 在剩余符合命名规则的文件中，仅保留模组版本最新的文件，其余删除。

目录不存在或无符合命名规则的文件时，直接结束并提示，不做任何操作。

## 文件命名规则

文件名必须匹配：

```
^.+-v\d+\.\d+\.\d+-mc\d+(\.\d+)*\.jar$
```

即 `{模组标识}-v{模组版本}-mc{Minecraft版本}.jar`：

- `mymod-v1.0.0-mc1.21.jar`
- `mymod-v1.0.0-mc1.21.5.jar`
- `mymod-v2.3.1-mc26.3.jar`

示例目录：

```
build/libs/
├── mymod-v1.0.0-mc1.21.jar
├── mymod-v1.0.0-mc1.21.5.jar
└── mymod-v1.0.0-mc26.3.jar
```

## 游戏版本范围计算

版本号内部被编码为数字（`major×10000 + minor×100 + patch`，如 `1.21.5 → 12105`、`26.3 → 260300`），版本比较即数字比较。对去重升序排序后的版本列表逐个计算覆盖范围：

```
对于排序后的每个版本 V：
  存在下一个版本 V'：上界 = V' 的数字减 1（覆盖到 V' 之前的全部版本），范围 = [V, 上界]
  不存在下一个版本（末位）：上界 = maxMinecraftVersion（未配置时为实时获取的最新正式版），范围 = [V, 上界]
```

假设目录下有 `1.21`、`1.21.5`、`1.21.6`、`1.21.11`、`26.1`、`26.1.2`、`26.3` 七个文件，未配置`maxMinecraftVersion`：

| 当前版本 | 下一版本 | 上界 | 覆盖范围 |
|---|---|---|---|
| 1.21 | 1.21.5 | 1.21.4 | 1.21 ~ 1.21.4 |
| 1.21.5 | 1.21.6 | 1.21.5 | 1.21.5 |
| 1.21.6 | 1.21.11 | 1.21.10 | 1.21.6 ~ 1.21.10 |
| 1.21.11 | 26.1 | 26.0.99 | 1.21.11 ~ 26.0.99 |
| 26.1 | 26.1.2 | 26.1.1 | 26.1 ~ 26.1.1 |
| 26.1.2 | 26.3 | 26.2.99 | 26.1.2 ~ 26.2.99 |
| 26.3 | — | 最新版本 | 26.3 ~ 最新版本 |

> 上界是「下一个版本数字减 1」的数学值（如 `26.2.99`、`26.0.99`），表示「下一个版本之前的所有版本」。范围是闭区间，插件会再从 `GET /v2/tag/game_version` 返回的版本列表中筛选出落在区间内的正式版（`release`）版本字符串作为 `game_versions`，因此 `26.0.99` 这类不存在的中间值不会出现在最终结果里。
>
> 末位文件覆盖到 `maxMinecraftVersion`（默认最新正式版）。当你未来新增更高版本的文件（如 `mc27.1.jar`）时，末位文件的范围会自动收缩到该新文件之前。

## 发布到 Modrinth 的字段

对每个文件调用 `POST /v2/version`，请求为 `multipart/form-data`，包含一个 `data` JSON 部分与一个文件部分。每个版本的请求字段如下：

| 字段 | 类别 | 取值 |
|---|---|---|
| `name` | 自动生成 | `{modName} {version_number}`，例如 `My Mod v1.0.0-mc1.21` |
| `version_number` | 自动生成 | `v{模组版本}-mc{MC版本}`，例如 `v1.0.0-mc1.21` |
| `game_versions` | 自动生成 | 该文件覆盖范围内的有效游戏版本数组 |
| `project_id` | 必填 | 配置的 Modrinth 项目 ID |
| `loaders` | 必填 | 配置的模组加载器 |
| `environment` | 必填 | 配置的环境 |
| `featured` | 固定 | `false` |
| `file_parts` | 固定 | 上传的 `.jar` 文件（单个） |
| `primary_file` | 固定 | 上传的 `.jar` 文件（单个） |
| `changelog` | 可选 | 仅当配置时发送 |
| `dependencies` | 可选 | 仅当配置时发送，每项为 `{ "project_id": "...", "dependency_type": "..." }` |
| `version_type` | 可选 | 仅当配置时发送 |
| `status` | 可选 | 仅当配置时发送 |
| `requested_status` | 可选 | 仅当配置时发送 |

请求头携带 `Authorization: {token}` 认证，并按 Modrinth 要求携带可唯一识别客户端的 `User-Agent`。

## 错误提示

- 文件校验失败会一次性列出**所有**不合规原因（文件夹、不符合命名规则的文件名）；
- 网络请求失败会输出 **HTTP 状态码与响应体**，例如令牌无效时：

```
Publishing version v1.0.0-mc1.21 failed: HTTP 401
Response body: {"error":"unauthorized","description":"Authentication Error: Authentication method was not valid"}
```
