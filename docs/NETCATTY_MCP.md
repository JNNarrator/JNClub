# Netcatty MCP 运维说明

> 从 2026-08-31 起，gm 服务器运维统一通过 **Netcatty External MCP** 连接，不再使用 Xterminal。

## 1. 为什么用 Netcatty MCP

- Netcatty 已保存 gm 服务器 SSH 会话，MCP 可以直接在现有终端会话里执行命令、查看输出、管理文件。
- 比 Xterminal 更适合 AI 自动化：有 `terminal_execute`、`terminal_start/poll/stop`、`sftp_*`、`vault_hosts_*` 等结构化工具。
- 以后所有 gm 服务器操作优先走 Netcatty MCP。

## 2. MCP Server 配置

在支持 MCP 的客户端（Claude/Codex 等）里注册：

```json
{
  "mcpServers": {
    "netcatty-external": {
      "command": "/Applications/Netcatty.app/Contents/Resources/app.asar.unpacked/electron/cli/netcatty-external-mcp",
      "args": [],
      "env": {
        "NETCATTY_EXTERNAL_MCP_DISCOVERY_FILE": "/Users/jiangnan/Library/Application Support/netcatty/external-mcp/discovery.json"
      }
    }
  }
}
```

> `discovery.json` 由 Netcatty 自动生成，包含本地 TCP 端口和 token，**不要提交到仓库**。
> 使用前提：Netcatty 应用保持运行，并已开启 Settings → AI → External MCP。

## 3. 常用 MCP 工具

| 用途 | 工具 |
|---|---|
| 查看当前可用的终端会话 | `get_environment` |
| 查看已保存主机 | `vault_hosts_list` |
| 打开已保存主机 | `host_open` |
| 执行短命令（快速检查） | `terminal_execute` |
| 启动长任务（构建/部署/日志） | `terminal_start` |
| 轮询长任务输出 | `terminal_poll` |
| 停止长任务 | `terminal_stop` |
| 远程文件读写 | `sftp_read_file` / `sftp_write_file` |
| 远程文件上传下载 | `sftp_upload` / `sftp_download` |
| 主机/保险箱管理 | `vault_hosts_*`、`vault_notes_*` |

## 4. gm 服务器

- 标签：`gm`
- 主机：`gm_ssh.88933.vip:21236`
- 用户：`root`
- 在 Netcatty 中通常已有连接中的会话；先 `get_environment` 拿到 `sessionId`，再对 `gm` 会话执行命令。
- 仓库部署路径：
  - 源码：`/home/jiangnan/JNClub-src`
  - 运行产物：`/home/jiangnan/JNClub/`
  - 音乐前端：`/home/jiangnan/music-frontend/`
  - 外置配置：`/home/jiangnan/JNClub/jnclub-gateway/application.yml`

## 5. 部署流程

### 5.1 常规自动部署

JNClub 已配置 git push 自动部署：

```bash
cd /Users/jiangnan/Documents/workspace/JNClub
git add -A
git commit -m "..."
git push origin master
```

push 后 gm 服务器最长 5 分钟内自动构建部署。

### 5.2 用 Netcatty MCP 验证

1. 调用 `get_environment` 找到 `gm` 的 `sessionId`。
2. 调用 `terminal_execute` 执行：
   - 查看自动部署日志/最近提交：
     ```bash
     cd /home/jiangnan/JNClub-src && git log --oneline -3
     ```
   - 检查音乐后端/前端进程或端口：
     ```bash
     ss -ltnp | grep 19005
     ls -lt /home/jiangnan/music-frontend/dist/assets | head
     ```
3. 若自动部署未触发或需要手动部署，再通过 `terminal_start` 在 gm 上执行构建脚本。

## 6. 注意

- 不要把 `discovery.json`、Netcatty token、服务器密码/私钥写进仓库。
- 不要再用 Xterminal 作为 AI 运维通道；已有 Xterminal 相关文档/习惯逐步迁移到 Netcatty MCP。
