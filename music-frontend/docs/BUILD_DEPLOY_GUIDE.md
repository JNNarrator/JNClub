# 音乐模块构建部署指南（并入 JNClub 后）

> **现状（2026-08-11 起）**：音乐服务（前身独立仓库 JNMUSIC_SERVER）已并入 JNClub 单体。
> 后端为 `jnclub-module-music`（随 `jnclub-gateway` 一起构建，端口 19005），对外 URL 保持 `/music/api/v1/...`（内部路径重写）；前端为本目录 `music-frontend/`。
> 本指南即音乐前端的构建部署说明，**已无独立后端 jar / 独立 `deploy.sh`**。

## 部署方式（git push 即上线）

音乐前端与后端均随 **JNClub 自动部署** 上线：

1. **常规发布**：本地 `git push` 到 JNClub master → gm 服务器最长 5 分钟内自动构建部署（含 `music-frontend: npm ci && npm run build`）
2. **立即部署**：`/opt/auto-deploy/deploy.sh jnclub`
3. **框架说明**：见 `服务器服务操作/AUTO-DEPLOY.md`

自动部署产物落位：

| 内容 | 位置 |
|---|---|
| 音乐前端构建产物 | gm `/home/jiangnan/music-frontend/`（nginx 静态托管 `/music/`） |
| 音乐后端 | 随 `jnclub-gateway` jar（端口 19005，对外 `/music/api/v1`） |
| 数据 | `jnclub` 库 `music_*` 表（`schema.sql` 见 `jnclub-module-music/src/main/resources/`） |

## 本地开发

```bash
cd Workspace/JNClub/music-frontend
npm install
npm run dev        # Vite :5173，base /music/，/music 代理到 http://127.0.0.1:19005
```

> 需本地 JNClub 后端（19005）已启动。配置见 `vite.config.ts` 的 `server.proxy`。

## 本地构建验证

```bash
cd Workspace/JNClub/music-frontend
npm run build      # 产物 dist/（base /music/）
```

## 验证线上部署

```bash
# 前端（nginx 静态托管）
curl -I http://jiangnan.88933.vip/music/

# 后端接口（随 jnclub 服务）
curl -s -o /dev/null -w '%{http_code}' http://localhost:19005/music/api/v1/tracks
```

## 相关配置

- 音乐后端配置（dufs 直链 / 蓝奏云 / 缓存）在 `jnclub-module-music/src/main/resources/application.properties`；生产如需覆盖，追加到 `jnclub-gateway` 外置 `application.yml`（服务器 `/home/jiangnan/JNClub/jnclub-gateway/application.yml`）。
- 音频/封面/歌词直链来自 dufs（`jn_file.88933.vip:27472`）。
