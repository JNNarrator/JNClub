# JNClub 部署文档

本文档介绍 JNClub 的本地环境部署与线上（服务器）部署方式。

## 组件依赖

| 组件 | 作用 | 本地 | 线上 |
|---|---|---|---|
| MySQL 8.x | 主数据库 `jnclub` | localhost:3306 | 服务器内网 |
| dufs | 文件存储（图片/云盘） | localhost:8000 | 服务器内网/域名单机 |
| JN_SSO | 单点登录服务端 | localhost:8080 | SSO 服务地址 |
| JNClub 后端 | 业务 API | 19005 | 19005 |
| JNClub 前端 | Vue3 SPA（Vite/构建产物） | 5173 | 静态托管/nginx |

---

## 一、本地开发部署

### 1. 前置依赖

- JDK 21
- Maven 3.8+
- Node.js（Vite 6 建议 18+）
- MySQL 8.x（本机）
- dufs（`brew install dufs`，本机）

### 2. 数据库

```bash
mysql -uroot -p < docs/init.sql          # 全新库
mysql -uroot -p jnclub < docs/init.sql   # 已有库：仅执行底部「迁移脚本」部分
```

### 3. 启动文件服务 dufs

dufs 通过 launchctl 托管，脚本在 `本地服务操作/` 目录：

```bash
./本地服务操作/start_dufs.sh   # 启动（端口 8000）
curl http://localhost:8000/    # 验证
./本地服务操作/stop_dufs.sh    # 停止
```

dufs 工作目录为 `本地服务操作/`，实际存储路径：
- 便签图片：`本地服务操作/jnclub/images/...`
- 云盘文件：`本地服务操作/jnclub/disk/...`

### 4. 启动 SSO

```bash
cd /path/to/JN_SSO && mvn package -DskipTests && java -jar sso-server/target/sso-server-1.0.0.jar
```

> 需在 SSO 库 `jn_sso.sso_client_app` 中登记 JNClub 应用（见 README「SSO 配置」）。

### 5. 启动备份后端

```bash
cd JNClub
mvn package -DskipTests
java -jar jnclub-gateway/target/jnclub-gateway-1.0.0-SNAPSHOT.jar
```

### 6. 启动前端

```bash
cd jnclub-web
npm install
npm run dev        # http://localhost:5173/jnclub/
```

### 一键脚本

`JNClub/.scripts/start.sh` 会依次构建并分别在三个 Terminal 窗口启动 SSO / 后端 / 前端并验证：

```bash
./.scripts/start.sh
./.scripts/stop.sh   # 按端口停止 5173 / 19005 / 8080
```

> 端口约定：SSO 8080、后端 19005、前端 5173、dufs 8000、MySQL 3306。

---

## 二、线上（服务器）部署

> ⚠️ **JNClub 已接入服务器自动部署**：本地 `git push` 到 master 后，gm 服务器最长 5 分钟内自动构建部署（源码 `/home/jiangnan/JNClub-src` → 产物回填 `/home/jiangnan/JNClub/`，外置 `/home/jiangnan/JNClub/jnclub-gateway/application.yml` 不受影响）。框架说明见 `服务器服务操作/AUTO-DEPLOY.md`；以下为部署环境要素说明。

线上与本地差异集中在 `dufs.base-url` 与 SSO 地址（走 `application.example.yml` 作为模板）。核心原则：**前端和浏览器不接触 dufs 内网地址**，读写都经后端中转。

### 1. 数据库

在服务器 MySQL 执行 `docs/init.sql`（新建库）或仅执行迁移脚本（升级）。云盘需 `t_file` 表。

### 2. 文件服务 dufs

在服务器以 systemd / 容器方式运行 dufs，监听本地或内网端口：

```bash
# 示例：前台运行（生产建议 systemd）
dufs -A -p 8000 /data/jnclub-files
```

- 存储根：`/data/jnclub-files`（对应 `jnclub/images`、`jnclub/disk`）
- 若对外暴露，建议置于 nginx/反代之后并加强认证

### 3. 后端配置

以 `application.example.yml` 为准，重点项：

```yaml
sa-token:
  sso:
    server-url: https://<sso>/sso      # SSO 生产地址
    client-url: https://<domain>:19005 # 或经网关后的地址
    client-id: app-jnclub

jnclub:
  dufs:
    base-url: http://127.0.0.1:8000    # 服务器本机 dufs（内网/回环地址）
    public-url: /api/files/jnclub/images/
    upload-path: /jnclub/images/
    disk-path: /jnclub/disk/
    username: your_dufs_user          # 线上必须设置
    password: your_dufs_password
  disk:
    max-size-mb: 500
    chunk-size-mb: 2
    temp-dir: /var/data/jnclub-upload  # 建议改为有足够磁盘的路径
```

覆盖项通过启动参数或环境变量：
```bash
java -jar jnclub-gateway-1.0.0-SNAPSHOT.jar \
  --sa-token.sso.server-url=https://<sso>/sso \
  --jnclub.dufs.base-url=http://127.0.0.1:8000
```

### 4. 前端构建与托管

```bash
cd jnclub-web
npm install
npm run build          # 产物在 dist/
```

将 `dist/` 部署到 nginx 或静态托管。SPA 需将 `/jnclub/` 下请求回退到 `index.html`（处理 history 路由），并把 `/api`、`/sso` 反向代理到后端 19005。

**PWA（Service Worker）nginx 要点**：
- Service Worker 注册要求 **HTTPS**（localhost 除外），线上域名必须走 https。
- `sw.js`、`manifest.webmanifest` 与构建产物**不能长缓存**（SW 更新需要拿到新 sw.js；产物文件名带 hash 本身安全，但 index.html 也不应长缓存，否则拿不到新引用）。
- `.webmanifest` 需正确 MIME。参考配置：

```nginx
location /jnclub/ {
    alias /path/to/jnclub-web/dist/;
    # SW / manifest / index 不缓存，其余带 hash 的静态资源可强缓存
    location ~* \.(?:js|css|png|svg|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    location ~* (?:sw\.js|manifest\.webmanifest|index\.html)$ {
        add_header Cache-Control "no-cache";
        add_header Service-Worker-Allowed "/jnclub/";
    }
    try_files $uri $uri/ /jnclub/index.html;
    # manifest MIME
    location = /jnclub/manifest.webmanifest {
        default_type application/manifest+json;
    }
}
```

### 5. SSO 生产应用登记

在 `jn_sso.sso_client_app` 登记/确认线上应用（如已有 `app-jnclub-prod`）：

```sql
INSERT INTO jn_sso.sso_client_app (app_name, app_code, redirect_url, homepage_url, type, status)
VALUES ('JNClub-生产', 'app-jnclub-prod', 'https://<domain>/jnclub/sso/login', 'https://<domain>/jnclub', 'web', 1);
```

---

## 三、云盘部署注意点（带宽小/不稳定场景）

1. **分片大小**：默认 `chunk-size-mb: 2`，弱网下单片成功率更高、重传成本低；可按需调小（如 1MB）。
2. **multipart 上限**：`spring.servlet.multipart.max-file-size` 必须 ≥ `chunk-size-mb`（默认 10MB），否则分片上传 500。
3. **临时目录磁盘**：`disk.temp-dir` 需有足够可用空间（最大文件 = 单文件上限 500MB × 并发用户数），建议放数据盘。断点续传期间分片暂存于此。
4. **孤儿清理**：后端每日凌晨自动清理超过 1 天的孤儿临时分片；也可手动 `DELETE /api/clouddisk/temp-clean?days=1`。
5. **dufs 稳定性**：dufs 作为单点存储，建议配 systemd 自愈；`base-url` 用本机回环地址 `127.0.0.1:8000` 减少网络层抖动。

---

## 四、验证清单

| 检查项 | 命令/操作 | 预期 |
|---|---|---|
| 后端编译 | `mvn -q -pl jnclub-gateway -am package -DskipTests` | 无错误 |
| 前端构建 | `cd jnclub-web && npm run build` | 成功 |
| dufs 存活 | `curl localhost:8000/` | 200 |
| 后端存活 | `curl localhost:19005/` | 401（需登录为正常） |
| 前端 | 浏览器 `http://localhost:5173/jnclub/` | 显示登录/工作台 |
| 云盘上传 | 云盘模块新建目录 → 上传文件 → 列表出现 | 进度到 100%，文件可下载 |
| 断点续传 | 上传中断后重新选择同名文件 | 从已传分片续传，不整文件重传 |
| 云盘删除目录保护 | 目录含文件时删除 | 提示"目录下存在条目"不被删除 |
