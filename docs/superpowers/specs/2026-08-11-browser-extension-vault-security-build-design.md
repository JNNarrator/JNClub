# JNClub 三方向增强规格：浏览器插件 · 密码库主密钥 · 构建优化

> 日期：2026-08-11
> 范围：① Chrome 浏览器插件「JNClub 收藏助手」（独立子项目）② 密码库主密钥 + 密码健康检查 ③ 前端构建性能优化
> 前置：PWA + 移动端适配已完成（2026-08-11），桌面端与移动端双端可用

## 背景与目标

JNClub 已完成 12+ 功能模块（SSO/收藏/便签/云盘/密码库/回收站/标签/搜索/偏好记忆）+ PWA + 移动端。
本规格为三个"添砖加瓦"方向：

1. **浏览器插件**：把"收藏网页"从"打开 JNClub → 新建 → 填 URL"变成"点一下图标/右键"。最高频的个人工作台刚需。
2. **密码库主密钥 + 健康检查**：当前 AES 密钥是配置硬编码兜底（`jnclub-vault-2026`，代码注释明确要求生产替换），是现存最明确的安全债。
3. **构建性能**：NoteEditorPage chunk 905KB（构建警告），首屏/编辑页加载可优化。

## 方向 ① 浏览器插件「JNClub 收藏助手」（Chrome MV3）

### 架构

```
browser-extension/
├── manifest.json              # MV3: permissions[tabs,storage,contextMenus,notifications]
│                              #      host_permissions [<all_urls>（可配置服务器，上架收窄）]
├── popup/popup.html|js|css    # 点图标弹窗：当前页一键收藏 + 登录态 + 设置
├── batch/batch.html|js|css    # 标签页批量收藏（chrome.tabs.query 列全部标签页）
├── background/service-worker.js  # 右键菜单注册、token 管理、API 调用
├── content/inject.js          # 注入 JNClub 网页，监听 localStorage 'jn-token' 同步到插件
├── lib/api.js                 # JNClub API 客户端（fetch + jn-token 头 + 401 处理）
├── assets/icon16/48/128.png   # Apple Pink 渐变 heart（与 Web 端 logo 一致）
├── settings.html|js|css       # 服务器地址配置（默认 http://localhost:19005）
└── README.md                  # 安装指引（开发者模式加载）
```

### 认证：内嵌 SSO 登录（无新后端接口）

- 插件 popup 显示「未登录」→ 点登录 → `chrome.tabs.create({ url: '{server}/jnclub/sso/login' })`
- 复用既有 SSO 流程：登录成功后 JNClub 前端 `user.ts:25` 把 token 写入 `localStorage['jn-token']`
- content script（匹配 `{server}/jnclub/**`）监听该 key → `chrome.runtime.sendMessage` → background 存 `chrome.storage.local`
- token 有效期 7 天；401 时 popup 提示重登
- **CORS 无需改造**：MV3 扩展对 `host_permissions` 声明的 origin 请求绕过网页 CORS；且现有 `CorsConfig` 已是 `allowedOriginPattern("*")`

### 功能

| 功能 | 实现 |
|---|---|
| 当前页一键收藏 | popup 取 `chrome.tabs.query({active,currentWindow})` 的 title/url → 目录下拉（`GET /api/directories?type=1`，记忆上次选择）→ `POST /api/bookmarks` |
| 标签页批量收藏 | batch 页 `chrome.tabs.query({})` 列全部标签页（排除 `chrome://`/`edge://` 等受保护协议）→ 勾选 → 选目录 → 循环 `POST /api/bookmarks`，失败单项跳过统计 |
| 右键菜单收藏 | background `chrome.contextMenus.create`「收藏到 JNClub」→ 取 `info.pageUrl`/`tab.title` → 收藏到默认目录 → `chrome.notifications.create` 结果通知 |

### 样式：JNClub 风格（用户重点要求）

- popup 宽 340px；玻璃拟态：`backdrop-filter: blur(20px)`、`rgba(255,255,255,0.72)` 底（暗色 `rgba(28,28,30,0.72)`）
- 品牌色 `--brand: #EC5B8E`、渐变按钮 `linear-gradient(135deg,#EC5B8E,#FF8FAB)`、圆角 10/16px、DM Sans
- 亮/暗跟随 `prefers-color-scheme`（与 Web 端 token 一致）

### 分发：本地加载 + JNClub 下载页

- `scripts/build-extension.sh`：打包 zip（排除源码调试文件）→ `jnclub-web/public/extension/jnclub-extension-<version>.zip`
- 前端新增路由 `/extension`（模块外独立页）：下载按钮 + 图文安装步骤（下载 → 解压 → chrome://extensions → 开发者模式 → 加载已解压的扩展程序）
- 上架 Chrome Web Store 代码已兼容（manifest 描述/图标/权限齐全），需要时改 host_permissions 收窄后提交

## 方向 ② 密码库主密钥 + 健康检查

### 现状问题
- `VaultService` 用配置 `jnclub.vault.crypto-key`（默认 `jnclub-vault-2026`）经 MD5 派生 AES 密钥
- 密钥硬编码于配置，任何拿到配置的人可解密全部密码；无主密钥概念
- `t_vault` 表已有 `user_id VARCHAR(64)` 用户维度 ✓

### 设计

**数据模型**（`docs/init.sql` 追加）：
```sql
CREATE TABLE IF NOT EXISTS t_vault_meta (
  user_id VARCHAR(64) PRIMARY KEY COMMENT 'SSO用户标识',
  salt VARCHAR(64) NOT NULL COMMENT 'PBKDF2 盐（hex）',
  iterations INT NOT NULL DEFAULT 100000 COMMENT 'PBKDF2 迭代次数',
  key_version INT NOT NULL DEFAULT 1 COMMENT '密钥版本',
  kdf VARCHAR(20) NOT NULL DEFAULT 'PBKDF2-SHA256' COMMENT 'KDF 算法',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='密码库主密钥元数据（不含密钥本身）';
ALTER TABLE t_vault ADD COLUMN password_fingerprint VARCHAR(64) DEFAULT NULL COMMENT '密码SHA-256指纹（健康检查用，不解密可比）' AFTER password;
```

**密钥派生与生命周期**：
- 用户首次设置主密钥：服务端生成随机 16B salt → `PBKDF2WithHmacSHA256(masterKey, salt, 100000, 256bit)` → AES-256-GCM
- 密钥**只存内存**（`ConcurrentHashMap<userId, byte[]>` + 30 分钟空闲过期），**永不入库**
- 设置/修改主密钥时：读旧配置密钥解密全部条目 → 新密钥重加密 → 更新 fingerprint
- 修改需先验证旧密钥；重置（遗忘场景）双重确认后清空 `t_vault` 全部条目
- 兼容迁移：未设置主密钥时（meta 不存在）沿用现有配置密钥路径，**存量数据无感**

**API**（`VaultController` 新增）：
| 接口 | 说明 |
|---|---|
| `GET /api/vault/master-key/status` | 是否已设置、是否已解锁 |
| `POST /api/vault/master-key` | 设置/修改（body: oldMasterKey?, newMasterKey）→ 迁移重加密 |
| `POST /api/vault/unlock` | body: masterKey → 校验 PBKDF2 派生结果匹配（meta 存 salt+hash 校验）→ 缓存 30min |
| `POST /api/vault/lock` | 清内存密钥 |
| `POST /api/vault/reset` | 遗忘重置：双重确认（body: confirm="RESET", resetCode）清空条目 + meta |
| `GET /api/vault/check-health` | 解锁后返回弱/重复密码列表 |

**主密钥校验**：meta 需存一个 `keyCheck` 派生（如对固定字符串 `jnclub-master-check` 加密）用于解锁时校验输入正确性，不存密钥本体。

**健康检查**（只提示不拦截，用户明确要求）：
- 保存密码时（创建/更新），若为明文，服务端算 SHA-256 指纹存 `password_fingerprint`；若留空（编辑不改密码），重新读明文计算
- 强度评分：长度（≥8/12/16）+ 字符类别（大小写/数字/符号）+ 常见弱密码列表（123456/password 等）→ 0-100 分
- 重复检测：同用户下 SHA-256 指纹重复的条目
- 前端列表页角标：弱密码橙色「弱」、重复密码橙色「重复」；弹窗保存时顶部提示但不阻止

### 前端
- `VaultView` 未设置主密钥 → 引导设置向导（两遍输入 + 强度提示 + 风险告知"遗忘不可恢复"）
- 已设置未解锁 → 锁定面板（输入主密钥解锁 / 遗忘重置入口）
- 已解锁 → 正常列表 + 弱/重复角标
- 刷新页面需重新解锁（后端内存态），前端存会话级 `sessionStorage` 标记避免重复输入

## 方向 ③ 构建性能优化

- `vite.config.ts` `build.rollupOptions.output.manualChunks`：
  - `naive-ui`：naive-ui + 依赖
  - `md-editor`：md-editor-v3 + codemirror 相关
  - `lucide`：lucide-vue-next
  - `vendor`：vue/vue-router/pinia/axios 等
- 目标：无单 chunk > 600KB（消除 NoteEditorPage 905KB 警告）

## 实施顺序与依赖

| 阶段 | 内容 | 依赖 |
|---|---|---|
| 0 | 规格落盘 + git 提交 | — |
| A | 浏览器插件（A1-A5） | 无（独立目录） |
| B | 密码库主密钥 + 健康检查 | t_vault 已含 user_id；需补 spring-boot-starter-test |
| C | 构建优化 | 与 A/B 并行 |

## 验证清单

| 检查项 | 方法 | 预期 |
|---|---|---|
| 前端构建 | `cd jnclub-web && npm run build` | vue-tsc 零错误；无 >600KB chunk 警告 |
| 后端构建 | `mvn package -DskipTests` | 通过 |
| 后端单测 | VaultService JUnit（补 starter-test） | 派生/迁移/指纹逻辑通过 |
| 插件三功能 | 本地加载实测 | 登录→当前页收藏→批量→右键收藏 全通 |
| 插件视觉 | 与 Web 端截图对比 | Apple Pink 玻璃拟态一致 |
| 主密钥流程 | 设置→迁移→锁定→解锁→健康检查→重置 | 全通 |
| 回归 | 收藏/便签/云盘 | 不受影响 |

## 风险与注意

- 插件 `host_permissions <all_urls>` 仅限本地加载场景（上架前收窄到 JNClub 域名）
- 主密钥不可恢复是设计取舍（业界标准）；重置入口务必双重确认
- fingerprint 用 SHA-256 可逆性低（短弱密码可彩虹表），仅用于"相同密码"比较，不用于还原密码
- 迁移期间保留旧配置密钥兼容路径直至全部重加密完成
