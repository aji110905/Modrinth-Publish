# Modrinth Publish

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/aji110905/Modrinth-Publish/blob/release/LICENSE)

[**中文**](https://github.com/aji110905/Modrinth-Publish/blob/release/README.md) | **English**

A Gradle plugin for publishing build artifacts to [Modrinth](https://modrinth.com/).

The plugin scans all files in a specified directory, parses the **mod version** and **Minecraft version** from the **file name**, automatically generates the version title, version number, and `game_versions`, then calls the Modrinth Create Version API for each file to create a version.

Under the hood it uses **Gson** to parse / assemble JSON and **OkHttp** to send network requests, and bundles these dependencies into a **fat JAR** that ships with the plugin.

## Using the plugin in other projects

`settings.gradle`:

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

`build.gradle`:

```groovy
plugins {
    id 'top.ajitech.modrinthpublish' version '1.0.0'
}
```

## Configuration

The plugin exposes configuration through the `modrinthPublish` extension. Fields are grouped into four categories: **auto-generated**, **required**, **fixed**, and **optional**.

### Required configuration

| Option | Type | Description |
|---|---|---|
| `projectId` | `String` | The Modrinth project ID. Note: the Modrinth Create Version API requires the **8-character base62 project ID** (e.g. `P7dR8mSH`); a hyphenated project slug will be rejected |
| `token` | `String` | Your Modrinth personal access token (PAT). It is recommended not to hardcode it — read it from an environment variable or a Gradle property instead |
| `loaders` | `List<String>` | The list of mod loaders, e.g. `['fabric']`, `['forge']`, `['neoforge']`, `['quilt']` |
| `environment` | `String` | The environment(s) the version supports (see allowed values below). Required |

Allowed values for `environment`: `client_and_server`, `client_only`, `client_only_server_optional`, `singleplayer_only`, `server_only`, `server_only_client_optional`, `dedicated_server_only`, `client_or_server`, `client_or_server_prefers_both`.

### Optional configuration

These fields are **only sent with the request when explicitly set in the configuration**; if left unset they are omitted entirely.

| Option | Type | Description |
|---|---|---|
| `modName` | `String` | The mod display name, used as the prefix when generating the version title `name`, e.g. `My Mod v1.0.0-mc1.21`. When unset, `name` falls back to `version_number` |
| `changelog` | `String` | The changelog for this version |
| `versionType` | `String` | The release channel: `release` / `beta` / `alpha` |
| `status` | `String` | The version status: `listed` / `archived` / `draft` / `unlisted` / `scheduled` / `unknown` |
| `requestedStatus` | `String` | The requested status: `listed` / `archived` / `draft` / `unlisted` |
| `dependencies { }` | Config block | A list of dependencies, each containing a Project ID and a dependency type; multiple can be configured |
| `fileDirectory` | `String` | The directory containing the files to publish, defaults to `build/libs` |
| `maxMinecraftVersion` | `String` | The upper bound of the trailing version in the range calculation. When unset, the latest release version is fetched in real time via `GET /v2/tag/game_version` |

Supported dependency types (`dependency_type`): `required`, `optional`, `incompatible`, `embedded`.

### Auto-generated / fixed fields

The following fields **cannot be configured** and are handled automatically by the plugin (these are the parameters sent in the request to Modrinth):

| Field | Handling |
|---|---|
| `name` | Auto-generated: `{modName} {version_number}` (or `{version_number}` when no `modName`) |
| `version_number` | Auto-generated: `v{mod version}-mc{MC version}`, e.g. `v1.0.0-mc1.21` |
| `game_versions` | Auto-generated: the list of release versions mapped from the Minecraft version range derived from the file name |
| `featured` | Always sends `false` |
| `file_parts` / `primary_file` | Always a single uploaded file (the mod JAR); the field name is fixed to `file` |

### Full example

```groovy
plugins {
    id 'top.ajitech.modrinthpublish'
}

modrinthPublish {
    modName = 'My Mod'                       // Optional, used as the version title prefix
    projectId = 'P7dR8mSH'                   // Required
    // Recommended: read from an environment variable / Gradle property to avoid hardcoding
    token = System.getenv('MODRINTH_TOKEN') ?: project.findProperty('modrinthToken')
    loaders = ['fabric']                     // Required
    environment = 'client_and_server'        // Required

    // The following are all optional and only sent when explicitly set
    //changelog = 'List of changes...'
    versionType = 'release'
    // status = 'listed'
    // requestedStatus = 'listed'
    //fileDirectory = 'build/libs'
    // maxMinecraftVersion = '26.3' // Optional; auto-fetches the latest release when unset

    dependencies {
        required 'fabric-api'      // Required dependency
        optional 'cloth-config'    // Optional dependency
        incompatible 'sodium'      // Incompatible
        embedded 'my-lib'          // Embedded dependency
    }
}
```

## Usage

### Publish to Modrinth

```bash
./gradlew publishToModrinth
```

Task execution flow:

1. Verify that the plugin is applied to the root project;
2. Read `fileDirectory` (defaults to `build/libs`);
3. Scan and validate the files: **subdirectories are not allowed**, and **every file must conform to the naming rule**, otherwise the task aborts with an error; if there is no valid file at all, it also aborts;
4. Parse the mod identifier, mod version, and Minecraft version from each file name;
5. Compute the game version range covered by each file;
6. For each file, call `POST https://api.modrinth.com/v2/version` to create the version and upload the file.

### collectLibs

```bash
./gradlew collectLibs
```

Moves all files and subdirectories under each subproject's `build/libs` into the root project's `build/libs` directory, so they can later be published together.

- If there are no subprojects, it returns immediately without doing anything;
- Before moving, it checks the entry names already present under the root `build/libs`; on a **name conflict** it aborts with an error and moves nothing;
- If a failure occurs during the move, the already-moved entries are **rolled back** so the directory is never left partially modified.

### cleanArtifacts

```bash
./gradlew cleanArtifacts
```

Cleans the root project's `build/libs` directory, keeping only files that conform to the naming rule and have the **latest mod version**:

1. Deletes all subdirectories;
2. Deletes files that do not match the file naming rule;
3. Among the remaining conforming files, keeps only those with the latest mod version and deletes the rest.

If the directory does not exist or no conforming file remains, it returns with a message and does nothing.

## File naming rule

The file name must match:

```
^.+-v\d+\.\d+\.\d+-mc\d+(\.\d+)*\.jar$
```

That is, `{mod identifier}-v{mod version}-mc{Minecraft version}.jar`:

- `mymod-v1.0.0-mc1.21.jar`
- `mymod-v1.0.0-mc1.21.5.jar`
- `mymod-v2.3.1-mc26.3.jar`

Example directory:

```
build/libs/
├── mymod-v1.0.0-mc1.21.jar
├── mymod-v1.0.0-mc1.21.5.jar
└── mymod-v1.0.0-mc26.3.jar
```

## Game version range calculation

Version numbers are encoded internally as numbers (`major×10000 + minor×100 + patch`, e.g. `1.21.5 → 12105`, `26.3 → 260300`), so comparing versions is simply a numeric comparison. After deduplicating and sorting the version list in ascending order, the covered range is computed for each version:

```
For each version V in the sorted list:
  If a next version V' exists: upper bound = V' number minus 1 (covers everything before V'), range = [V, upper bound]
  If no next version (the last one): upper bound = maxMinecraftVersion (the latest release fetched in real time when unset), range = [V, upper bound]
```

Assume the directory contains seven files: `1.21`, `1.21.5`, `1.21.6`, `1.21.11`, `26.1`, `26.1.2`, `26.3`, not configured `maxMinecraftVersion`:

| Current version | Next version | Upper bound | Covered range |
|---|---|---|---|
| 1.21 | 1.21.5 | 1.21.4 | 1.21 ~ 1.21.4 |
| 1.21.5 | 1.21.6 | 1.21.5 | 1.21.5 |
| 1.21.6 | 1.21.11 | 1.21.10 | 1.21.6 ~ 1.21.10 |
| 1.21.11 | 26.1 | 26.0.99 | 1.21.11 ~ 26.0.99 |
| 26.1 | 26.1.2 | 26.1.1 | 26.1 ~ 26.1.1 |
| 26.1.2 | 26.3 | 26.2.99 | 26.1.2 ~ 26.2.99 |
| 26.3 | — | latest | 26.3 ~ latest |

> The upper bound is the mathematical value of "next version number minus 1" (e.g. `26.2.99`, `26.0.99`), meaning "all versions before the next version". The range is a closed interval, and the plugin further filters the version list returned by `GET /v2/tag/game_version` to select only the release (`release`) version strings that fall within the interval as `game_versions`, so non-existent intermediate values like `26.0.99` never appear in the final result.
>
> The trailing file covers up to `maxMinecraftVersion` (the latest release by default). When you later add a file with a higher version (e.g. `mc27.1.jar`), the trailing file's range automatically shrinks to just before that new file.

## Fields sent to Modrinth

For each file, `POST /v2/version` is called with a `multipart/form-data` request containing a `data` JSON part and a file part. The request fields for each version are as follows:

| Field | Category | Value |
|---|---|---|
| `name` | Auto-generated | `{modName} {version_number}`, e.g. `My Mod v1.0.0-mc1.21` |
| `version_number` | Auto-generated | `v{mod version}-mc{MC version}`, e.g. `v1.0.0-mc1.21` |
| `game_versions` | Auto-generated | The array of valid game versions within the file's covered range |
| `project_id` | Required | The configured Modrinth project ID |
| `loaders` | Required | The configured mod loaders |
| `environment` | Required | The configured environment |
| `featured` | Fixed | `false` |
| `file_parts` | Fixed | The uploaded `.jar` file (single) |
| `primary_file` | Fixed | The uploaded `.jar` file (single) |
| `changelog` | Optional | Sent only when configured |
| `dependencies` | Optional | Sent only when configured, each entry as `{ "project_id": "...", "dependency_type": "..." }` |
| `version_type` | Optional | Sent only when configured |
| `status` | Optional | Sent only when configured |
| `requested_status` | Optional | Sent only when configured |

The request header carries `Authorization: {token}` for authentication, along with a `User-Agent` that uniquely identifies the client as required by Modrinth.

## Error messages

- On file validation failure, **all** invalid reasons (subdirectories, file names that do not match the naming rule) are listed at once;
- On network request failure, the **HTTP status code and response body** are printed, for example when the token is invalid:

```
Publishing version v1.0.0-mc1.21 failed: HTTP 401
Response body: {"error":"unauthorized","description":"Authentication Error: Authentication method was not valid"}
```
