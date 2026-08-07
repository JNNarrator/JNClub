# JNClub 设计文档

## 一、总体架构

前后端分离的单体 Web 应用，配合独立的 SSO 服务与文件服务。

```
浏览器 (Vue3 SPA)
   │  /jnclub/* (静态)
   ▼
nginx / Vite dev 代理
   │  /api、/sso → 后端
   ▼
JNClub 后端 (Spring Boot :19005)
   │  ┌─────────────────────────────────┐
   │  │ jnclub-common  SSO/异常/统一返回  │
   │  │ jnclub-module-bookmark 业务      │
   │  └─────────────────────────────────┘
   ├──► MySQL :3306 (jnclub)
   └──► dufs :8000 (文件存储)
            ├── jnclub/images/  (便签图片)
            └── jnclub/disk/    (云盘文件)
```

### 分层与模块

- **jnclub-common**：Sa-Token 拦截器（`WebMvcConfig`）、SSO 客户端、统一返回 `R<T>`、全局异常、跨域、静态资源。
- **jnclub-module-bookmark**：核心业务。目录 / 收藏 / 便签 / 云盘 / 图片资产。
- **jnclub-gateway**：启动入口（:19005），含 `application.yml`。

### 关键约定

1. **鉴权**：`WebMvcConfig` 的 Sa-Token 拦截器拦截 `/api/**`，`/api/files/**` 与 `/sso/*` 放行。
2. **统一返回**：所有接口返回 `R<T>`（`code`/`message`/`data`），业务异常抛 `BizException`。
3. **不暴露内网存储**：dufs 地址只在后端配置（`jnclub.dufs.base-url`），前端经 `/api/files/**` 反向代理读取，浏览器不接触内网地址。

---

## 二、目录体系（多模块复用）

`t_directory` 按 `type` 区分所属模块，一套目录树服务所有模块：

| type | 模块 |
|---|---|
| 1 | 收藏夹 |
| 2 | 便签 |
| 3 | 云盘 |

- `DirectoryService` 提供树构建、级联删除（含删除保护）、排序、内容计数。
- `getContentCount` 按后代目录统计 `bookmarkCount / noteCount / fileCount`，`fileCount` 来自 `t_file`。
- `deleteDirectory` 在存在任一内容（收藏/便签/文件）时拒绝删除，防止误删。

> 新增一个模块 = 复用目录能力，只需扩展一个新 `type`。

---

## 三、云盘：分片上传 + 断点续传（核心设计）

### 3.1 需求与约束

- 仅单文件上传。
- 服务器**带宽小且不稳定**，必须**断点续传、失败可恢复**。
- dufs 仅支持整体 PUT，**不支持**分片续写（PATCH/append），因此断点续传落在后端。
- 上传带宽瓶颈在「浏览器 → 服务器」公网段；「后端 → dufs」为内网，很快。

### 3.2 数据流

```
前端切片(userId 会话)
   │  ① init          ──► 生成 uploadId + 分片参数
   │  ② status        ──► 查询已落盘分片（断点续传跳过）
   │  ③ chunk×N       ──► 分片写后端本地临时目录（幂等）
   │  ④ complete      ──► 校验完整性 → 合并 → 一次性 PUT dufs → 入库 → 清理临时
   ▼
JNClub 后端
   └── {disk.temp-dir}/{userId}/{uploadId}/chunks/*.part
JNClub 后端
   └── dufs /jnclub/disk/yyyy/MM/dd/{uuid}.{ext}
   └── MySQL t_file
```

### 3.3 分片协议

| 接口 | 说明 | 关键字段 |
|---|---|---|
| `POST /upload/init` | 初始化 | `filename, size, directoryId, chunkSizeMb` → 返回 `uploadId, chunkSize, totalChunks` |
| `POST /upload/chunk` | 上传分片 | multipart `uploadId, chunkIndex, file` |
| `GET /upload/status` | 查询进度 | `uploadId` → 返回 `uploaded[]`（已落盘分片）+ `totalChunks` |
| `POST /upload/complete` | 合并完成 | `uploadId` → 返回 `t_file` 记录 |

`totalChunks = ceil(totalSize / chunkBytes)`，`chunkBytes = chunkSizeMb * 1024 * 1024`。

### 3.4 稳定性设计要点

1. **小分片**：默认 2MB，弱网单分片更易成功、重传成本低。
2. **幂等接收**：分片已存在（`.part` 非空）直接返回成功；先写 `.tmp` 再原子改名，避免半截文件被当成成功分片。
3. **乱序支持**：不要求顺序到达，`complete` 时按 index 顺序合并。
4. **云端进度为准**：`status` 从服务端临时目录统计已落盘分片，刷新/换端都能准确续传，不依赖本地 localStorage。
5. **一次性推 dufs**：`complete` 时校验 `分片数齐 + size 总和 == totalSize` 通过后才合并并 PUT；失败不产生半成品对象。
6. **临时区生命周期**：`complete` 成功后即删除整个 uploadId 临时目录；每日凌晨 `@Scheduled` 清理超过 1 天的孤儿临时目录（防残留占盘）。
7. **前端重试**：单片失败指数退避重试（最多 3 次），支持手动暂停/恢复。

### 3.5 文件管理

- `t_file` 登记：`directory_id, user_id, original_name, stored_key, url, size, mime`。
- 读：`/api/files/{stored_key}` 反向代理 dufs（只读、免登录）。
- 下载：`GET /api/clouddisk/files/{id}/download` 按 `original_name` 设置 `Content-Disposition: attachment; filename*=UTF-8''...`，还原原始文件名（支持中文）。
- 删除：删 dufs 对象 + 删 `t_file` 记录。
- 目录删除保护：目录及其后代含 `t_file` 时禁止删除。

### 3.6 关键实现文件

| 文件 | 职责 |
|---|---|
| `service/CloudDiskService.java` | 分片/合并/续传/列表/删除/下载/清理（核心） |
| `controller/CloudDiskController.java` | `/api/clouddisk/**` 接口 |
| `entity/FileRecord.java` / `mapper/FileMapper.java` | `t_file` 持久层 |
| 前端 `composables/useChunkedUpload.ts` | 切片 + 续传 + 暂停/恢复 + 退避重试 |
| 前端 `components/DiskView.vue` | 云盘上传进度 + 文件列表 |
| 前端 `stores/clouddisk.ts` | 文件列表 CRUD store |

---

## 四、图片资产（便签）设计

- 上传 `POST /api/upload/image`：白名单校验 MIME（仅图片）、大小（默认 10MB），生成随机文件名 + `yyyy/MM/dd` 分层，PUT 到 dufs，登记 `t_note_asset`。
- 认领：保存便签时扫描 Markdown 中 `/api/files/...` 引用，将资产与 `note_id` 绑定。
- 清理：`AssetCleanService` 定时（每日 03:00）清理未被任何便签引用的孤儿图片。

---

## 五、元数据表

| 表 | 说明 |
|---|---|
| `t_directory` | 目录（`type` 区分模块） |
| `t_bookmark` | 网页收藏 |
| `t_note` | 便签 |
| `t_note_asset` | 便签图片资源审计 |
| `t_file` | 云盘文件 |
| `t_user_preference` | 用户偏好 KV（模块/视图/目录记忆） |

---

## 六、配置项速查

| 配置 | 默认 | 说明 |
|---|---|---|
| `jnclub.dufs.base-url` | - | dufs 内网地址（本地/线上） |
| `jnclub.dufs.disk-path` | `/jnclub/disk/` | 云盘存储前缀 |
| `jnclub.disk.max-size-mb` | 500 | 云盘单文件上限 |
| `jnclub.disk.chunk-size-mb` | 2 | 分片大小（MB） |
| `jnclub.disk.temp-dir` | `<tmp>/jnclub-upload` | 分片暂存目录 |
| `spring.servlet.multipart.max-file-size` | 10MB | 必须 ≥ 分片大小 |
