# JNClub - 个人工作台服务

JNClub 是一个前后端分离的个人工作台 Web 服务，包含「收藏夹」「便签」「云盘」「密码库」四大模块，并配套「回收站」（软删除统一管理）、「浏览器收藏助手」（Chrome 扩展）、内嵌「音乐」模块（JNMUSIC 融合，对外路径 `/music/`）与「WebDAV」站点管理模块（站内简单文件管理）。

## 技术栈

### 后端
- Spring Boot 4.0.0 (JDK 21)
- MySQL 8.x
- MyBatis-Plus
- Sa-Token SSO（对接 JN_SSO 单点登录）
- Hutool（dufs 上传/下载 HTTP 调用）
- dufs 文件服务器（内网存储）

### 前端
- Vue 3 + TypeScript + Vite
- Naive UI
- Pinia
- md-editor-v3
- sortablejs（拖拽排序）

## 项目结构

```
JNClub/
├── pom.xml                    # 父 POM
├── jnclub-common/             # 公共模块（SSO、异常、CORS、统一返回 R）
├── jnclub-module-bookmark/    # 业务模块（目录/收藏/便签/云盘/密码库/标签/回收站/上传）
├── jnclub-module-music/       # 音乐模块（JNMUSIC 并入：/music/api 匿名 API，music_* 表）
├── jnclub-gateway/            # API 网关（启动入口，端口 19005）
├── jnclub-web/                # 主前端项目（Vue3，base /jnclub/，侧边栏含四大模块/回收站/音乐/插件入口）
├── music-frontend/            # 音乐播放器前端（Vue3，base /music/，nginx 静态托管）
├── browser-extension/         # 浏览器收藏助手（Chrome 扩展，一键/批量收藏）
└── docs/                      # 文档（init.sql / 部署 / 设计）
```

> **音乐模块**：后端为 `jnclub-module-music`，对外 URL 保持 `/music/api/v1/...`（内部由过滤器重写为 `/api/v1/...`），接口匿名访问（按 `X-Device-Id` 隔离用户数据）；数据存放于 `jnclub` 库 `music_*` 表（`music_track` / `music_lyrics_cache` / `music_user_favorite` / `music_play_history` / `music_search_history` / `music_play_queue`），建表脚本见 `jnclub-module-music/src/main/resources/schema.sql`。音乐依赖蓝奏云（`lanzou.client.*`）与 dufs（`jnmusic.file-server.*`）配置，见 `jnclub-module-music/src/main/resources/application.properties`。

## 快速开始

> 详细步骤见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

### 1. 初始化数据库

```bash
mysql -u root -p < docs/init.sql
```

> 若为已有数据库升级，请执行 `docs/init.sql` 底部「迁移脚本」一节。

### 2. 启动文件服务器 dufs（存储后端）

本地使用 launchctl 托管（见 `本地服务操作/start_dufs.sh`），默认端口 8000：

```bash
./本地服务操作/start_dufs.sh
curl http://localhost:8000/   # 验证
```

### 3. 启动后端

```bash
mvn clean package -DskipTests
java -jar jnclub-gateway/target/jnclub-gateway-1.0.0-SNAPSHOT.jar
```

后端将在 http://localhost:19005 启动（登录鉴权 401 属正常）

### 4. 启动前端

```bash
cd jnclub-web
npm install
npm run dev
```

前端将在 http://localhost:5173/jnclub/ 启动

## SSO 配置

本项目使用 JN_SSO 进行单点登录。启动前需要在 SSO 数据库中注册应用：

```sql
INSERT INTO jn_sso.sso_client_app (app_name, app_code, redirect_url, homepage_url, type, status)
VALUES ('JNClub', 'app-jnclub', 'http://localhost:19005/sso/login', 'http://localhost:5173', 'web', 1);
```

## 功能特性

- ✅ 收藏夹/便签/云盘/密码库（CRUD、目录、拖拽排序、标签、回收站、导入导出、全量备份）
- ✅ 待办 2.0（优先级/截止时间/子任务/重复规则/提醒/自然语言快速添加）
- ✅ 日历 2.0（月/周视图、拖拽改期、重复待办动态展开、待办提醒）
- ✅ RSS 订阅阅读器、稍后读（阅读进度）、网页快照、分享管理
- ✅ 便签增强（Markdown、图片、双链与反向链接、模板、字数统计、版本历史）
- ✅ 全局搜索 2.0（10+ 分组、`type:`/`date:`/`#标签` 语法、服务端历史、建议词、结果深链）
- ✅ Dashboard 2.0（今日必办、近期动态、趋势、自定义布局）
- ✅ 通知中心、用户偏好跨会话记忆、日/夜间模式（跟随系统）
- ✅ 音乐（JNMUSIC 融合：曲目/收藏/播放历史/搜索历史/播放队列/**歌单管理**/**猜你喜欢**/**跨设备播放进度同步**，蓝奏云+dufs 存储）
- ✅ WebDAV 站点管理（URL + 独立账号密码配置，站内简单文件管理：浏览/上传/下载/新建/删除/重命名，密码 AES 加密存储）
- ✅ 浏览器收藏助手（Chrome 扩展：一键/批量收藏、右键菜单、网页转便签、**稍后读**、**网页快照**、**保存去重提示**）
- ✅ SSO 单点登录

## 配置说明

后端配置文件：`jnclub-gateway/src/main/resources/application.yml`（本地）
线上参考：`application.example.yml`

```yaml
server:
  port: 19005

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jnclub?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: jiangnan123
  # 分片上传：max-file-size 需大于单个分片（jnclub.disk.chunk-size-mb），预留余量
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB

sa-token:
  sso:
    mode: client
    server-url: http://localhost:8080/sso   # 本地联调；生产为 SSO 服务地址
    client-url: http://localhost:19005
    client-id: app-jnclub
    secret-key: jn-sso-secret-key-2026
  token-name: jn-token

jnclub:
  dufs:
    base-url: http://localhost:8000          # 本地 dufs 内网地址；生产为服务器内网/域名
    public-url: /api/files/jnclub/images/
    upload-path: /jnclub/images/             # 便签图片存储前缀
    disk-path: /jnclub/disk/                 # 云盘文件存储前缀
    username:                                # dufs Basic Auth（本地可空）
    password:
  upload:
    max-size-mb: 10                          # 便签图片大小上限
  disk:
    max-size-mb: 500                         # 云盘单文件大小上限
    chunk-size-mb: 2                         # 云盘分片大小
    # temp-dir: /path/to/jnclub-upload       # 分片暂存目录，默认 <tmp>/jnclub-upload
  # WebDAV 站点管理：站点密码 AES 加密密钥（生产用环境变量 JNCLUB_WEBDAV_CRYPTO_KEY 覆盖）
  webdav:
    crypto-key: ${JNCLUB_WEBDAV_CRYPTO_KEY:jnclub-webdav-dev-key-2026}
```

**要点**：
- `dufs.base-url` 本地/线上差异通过本文件（本地）与 `application.example.yml`（线上）区分；`username/password` 线上必填。
- 前端与浏览器**不直接接触 dufs 内网地址**：写入由后端 PUT、读取走 `/api/files/**` 反向代理，避免内网地址泄露。
- `spring.servlet.multipart.max-file-size` 必须大于 `jnclub.disk.chunk-size-mb`，否则分片上传会被拒绝。

## API 文档

### 认证
- `GET /sso/login` - SSO 回调端点
- `GET /api/auth/userinfo` - 获取当前用户信息
- `POST /api/auth/logout` - 登出

### 目录
- `GET /api/directories?type=` - 获取目录树（type=1 收藏 / 2 便签 / 3 云盘）
- `POST /api/directories` - 创建目录
- `PUT /api/directories/{id}` - 重命名
- `GET /api/directories/{id}/content-count` - 删除前计数（含 fileCount）
- `DELETE /api/directories/{id}` - 删除（级联，有内容时禁止）
- `PUT /api/directories/sort` - 批量排序

### 收藏
- `GET /api/bookmarks?directoryId=` - 获取列表
- `GET /api/bookmarks/preview?url=` - URL 预览
- `POST /api/bookmarks` - 添加（自动提取 icon）
- `PUT /api/bookmarks/{id}` - 编辑
- `DELETE /api/bookmarks/{id}` - 删除
- `PUT /api/bookmarks/sort` - 批量排序

### 便签
- `GET /api/notes?directoryId=` - 获取列表
- `GET /api/notes/{id}` - 获取详情
- `POST /api/notes` - 新建
- `PUT /api/notes/{id}` - 编辑
- `DELETE /api/notes/{id}` - 删除
- `PUT /api/notes/sort` - 批量排序

### 图片上传（便签）
- `POST /api/upload/image` - 上传图片到 dufs（仅图片类型）

### 云盘（分片上传 + 断点续传）
- `POST /api/clouddisk/upload/init` - 初始化上传，返回 uploadId/分片配置
- `POST /api/clouddisk/upload/chunk` - 上传单个分片（multipart）
- `GET /api/clouddisk/upload/status?uploadId=` - 查询已传分片（断点续传）
- `POST /api/clouddisk/upload/complete` - 合并分片并入库
- `GET /api/clouddisk/files?directoryId=` - 文件列表
- `GET /api/clouddisk/files/{id}/download` - 下载（还原原始文件名）
- `DELETE /api/clouddisk/files/{id}` - 删除（dufs 对象 + 记录）
- `DELETE /api/clouddisk/temp-clean?days=` - 手动清理孤儿临时分片

### 标签
- `GET /api/tags` - 标签列表
- `POST /api/tags` - 新建标签
- `PUT /api/tags/{id}` - 重命名标签
- `DELETE /api/tags/{id}` - 删除标签（解绑关联）
- `GET /api/tags/relations` - 查询标签关联
- `PUT /api/tags/relations` - 绑定/更新收藏、便签的标签关联

### 密码库
- `GET /api/vault?directoryId=` - 条目列表（AES 密文，需已解锁）
- `GET /api/vault/{id}` - 条目详情
- `POST /api/vault` - 新增条目
- `PUT /api/vault/{id}` - 编辑条目
- `DELETE /api/vault/{id}` - 删除（软删除至回收站）
- `PUT /api/vault/sort` - 批量排序
- `POST /api/vault/master-key` - 设置主密钥（PBKDF2 派生）
- `POST /api/vault/unlock` / `POST /api/vault/lock` - 解锁/锁定
- `POST /api/vault/reset` - 重置主密钥
- `GET /api/vault/master-key/status` - 主密钥状态
- `GET /api/vault/check-health` - 密码库健康检查（重复密码/弱密码）

### 回收站
- `GET /api/recycle?type=` - 软删除列表（type: bookmark/note/file/vault）
- `POST /api/recycle/restore` - 恢复
- `DELETE /api/recycle/{type}/{id}` - 彻底删除
- `DELETE /api/recycle/clear?type=` - 清空

### 搜索
- `GET /api/search?q=` - 全局搜索（收藏/便签）

### 用户偏好
- `GET /api/user-preferences` - 获取偏好
- `PUT /api/user-preferences` - 保存偏好（模块/视图/导航顺序等）

### 音乐（JNMUSIC，对外前缀 `/music/api/v1/...`，匿名按 `X-Device-Id` 隔离）
- `GET /api/v1/tracks` - 曲目列表
- `GET /api/v1/tracks/search?q=` - 曲目搜索
- `GET /api/v1/tracks/batch` - 批量曲目
- `GET /api/v1/tracks/{trackId}` - 曲目详情
- `GET /api/v1/tracks/{trackId}/media-url` - 音频直链
- `GET /api/v1/tracks/{trackId}/lyrics` - 歌词
- `GET /api/v1/favorites` - 收藏列表
- `POST /api/v1/favorites` - 添加收藏
- `DELETE /api/v1/favorites/{trackId}` - 取消收藏
- `GET /api/v1/favorites/{trackId}/exists` - 是否已收藏
- `GET /api/v1/history` / `POST /api/v1/history` / `DELETE /api/v1/history` - 播放历史（POST 可携带 `progress` 秒数，用于跨设备「继续播放」）
- `GET /api/v1/history/latest` - 最近一次播放（含进度，供「继续播放」）
- `GET /api/v1/search-history` / `POST /api/v1/search-history` / `DELETE /api/v1/search-history` - 搜索历史
- `GET /api/v1/queue` / `PUT /api/v1/queue` - 播放队列（读取/保存）
- `POST /api/v1/queue/items` / `DELETE /api/v1/queue/items/{trackId}` - 队列增删
- `GET /api/v1/recommend?limit=` - 猜你喜欢（收藏/历史优先 + 随机补足）
- `GET /api/v1/playlists` / `POST /api/v1/playlists` / `PUT /api/v1/playlists/{id}` / `DELETE /api/v1/playlists/{id}` - 歌单 CRUD
- `GET /api/v1/playlists/{id}/tracks` / `POST /api/v1/playlists/{id}/tracks` / `DELETE /api/v1/playlists/{id}/tracks/{trackId}` - 歌单曲目管理
- `GET /api/v1/admin/lanzou/status` - 蓝奏云状态（管理端）
- `POST /api/v1/admin/lanzou/login` / `POST /api/v1/admin/lanzou/cookie` / `POST /api/v1/admin/lanzou/refresh-cache` - 蓝奏云登录/缓存管理（管理端）

### WebDAV 站点管理（需登录）
- `GET /api/webdav/servers` - 我的站点列表（密码不回传）
- `POST /api/webdav/servers` - 新增站点（密码 AES 加密存储）
- `PUT /api/webdav/servers/{id}` - 更新站点（password 传空 = 不变）
- `DELETE /api/webdav/servers/{id}` - 删除站点配置（不影响服务器文件）
- `POST /api/webdav/servers/{id}/test` - 测试连接（PROPFIND 根目录）
- `GET /api/webdav/servers/{id}/list?path=` - 列目录（PROPFIND，"" 为根目录）
- `POST /api/webdav/servers/{id}/mkdir` - 新建文件夹（MKCOL）
- `POST /api/webdav/servers/{id}/upload?path=` - 上传文件（PUT，multipart）
- `GET /api/webdav/servers/{id}/download?path=` - 下载文件（GET）
- `DELETE /api/webdav/servers/{id}/delete?path=&isDir=` - 删除文件/文件夹（DELETE，目录递归）
- `PUT /api/webdav/servers/{id}/rename` - 重命名（MOVE）

### 文件读取代理
- `GET /api/files/**` - 反向代理 dufs 文件（无需登录，只读）

## 数据库

`docs/init.sql` 包含核心 DDL：`t_directory`、`t_bookmark`、`t_note`、`t_note_asset`、`t_file`、`t_user_preference`、`sa_token_data`；标签/密码库/回收站为增量迁移（`t_tag`、`t_tag_relation`、`t_vault`、`t_vault_meta` 及软删除 `deleted` 列），见 init.sql 底部「迁移脚本」注释。音乐模块表见 `jnclub-module-music/src/main/resources/schema.sql`（`music_track` 等 6 张表）。WebDAV 站点表 `t_webdav_server` 由 `WebDavTableInit` 启动幂等自建（无需手工迁移）。

## 许可证

MIT
