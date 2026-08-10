# JNClub 回收站玻璃化改造设计

日期：2026-08-11
状态：已获用户确认（"实现并部署"）

## 1. 背景与目标

JNClub 已由提交 `021d2ac` 完成全项目玻璃拟态重构（Apple Pink 品牌、亮/暗双模式 `--glass-*` token 体系、main.css 的 `.glass-*` 工具类）。回收站页（RecycleView.vue）是漏网之鱼：header 已用 `.glass-header`、条目卡片已玻璃化，但内容区无玻璃面板、Tabs/Empty/操作按钮未适配。目标：回收站对齐既有玻璃体系。

## 2. 范围

- 仅改 `jnclub-web/src/modules/bookmark/views/RecycleView.vue` 一个文件（模板局部 + scoped 样式）
- 零新增 token/工具类——全部复用 main.css 既有 `--glass-*` 变量与 `.glass-*` 类
- 不改任何 JS 逻辑（API/交互不变）
- 后端零改动

## 3. 设计

参照 Home.vue `collection-column` 与 DiskView 面板样式，逐项对齐：

| 元素 | 现状 | 改造 |
|---|---|---|
| 内容区 `.recycle-body` | 无背景（透明浮在 app-content 玻璃底上） | 包成玻璃面板：`radial-gradient(glow-top/bottom) + glass-bg-trans + blur + 1px glass-border + radius-md + glass-shadow` |
| Tabs `NTabs` | Naive 默认 | 选中下划线/文字改 `--brand`、未选中 `--glass-text-secondary`，与主页 Tabs 观感一致 |
| Empty `NEmpty` | 默认 | 文字转 `--glass-text-secondary`（scoped `:deep` 覆盖） |
| 恢复按钮 | `type="primary" quaternary` tiny | 改 `glass-primary-btn`（粉色渐变，对齐 VaultView/DiskView） |
| 永久删除按钮 | `type="error" quaternary` tiny | 改红色玻璃按钮（`glass-bg-trans` 底 + 红字红边，hover 加强） |
| 清空当前类型按钮 | `type="error" quaternary` | 玻璃按钮化（红色系玻璃，disabled 半透明） |
| 返回按钮/标题 | 已 OK | 不动 |
| 移动端 `@media` | 已有 | 保留不破坏 |

## 4. 验证

1. `cd jnclub-web && npx vue-tsc --noEmit` 零错误
2. `npm run build` 成功
3. `git push` → auto-deploy（JNClub-src）→ 部署成功 + 前端 HTTP 200
4. 浏览器线上复核：亮/暗两模式回收站——内容玻璃面板、Tabs 粉色下划线、Empty 可读、恢复粉色渐变按钮、永久删除红玻璃按钮

## 5. 不做（YAGNI）

- 不改 Tabs 组件结构/交互
- 不新增玻璃体系（复用既有 token）
- 不重构回收站 JS 逻辑
