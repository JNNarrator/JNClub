# JNMusic 构建部署指南

> ⚠️ **已接入服务器自动部署**：`git push` 到 master 后，gm 服务器最长 5 分钟内自动构建部署（服务器自拉取自构建自重启）。**日常发布流程 = `git push`**；立即部署可执行 `/opt/auto-deploy/deploy.sh music`；框架说明见 `服务器服务操作/AUTO-DEPLOY.md`。本指南保留的 `deploy.sh` 流程为手动备选/回退。

## 前置条件

- Node.js 环境（已安装 npm）
- Java 21+ / Maven 环境（仓库内置 `./mvnw`）
- SSH 访问 `gm` 服务器（`~/.ssh/config` 已配置，或 `DEPLOY_HOST=gm`）
- gm 服务器上已有后端启动脚本：`/home/jiangnan/music/deepseek_bash_20260708_19322e.sh`

## 快速部署（一条命令）

```bash
cd Workspace/JNMUSIC_SERVER
bash deploy.sh
```

`deploy.sh` 自动执行（前后端分离）：

1. **构建前端**：`cd admin && npm run build`，产物在 `admin/dist/`
2. **构建后端 JAR**：`./mvnw package -DskipTests`，产物 `target/music-0.0.1-SNAPSHOT.jar`（仅后端 API）
3. **上传前端**：打包 `dist/` → 解压到 gm 服务器 `/home/jiangnan/music-frontend/`（nginx 静态托管，base `/music/`）
4. **上传后端**：JAR 先 `rsync` 到 `/home/jiangnan/music/music-0.0.1-SNAPSHOT.jar.bak`（回滚备份），再上传到 `/home/jiangnan/music/`
5. **重启后端服务**：`ssh gm "cd /home/jiangnan/music && sh deepseek_bash_20260708_19322e.sh restart"`

## 回滚

```bash
ssh gm 'cp /home/jiangnan/music/music-0.0.1-SNAPSHOT.jar.bak \
  /home/jiangnan/music/music-0.0.1-SNAPSHOT.jar \
  && cd /home/jiangnan/music && sh deepseek_bash_20260708_19322e.sh start'
```

## 验证部署

```bash
# 前端（nginx 静态托管）
curl -I http://jiangnan.88933.vip/music/

# 后端 JAR 完整性
ssh gm "ls -lh /home/jiangnan/music/music-0.0.1-SNAPSHOT.jar && unzip -t /home/jiangnan/music/music-0.0.1-SNAPSHOT.jar 2>&1 | tail -2"

# 服务进程 / 日志
ssh gm "ps aux | grep music"
ssh gm "tail -30 /home/jiangnan/music/logs/music-app.log"
```

## 仅提交代码（不部署）

```bash
cd Workspace/JNMUSIC_SERVER
git add <files>
git commit -m "message"
git push
```

## 项目路径速查

| 项目 | 路径 |
|------|------|
| 前端源码 | `admin/src/` |
| 前端构建产物 | `admin/dist/` |
| 后端 JAR | `target/music-0.0.1-SNAPSHOT.jar` |
| gm 服务器前端部署目录 | `/home/jiangnan/music-frontend/` |
| gm 服务器后端部署目录 | `/home/jiangnan/music/` |
| gm 服务器启动脚本 | `/home/jiangnan/music/deepseek_bash_20260708_19322e.sh` |
| gm 服务器日志 | `/home/jiangnan/music/logs/music-app.log` |