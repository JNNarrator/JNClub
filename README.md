# JNClub - 个人工作台服务

JNClub 是一个前后端分离的个人工作台 Web 服务，包含「收藏夹」「便签」「云盘」三大模块，并内嵌「音乐」模块（JNMUSIC 融合，对外路径 `/music/`）。

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
├── jnclub-module-bookmark/    # 业务模块（目录/收藏/便签/云盘/上传）
├── jnclub-module-music/       # 音乐模块（JNMUSIC 并入：/music/api 匿名 API，music_* 表）
├── jnclub-gateway/            # API 网关（启动入口，端口 19005）
├── jnclub-web/                # 主前端项目（Vue3，base /jnclub/，侧边栏含音乐入口）
├── music-frontend/            # 音乐播放器前端（Vue3，base /music/，nginx 静态托管）
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

- ✅ 目录管理（树形目录、CRUD、拖拽排序、级联删除、删除保护；type 区分模块：1 收藏夹 / 2 便签 / 3 云盘）
- ✅ 网页收藏（CRUD、Favicon 自动提取、URL 预览、拖拽排序）
- ✅ 便签（CRUD、Markdown 编辑、图片上传、拖拽排序）
- ✅ 云盘（单文件分片上传、断点续传、暂停/恢复、文件列表、下载还原原始文件名、删除）
- ✅ SSO 单点登录
- ✅ 日/夜间模式（跟随系统）
- ✅ 用户偏好记忆（模块/视图/目录跨会话记忆）

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

### 文件读取代理
- `GET /api/files/**` - 反向代理 dufs 文件（无需登录，只读）

## 数据库

`docs/init.sql` 包含全部 DDL：`t_directory`、`t_bookmark`、`t_note`、`t_note_asset`、`t_file`、`t_user_preference`。

## 许可证

MIT
