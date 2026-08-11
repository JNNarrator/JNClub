# JNClub 主界面顶栏与滚动布局重构设计

日期：2026-08-11
状态：已获用户确认（"可以开始"）

## 1. 背景与目标

修复三处问题并统一布局：
1. **Bug**：收藏夹顶栏误现"新建密码"按钮（根因：上轮"导出全部"用独立 `v-if` 插入，打断了新建按钮的 `v-if/v-else-if/v-else` 链，导致 `v-else`（新建密码）在非云盘模块错误命中）
2. **模块功能归属**：各模块特定操作（新建/导出/上传）移入模块内部工具栏；顶栏只留通用能力
3. **滚动策略**：内容多时在内部容器滚动，不用浏览器滚动条（规范化现有滚动链）

已确认决策：**暗色模式开关移顶栏**（与头像一起）；**移除右下角悬浮 FAB**。

## 2. 现状（已探索确认）

- **顶栏 `.home-header`**（Home.vue:511-588，60px 玻璃条）：面包屑 + 搜索(⌘K) + ViewSwitcher(书签/便签) + 新建按钮链（新建/新建便签/上传文件/新建密码，`v-if/v-else-if/v-else`）+ 导出全部（独立 `v-if`，打断链）+ 刷新
- **头像/名称/暗色开关**：位于 SideNav 底部 `.sider-footer`（user-row + theme toggle）
- **滚动现状**：`.collection-column` 已 `overflow-y: auto`、`FolderPanel` 已内部滚动 → 浏览器滚动条本就不出现；但 `.folder-column` 无高度约束，目录超高时可能撑破
- **模块内部工具栏**：仅 VaultView 有【新建条目】；DiskView 无（上传靠顶栏 trigger）；书签/便签无
- **FAB**：FloatingActions（右下角固定，含 add 按钮）

## 3. 设计

### 3.1 顶栏（只留通用能力 + 用户区）

左→右：
```
[目录抽屉·移动端] 面包屑(JNClub/模块/目录) │ 搜索(⌘K) 视图切换(书签/便签) 刷新 │ 暗色toggle 头像+名称(下拉:用户信息/退出登录)
```

- 移除：新建按钮链、导出全部
- 新增：暗色模式 toggle（Sun/Moon + pill，从 SideNav 移入）、头像+名称下拉（NAvatar + nickname + 角色，NDropdown：用户信息/退出登录，逻辑从 SideNav 迁移）

### 3.2 各模块内部工具栏（collection-column 顶部、chip-bar 上方）

| 模块 | 工具栏按钮 |
|---|---|
| 收藏夹 | 【新建收藏】`btn-new` pill → handleOpenCreate |
| 便签 | 【新建便签】【导出全部】→ handleCreateNote / handleExportAllNotes |
| 云盘 | 【上传文件】`btn-new` pill → diskUploadTriggered++（DiskView 已有 trigger 监听） |
| 密码库 | VaultView 已有【新建条目】✓ 不动 |

统一 `.module-toolbar` 样式：flex + gap + margin-bottom，按钮沿用 `btn-new`/`io-export-btn` 玻璃 pill。

### 3.3 滚动规范化

- `.folder-column`：补 `flex:1; min-height:0; overflow:hidden`（目录超高由 FolderPanel 内部滚动）
- `.collection-column`：保持 `overflow-y:auto`；`.module-toolbar` 在滚动区内顶部（随内容区，不随列表滚）
- `.home`/`.content-area` 已有 `flex + min-height:0` 保持 → 滚动只发生在两个玻璃面板内部
- 移动端 `@media` 保留，toolbar 适配

### 3.4 移除 FAB

`FloatingActions.vue` 删除；Home.vue 移除引用；清理 fabLabel/handleHelp 死代码。

### 3.5 SideNav 瘦身

移除底部 `.sider-footer`（theme toggle + user-row）→ 侧栏只剩 Logo + 导航 + 回收站；清理 `isDark/toggle-theme` props/emit 与 userStore 引用。MainLayout 相应不再传这些 prop。MobileTabBar 保留（移动端模块切换）。

## 4. 验证

1. `cd jnclub-web && npx vue-tsc --noEmit` 零错误
2. `npm run build` 成功
3. `git push` → JNClub auto-deploy → 部署成功
4. 线上浏览器实测：
   - 收藏夹顶栏无"新建密码"；顶栏=面包屑/搜索/视图/刷新/暗色/头像
   - 各模块内部工具栏（收藏夹新建、便签新建+导出、云盘上传、密码库新建条目）
   - 头像/名称/暗色在顶栏，下拉可用（用户信息/退出登录）
   - 四模块内容多时：滚动在 collection-column 内部，无浏览器滚动条；目录超高 FolderPanel 内部滚动
   - FAB 消失；移动端适配正常

## 5. 不做（YAGNI）

- 不改 DiskView/VaultView 组件逻辑（trigger 机制保留）
- 不动 MobileTabBar
- 不做顶栏布局大改（保持 60px 玻璃条）
