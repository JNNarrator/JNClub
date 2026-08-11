# jnclub-web 样式打磨实施计划（方案 C 三阶段）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 jnclub-web 做一轮全面样式升级：解决「玻璃套玻璃」造成的视觉发平、统一全站交互/圆角/状态色语言、为各模块注入视觉性格，并丰富动效——保留 Apple Pink 设计语言，分三阶段落地，每阶段可独立验证。

**Architecture:** 阶段 1 重构层次（外层玻璃面板 + 内层实底卡片 + 氛围光背景）并修复缺陷与统一语言；阶段 2 为收藏/便签/云盘/密码库注入模块性格并统一空状态；阶段 3 丰富动效并收敛 token 体系（字号层、按钮、拖拽 ghost）。全部改动集中在 `src/themes/`、`src/assets/` 与 `src/modules/bookmark/`、`src/shared/layout/` 的 `<style>` 与少量模板/脚本。

**Tech Stack:** Vue 3.5 + TypeScript + Vite 6 + Naive UI 2.40 + 原生 CSS 变量（design tokens，无预处理器）。

## Global Constraints

- 设计规格：`docs/superpowers/specs/2026-08-11-jnclub-style-polish-design.md`（已提交，commit `671d32a`）
- 主色系不变：Apple Pink（亮 `#EC5B8E` / 暗 `#FF8FAB`），不换色系
- 用户已拍板决策：外层玻璃+内层实底；激活态统一浅底粉字（主操作按钮保留实心渐变）；便签纸张感（`--pink-white` 底）；密码生成按钮保留绿色并 token 化
- 不改数据模型/后端/路由；不动 `music-frontend`、`browser-extension`
- 改动以 `<style>` 与类调整为主，结构改动仅限 EmptyState 参数化与必要的模板小改
- 验证：每个 Task 完成后 `npm run build`（`vue-tsc && vite build`）必须零错误；阶段末用浏览器（Playwright）抽取 computed style 比对预期值
- 本地环境：dev server 已在 `localhost:5173`（base `/jnclub/`）、网关 `19005`、SSO `8080`、dufs `8000`、MySQL `3306` 均在运行

## 文件结构

| 文件 | 职责 | 改动 |
|---|---|---|
| `src/themes/tokens.ts` | 设计 token 定义 | 新增 radius-xs / state-*-soft / gradient-success / shadow-success / fs-* 字号层 |
| `src/assets/main.css` | 全局样式与玻璃体系 | 新增全局 `.sortable-ghost`/`.sortable-chosen`、`.chip-active` 改浅底粉字 |
| `src/modules/bookmark/views/Home.vue` | 主工作台布局 | 层次重构、氛围光、chip 激活语言 |
| `src/shared/layout/MainLayout.vue` | 应用骨架 | 移动端底部留白 64→56px |
| `src/modules/bookmark/components/CollectionCard.vue` | 收藏卡片 | 实底化、hover 统一、favicon 圆角 token |
| `src/modules/bookmark/components/CollectionRow.vue` | 收藏列表行 | 左缘描边 hover、标题改 `--text-1` |
| `src/modules/bookmark/components/NoteCard.vue` | 便签卡片 | 阶段1 实底化；阶段2 纸张感 |
| `src/modules/bookmark/components/NoteRow.vue` | 便签列表行 | 左缘描边 hover |
| `src/modules/bookmark/components/ViewSwitcher.vue` | 视图切换 | 激活态浅底粉字、圆角 token |
| `src/modules/bookmark/components/DiskView.vue` | 云盘 | 修 `var(--radius)` 缺陷、实底化、批量条玻璃化、彩色文件图标 |
| `src/modules/bookmark/components/VaultView.vue` | 密码库 | 状态色 token 化、去重、条目实底化、引导块玻璃化 |
| `src/modules/bookmark/components/PasswordEditorModal.vue` | 密码编辑弹框 | 去重、圆角 token、gen-btn token 化 |
| `src/modules/bookmark/components/EmptyState.vue` | 空状态 | 参数化（icon 扩展 file/vault） |
| `src/modules/bookmark/components/NoteGrid.vue` / `NoteList.vue` / `CollectionGrid.vue` / `CollectionList.vue` | 网格/列表 | 删 ghost 副本、stagger 统一、落定动画（阶段3） |
| `src/shared/layout/MobileTabBar.vue` | 移动端 Tab | 激活指示条（阶段3） |

---

# 阶段 1：层次重构 + 氛围光 + 修缺陷

### Task 1: tokens 扩展（radius-xs / 状态色 soft / 绿色渐变 / 字号层）

**Files:**
- Modify: `src/themes/tokens.ts`

**Interfaces:**
- Produces: 新增 CSS 变量 `--radius-xs`、`--state-warning-soft`、`--state-error-soft`、`--gradient-success`、`--shadow-success`、`--fs-xs/sm/md/base`（供后续所有 Task 使用）

- [ ] **Step 1: 扩展 `DesignTokens` 接口**（在 `fontMono` 前插入字号层，`radiusSm` 前插入 radiusXs，状态色后插入 soft 变体与渐变）

```ts
export interface DesignTokens {
  // ... 现有字段不动 ...
  stateSuccess: string
  stateWarning: string
  stateError: string
  stateInfo: string
  stateWarningSoft: string
  stateErrorSoft: string
  gradientSuccess: string
  shadowSuccess: string
  // ...
  radiusXs: string
  // ...
  fontSans: string
  fontMono: string
  fsXs: string
  fsSm: string
  fsMd: string
  fsBase: string
  // ...
}
```

- [ ] **Step 2: `lightTokens` 补值**

```ts
stateWarningSoft: 'rgba(243, 196, 112, 0.14)',
stateErrorSoft: 'rgba(232, 120, 120, 0.12)',
gradientSuccess: 'linear-gradient(135deg, #7AC686, #059669)',
shadowSuccess: '0 4px 14px -4px rgba(16, 185, 129, 0.5)',
radiusXs: '6px',
fsXs: '11px',
fsSm: '12px',
fsMd: '13px',
fsBase: '14px',
```

- [ ] **Step 3: `darkTokens` 补值**（暗色 soft 透明度略高，其余同 light）

```ts
stateWarningSoft: 'rgba(243, 196, 112, 0.16)',
stateErrorSoft: 'rgba(232, 120, 120, 0.14)',
gradientSuccess: 'linear-gradient(135deg, #7AC686, #059669)',
shadowSuccess: '0 4px 14px -4px rgba(16, 185, 129, 0.45)',
radiusXs: '6px',
fsXs: '11px',
fsSm: '12px',
fsMd: '13px',
fsBase: '14px',
```

- [ ] **Step 4: `tokensToCSSVars` 补映射**

```ts
'--state-warning-soft': tokens.stateWarningSoft,
'--state-error-soft': tokens.stateErrorSoft,
'--gradient-success': tokens.gradientSuccess,
'--shadow-success': tokens.shadowSuccess,
'--radius-xs': tokens.radiusXs,
'--fs-xs': tokens.fsXs,
'--fs-sm': tokens.fsSm,
'--fs-md': tokens.fsMd,
'--fs-base': tokens.fsBase,
```

- [ ] **Step 5: 验证** — `cd jnclub-web && npm run build` 零错误；浏览器 Console 执行 `getComputedStyle(document.documentElement).getPropertyValue('--radius-xs')` 返回 `6px`

- [ ] **Step 6: Commit**

```bash
git add jnclub-web/src/themes/tokens.ts
git commit -m "style(web): tokens 新增 radius-xs/状态色soft/成功渐变/字号层"
```

### Task 2: 全局 sortable-ghost 收敛 + chip-active 改浅底粉字

**Files:**
- Modify: `src/assets/main.css`
- Modify: `src/modules/bookmark/components/VaultView.vue`（删 scoped ghost 副本）
- Modify: `src/modules/bookmark/components/DiskView.vue`（删 scoped ghost 副本）
- Modify: `src/modules/bookmark/components/NoteGrid.vue`（删 scoped ghost 副本）
- Modify: `src/modules/bookmark/components/NoteList.vue`（删 scoped ghost 副本）

**Interfaces:**
- Consumes: Task 1 的 `--radius-sm`（已存在）与 `--brand-soft`/`--brand`
- Produces: 全局 `.sortable-ghost`/`.sortable-chosen` 类；`.chip-active` 新激活语言

- [ ] **Step 1: main.css 新增全局拖拽视觉**（加在 `/* ---- 选中文本色 ---- */` 附近）

```css
/* ---- 拖拽排序视觉（全局一份） ---- */
.sortable-ghost {
  opacity: 0.4;
  background: var(--brand-soft) !important;
  border-radius: var(--radius-sm);
  outline: 2px dashed var(--brand);
  outline-offset: -2px;
}
.sortable-chosen { cursor: grabbing; }
```

- [ ] **Step 2: main.css 改 `.chip-active`**（实心渐变 → 浅底粉字；`--glass-chip-border` 来自既有 token）

```css
.chip-active {
  background: var(--brand-soft) !important;
  color: var(--brand) !important;
  border-color: var(--glass-chip-border) !important;
  font-weight: 600;
  box-shadow: none;
}
```

- [ ] **Step 3: 删除四处 scoped ghost 副本**
- VaultView.vue ~L541-547：删 `.vault-list :deep(.sortable-ghost) { ... }` 与 `.sortable-chosen` 两段（保留其它内容）
- DiskView.vue ~L478-485：删 `.file-list :deep(.sortable-ghost) { ... }` 与 `.file-list :deep(.sortable-chosen) { cursor: grabbing; }`
- NoteGrid.vue ~L88-97：删 `.note-grid :deep(.sortable-ghost)` 与 `.sortable-chosen`
- NoteList.vue ~L59-65：同上

- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器拖拽卡片出现虚线 outline（人工）

- [ ] **Step 5: Commit**

```bash
git add src/assets/main.css src/modules/bookmark/components/VaultView.vue src/modules/bookmark/components/DiskView.vue src/modules/bookmark/components/NoteGrid.vue src/modules/bookmark/components/NoteList.vue
git commit -m "style(web): sortable-ghost 全局收敛，chip 激活态改浅底粉字"
```

### Task 3: Home.vue 层次重构 + 氛围光

**Files:**
- Modify: `src/modules/bookmark/views/Home.vue`（L940-955 folder-column、L1016-1029 collection-column、L915-926 content-area）

**Interfaces:**
- Consumes: 既有 `--glass-bg-trans`/`--glass-glow-top`/`--glass-glow-bottom`/`--glass-border`/`--radius-md`
- Produces: 「氛围光背景 → 玻璃面板 → 实底内容卡」三级层次

- [ ] **Step 1: `.content-area` 加氛围光**（容器本身无背景，直接加光晕；找到 `.content-area` 选择器，在其现有声明中补 `background`）

```css
.content-area {
  /* ... 现有 flex/max-width 等不动 ... */
  background:
    radial-gradient(700px 280px at 4% 0%, var(--glass-glow-top), transparent 55%),
    radial-gradient(700px 280px at 96% 100%, var(--glass-glow-bottom), transparent 55%);
}
```

- [ ] **Step 2: `.folder-column` 面板化**（去内部渐变、blur 局部 14px、去 glass-shadow 改轻阴影）

```css
.folder-column {
  display: flex;
  flex-direction: column;
  flex: 0 0 220px;
  min-height: 0;
  overflow: hidden;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: 14px 12px;
  box-shadow: var(--shadow-1);
}
```

- [ ] **Step 3: `.collection-column` 面板化**（同 folder-column：去径向渐变、blur 14px、shadow-1）

```css
.collection-column {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: 18px 20px;
  box-shadow: var(--shadow-1);
}
```

- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器 `getComputedStyle(document.querySelector('.collection-column')).backdropFilter` 为 `blur(14px)`、无 radial-gradient 背景图

- [ ] **Step 5: Commit**

```bash
git add jnclub-web/src/modules/bookmark/views/Home.vue
git commit -m "style(web): 主工作台层次重构——玻璃面板+氛围光背景"
```

### Task 4: MainLayout 移动端留白修复

**Files:**
- Modify: `src/shared/layout/MainLayout.vue`（L91）

- [ ] **Step 1: `64px` → `56px`**

```css
.app-layout.is-mobile .app-content {
  padding-bottom: calc(56px + env(safe-area-inset-bottom));
}
```

- [ ] **Step 2: 验证** — `npm run build` 零错误
- [ ] **Step 3: Commit** — `git add jnclub-web/src/shared/layout/MainLayout.vue && git commit -m "fix(web): 移动端底部留白对齐 TabBar 高度 56px"`

### Task 5: 内容卡实底化 + hover 统一（收藏卡/便签卡）

**Files:**
- Modify: `src/modules/bookmark/components/CollectionCard.vue`（L123-145、L180）
- Modify: `src/modules/bookmark/components/NoteCard.vue`（L102-124）

**Interfaces:**
- Consumes: `--bg-card`/`--border`/`--shadow-1`/`--shadow-card-hover`/`--brand`/`--radius-xs`
- Produces: 内层卡片实底（阶段 2 便签再改纸张感）

- [ ] **Step 1: CollectionCard 实底化 + hover 描边统一 `--brand`**

```css
.bookmark-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  overflow: hidden;
  box-shadow: var(--shadow-1);
}

.bookmark-card:hover {
  transform: translateY(-2px);
  border-color: var(--brand);
  box-shadow: var(--shadow-card-hover);
}

.bookmark-card:active {
  transform: translateY(0);
}
```

- [ ] **Step 2: CollectionCard favicon 圆角 token 化**（L180）

```css
.favicon-img {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-xs);
}
```

- [ ] **Step 3: NoteCard 实底化 + hover 统一**（与 Step 1 同构；background 改 `var(--bg-card)`、无 backdrop-filter、border `var(--border)`、hover 描边 `var(--brand)`、shadow 同上）
- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器卡片 `backgroundColor` 为实色 `rgb(28,28,30)`（暗色）/`rgb(255,255,255)`（亮色）、`backdropFilter` 为 `none`
- [ ] **Step 5: Commit**

```bash
git add jnclub-web/src/modules/bookmark/components/CollectionCard.vue jnclub-web/src/modules/bookmark/components/NoteCard.vue
git commit -m "style(web): 内容卡片实底化，hover 描边统一 --brand"
```

### Task 6: 列表行 hover 统一（左缘描边）

**Files:**
- Modify: `src/modules/bookmark/components/CollectionRow.vue`（L117-133、L148）
- Modify: `src/modules/bookmark/components/NoteRow.vue`（hover 段）

**Interfaces:**
- Consumes: `--glass-chip-bg`/`--brand`/`--radius-xs`

- [ ] **Step 1: CollectionRow hover 加左缘品牌描边**（用 inset 阴影，避免撑宽布局；favicon 圆角 2px → `--radius-xs`）

```css
.collection-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}

.collection-row:hover {
  background: var(--glass-chip-bg);
  box-shadow: inset 3px 0 0 var(--brand);
}

.collection-row:active {
  background: var(--brand-soft);
  box-shadow: inset 3px 0 0 var(--brand);
}
```

- [ ] **Step 2: CollectionRow `.favicon-img` 圆角 2px → `var(--radius-xs)`**
- [ ] **Step 3: NoteRow hover 同构**（找 `.note-row:hover` 的 background 段，追加 `box-shadow: inset 3px 0 0 var(--brand)`，transition 补 box-shadow）
- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器 hover 列表行出现左侧 3px 粉色竖线
- [ ] **Step 5: Commit**

```bash
git add jnclub-web/src/modules/bookmark/components/CollectionRow.vue jnclub-web/src/modules/bookmark/components/NoteRow.vue
git commit -m "style(web): 列表行 hover 统一左缘品牌描边"
```

### Task 7: ViewSwitcher 激活态浅底粉字

**Files:**
- Modify: `src/modules/bookmark/components/ViewSwitcher.vue`（L58、L68-73）

- [ ] **Step 1: 圆角 token + 激活态改浅底粉字**

```css
.switcher-btn {
  border: none;
  background: transparent;
  padding: 5px 15px;
  font-size: var(--fs-md);
  font-weight: 500;
  color: var(--text-2);
  cursor: pointer;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

.switcher-btn.active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
  box-shadow: none;
}
```

- [ ] **Step 2: 验证** — `npm run build` 零错误；浏览器切换视图时激活按钮为浅粉底 + 粉字
- [ ] **Step 3: Commit** — `git commit -m "style(web): 视图切换激活态统一浅底粉字"`（先 add 该文件）

### Task 8: DiskView 缺陷修复 + 条目实底化

**Files:**
- Modify: `src/modules/bookmark/components/DiskView.vue`（L426 `var(--radius)`、L486-501 file-item、L506 file-icon）

- [ ] **Step 1: 修 `var(--radius)` → `var(--radius-sm)`**（upload-progress-card 的 border-radius）
- [ ] **Step 2: file-item 实底化 + hover 过渡**

```css
.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease), background var(--dur) var(--ease);
}
.file-item:hover { border-color: var(--brand); box-shadow: var(--shadow-card-hover); }
.file-item-selected {
  border-color: var(--brand);
  background: var(--brand-soft);
}
```

- [ ] **Step 3: file-icon 圆角 8px → `--radius-xs`**（L506）
- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器无 `var(--radius)` 无效回退、文件行实底
- [ ] **Step 5: Commit** — `git commit -m "fix(web): 云盘修复无效 var(--radius)，条目实底化"`

### Task 9: VaultView 状态色 token 化 + 去重 + 条目实底化

**Files:**
- Modify: `src/modules/bookmark/components/VaultView.vue`（L389-397 glass-panel、L426-435 guide-warn、L537-560 vault-item 区、L611-620 health-tag）

**Interfaces:**
- Consumes: Task 1 的 `--state-warning-soft`/`--state-error-soft`

- [ ] **Step 1: `.glass-panel` 去重**（scoped 只保留 padding/border-radius，删 background/blur/border/shadow 重复声明）

```css
.glass-panel {
  padding: 28px 24px;
  border-radius: var(--radius-md);
}
```

- [ ] **Step 2: `.guide-warn` 用 soft token**（替换手写 rgba(217,119,6,...)）

```css
.guide-warn {
  font-size: var(--fs-sm);
  color: var(--state-warning);
  background: var(--state-warning-soft);
  border: 1px solid color-mix(in srgb, var(--state-warning) 25%, transparent);
  border-radius: var(--radius-sm);
  padding: 8px 12px;
  margin-bottom: 14px;
  line-height: 1.6;
}
```

- [ ] **Step 3: vault-item 实底化 + hover 左缘描边**（找 `.vault-item` 样式段：background → `var(--bg-card)`、删 backdrop-filter、border → `var(--border)`，hover 加 `box-shadow: inset 3px 0 0 var(--brand)` + 背景浅色）

```css
.vault-item {
  /* ... 现有布局属性不动 ... */
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease), background var(--dur) var(--ease);
}
.vault-item:hover {
  background: var(--glass-chip-bg);
  box-shadow: inset 3px 0 0 var(--brand);
}
```

- [ ] **Step 4: `.health-tag` 用状态 token**

```css
.health-tag.weak {
  background: var(--state-warning-soft) !important;
  color: var(--state-warning) !important;
  border: 1px solid var(--state-warning-soft) !important;
}
.health-tag.dup {
  background: var(--state-error-soft) !important;
  color: var(--state-error) !important;
  border: 1px solid var(--state-error-soft) !important;
}
```

- [ ] **Step 5: 验证** — `npm run build` 零错误；浏览器密码库条目实底、弱密码角标为琥珀色系 token 值
- [ ] **Step 6: Commit** — `git commit -m "style(web): 密码库状态色 token 化、条目实底、玻璃面板去重"`

### Task 10: PasswordEditorModal 去重 + 圆角 token + gen-btn 成功渐变

**Files:**
- Modify: `src/modules/bookmark/components/PasswordEditorModal.vue`（L158-163、L179-183、L208-217、L225-256）

**Interfaces:**
- Consumes: Task 1 的 `--gradient-success`/`--shadow-success`/`--radius-xs`

- [ ] **Step 1: 圆角 token 化**：`.pwd-modal` 16px→`var(--radius-md)`；`.lock-chip` 9px→`var(--radius-sm)`；`.length-badge` 999px→`var(--radius-pill)`；`.confirm-btn`/`.ghost-btn` 10px→`var(--radius-sm)`
- [ ] **Step 2: `.gen-btn` 用成功渐变 token**

```css
.gen-btn {
  background: var(--gradient-success) !important;
  box-shadow: var(--shadow-success);
}
.gen-btn:hover { filter: brightness(1.1); }
```

- [ ] **Step 3: `.pwd-modal :deep(.n-input)` 去重**（main.css 已全局覆盖 `.n-modal .n-input` 的 color/border/focus；scoped 只保留 main.css 未覆盖的文本色与占位符色 + 圆角）

```css
.pwd-modal :deep(.n-input) {
  border-radius: var(--radius-sm);
  --n-text-color: var(--text-1) !important;
  --n-placeholder-color: var(--glass-text-placeholder) !important;
}
```

- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器打开密码编辑弹框：生成按钮绿色渐变、输入框聚焦辉光正常
- [ ] **Step 5: Commit** — `git commit -m "style(web): 密码弹框圆角/成功渐变 token 化，输入框覆盖去重"`

### Task 11: 阶段 1 验收

- [ ] **Step 1: 全量构建** — `cd jnclub-web && npm run build` 零错误
- [ ] **Step 2: 浏览器全量抽查**（Playwright）：明/暗双主题 × 收藏夹/便签/云盘/密码库，检查：两列面板 blur 14px、内容卡实底、chip/视图切换/导航激活浅底粉字、hover 左缘描边、无 `var(--radius)` 报错
- [ ] **Step 3: Commit**（如无代码改动则跳过）— `git commit -m "style(web): 阶段1验收通过"`

---

# 阶段 2：模块性格 + 空状态统一

### Task 12: 收藏夹性格（favicon 光晕 + 标题统一）

**Files:**
- Modify: `src/modules/bookmark/components/CollectionCard.vue`（favicon-box）
- Modify: `src/modules/bookmark/components/CollectionRow.vue`（title-link）

- [ ] **Step 1: favicon 盒 hover 品牌光晕放大**（CollectionCard）

```css
.favicon-box {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  background: var(--pink-cherry);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--glow-icon);
  flex-shrink: 0;
  transition: box-shadow var(--dur) var(--ease), transform var(--dur) var(--ease-bouncy);
}
.bookmark-card:hover .favicon-box {
  box-shadow: 0 0 0 6px var(--focus-ring), var(--glow-icon);
  transform: scale(1.06);
}
```

- [ ] **Step 2: CollectionRow `.title-link` 从链接蓝改内容色**（与卡片视图标题一致）

```css
.title-link {
  color: var(--text-1) !important;
  font-size: var(--fs-base);
  font-weight: 500;
}
```

- [ ] **Step 3: 验证** — `npm run build` 零错误；浏览器 hover 收藏卡 favicon 放大发光、列表标题为正文色
- [ ] **Step 4: Commit** — `git commit -m "style(web): 收藏卡 favicon 光晕，列表标题统一正文色"`

### Task 13: 便签纸张感

**Files:**
- Modify: `src/modules/bookmark/components/NoteCard.vue`（L102-114）

**Interfaces:**
- Consumes: `--pink-white`/`--pink-peach`

- [ ] **Step 1: 便签卡改纸张感**（浅粉实底 + 柔边框；保留顶部渐变条与 hover 抬升）

```css
.note-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--pink-white);
  border: 1px solid color-mix(in srgb, var(--pink-peach) 40%, transparent);
  border-radius: var(--radius-md);
  cursor: pointer;
  overflow: hidden;
  box-shadow: var(--shadow-1);
}

.note-card:hover {
  transform: translateY(-2px);
  border-color: var(--brand);
  box-shadow: var(--shadow-card-hover);
}
```

- [ ] **Step 2: 验证** — `npm run build` 零错误；浏览器便签卡为浅粉纸感底色（亮 `#FFF5F8` 系、暗 `#2D1F26` 系），与收藏卡形成区分
- [ ] **Step 3: Commit** — `git commit -m "style(web): 便签卡纸张感浅粉底"`

### Task 14: 云盘彩色文件图标 + 批量条玻璃化

**Files:**
- Modify: `src/modules/bookmark/components/DiskView.vue`（script 图标映射 + 模板 file-icon + batch-bar 样式）

- [ ] **Step 1: script 加扩展名类型映射**（在 `<script setup>` 中新增；颜色取既有 tokens 的 pink 阶与状态色）

```ts
const FILE_KINDS = [
  { re: /\.(png|jpe?g|gif|webp|svg|avif)$/i, label: '图片', color: '#7EB8E8' },
  { re: /\.(docx?|pdf|txt|md|pptx?|xlsx?)$/i, label: '文档', color: '#F472B6' },
  { re: /\.(zip|rar|7z|tar|gz)$/i, label: '压缩包', color: '#F3C470' },
  { re: /\.(mp3|wav|flac|aac|ogg|m4a)$/i, label: '音频', color: '#7AC686' },
] as const
const fileKindColor = (name: string) => FILE_KINDS.find(k => k.re.test(name))?.color ?? 'var(--text-3)'
```

- [ ] **Step 2: 模板 file-icon 套色**（在 `.file-icon` 上 `:style="{ color: fileKindColor(file.name) }"`；若模板为渲染函数则等效注入 style）
- [ ] **Step 3: `.batch-bar` 玻璃化**（找 batch-bar 样式段：`--brand` 实线边框 → 玻璃面板 + 品牌描边）

```css
.batch-bar {
  /* ... 现有定位/布局不动 ... */
  background: var(--glass-bg-trans);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  box-shadow: var(--glass-shadow);
}
```

- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器云盘文件按类型彩色图标、批量操作条玻璃化
- [ ] **Step 5: Commit** — `git commit -m "feat(web): 云盘文件类型彩色图标，批量条玻璃化"`

### Task 15: EmptyState 参数化 + 下沉到云盘/密码库

**Files:**
- Modify: `src/modules/bookmark/components/EmptyState.vue`（icon prop 扩展 + SVG）
- Modify: `src/modules/bookmark/components/DiskView.vue`（空态替换）
- Modify: `src/modules/bookmark/components/VaultView.vue`（如存在空态分支）

**Interfaces:**
- Produces: `icon?: 'bookmark' | 'note' | 'file' | 'vault'`

- [ ] **Step 1: EmptyState 扩展 icon**（props 联合类型加 `'file' | 'vault'`；新增两个 SVG 分支：file=文档+文件夹图标、vault=盾牌+钥匙图标，白描边风格与现有保持一致）

```ts
defineProps<{
  message?: string
  hint?: string
  icon?: 'bookmark' | 'note' | 'file' | 'vault'
  ctaLabel?: string
}>()
```

- [ ] **Step 2: DiskView 空态替换**（现有 `.disk-empty` 内若为默认 NEmpty，替换为 `<EmptyState icon="file" message="这个目录还没有文件" hint="上传第一个文件开始整理" cta-label="上传文件" @create="触发上传" />`；确认模板已有 `triggerUpload` 之类方法，若无则加在 script 中并调用隐藏 file input）
- [ ] **Step 3: VaultView 空态替换**（若解锁态存在空列表分支，替换为 `<EmptyState icon="vault" message="密码库空空如也" hint="添加第一条密码，开始你的安全清单" cta-label="新建密码" @create="触发新建" />`；若无空态分支则跳过，只做 icon 支持）
- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器空目录/空密码库显示花瓣空状态
- [ ] **Step 5: Commit** — `git commit -m "feat(web): 空状态统一组件下沉云盘/密码库"`

### Task 16: 阶段 2 验收

- [ ] **Step 1: 全量构建** — `npm run build` 零错误
- [ ] **Step 2: 浏览器验收**：四个模块各具性格（收藏光晕/便签纸张/云盘彩色图标/密码库状态 token）、空状态统一、明暗双主题正常
- [ ] **Step 3: Commit**（如有改动）— `git commit -m "style(web): 阶段2验收通过"`

---

# 阶段 3：动效 + token 收敛

### Task 17: 模块切换过渡 + stagger 统一

**Files:**
- Modify: `src/modules/bookmark/views/Home.vue`（spin-area 包 Transition）
- Modify: `src/modules/bookmark/components/CollectionGrid.vue` / `CollectionList.vue`
- Modify: `src/modules/bookmark/components/NoteGrid.vue` / `NoteList.vue`
- Modify: `src/assets/main.css`（全局 `jnclub-cardIn` keyframes）

- [ ] **Step 1: main.css 新增统一卡片入场 keyframes**（供四个 Grid/List 引用，删各自 scoped 副本）

```css
@keyframes jnclub-cardIn {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}
```

- [ ] **Step 2: 四个 Grid/List 统一 stagger**：animation 改为 `jnclub-cardIn 0.32s var(--ease) both`，delay `calc(var(--i) * 0.045s)`；删除各文件 scoped 中重复的 `fadeSlideIn` keyframes 定义
- [ ] **Step 3: Home.vue 模块内容切换过渡**（spin-area 内容包 `<Transition name="module-fade" mode="out-in">`；新增样式）

```css
.module-fade-enter-active,
.module-fade-leave-active {
  transition: opacity 0.18s var(--ease), transform 0.18s var(--ease);
}
.module-fade-enter-from { opacity: 0; transform: translateY(8px); }
.module-fade-leave-to { opacity: 0; transform: translateY(-6px); }
```

- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器切换模块时内容淡入下滑、卡片交错入场节奏统一
- [ ] **Step 5: Commit** — `git commit -m "feat(web): 模块切换过渡与卡片入场动画统一"`

### Task 18: 拖拽落定动画 + 批量条入场 + Tab 指示条

**Files:**
- Modify: `src/modules/bookmark/components/NoteGrid.vue` / `CollectionGrid.vue`（sortable onEnd 落定）
- Modify: `src/modules/bookmark/components/DiskView.vue`（batch-bar 入场）
- Modify: `src/shared/layout/MobileTabBar.vue`（激活指示条）

- [ ] **Step 1: 拖拽落定动画**（在 sortablejs `onEnd` 回调中给当前拖拽 DOM 节点加临时类；CSS 定义回弹）

```css
.drop-settle {
  animation: jnclub-dropSettle 0.28s var(--ease-bouncy);
}
@keyframes jnclub-dropSettle {
  0% { transform: scale(0.97); }
  100% { transform: scale(1); }
}
```

```ts
// 在 NoteGrid / CollectionGrid 的 Sortable 实例 onEnd 里：
onEnd: (evt) => {
  const el = evt.item
  el.classList.remove('drop-settle')
  void el.offsetWidth // 强制重排以重播动画
  el.classList.add('drop-settle')
  el.addEventListener('animationend', () => el.classList.remove('drop-settle'), { once: true })
}
```

- [ ] **Step 2: batch-bar 入场**（DiskView 模板批量条外包 `<Transition name="batch-up">` 或依赖 v-if 显示时挂类；样式）

```css
.batch-up-enter-active { transition: opacity 0.22s var(--ease), transform 0.22s var(--ease-bouncy); }
.batch-up-enter-from { opacity: 0; transform: translateY(14px); }
```

- [ ] **Step 3: MobileTabBar 激活指示条**（`.tab-active` 底部 3px 圆角指示条，宽度过渡）

```css
.tab-item { position: relative; }
.tab-item.tab-active::after {
  content: '';
  position: absolute;
  bottom: 6px;
  left: 50%;
  transform: translateX(-50%);
  width: 16px;
  height: 3px;
  border-radius: 2px;
  background: var(--brand);
  transition: width var(--dur) var(--ease);
}
.tab-item.tab-active:hover::after { width: 22px; }
```

- [ ] **Step 4: 验证** — `npm run build` 零错误；浏览器拖拽卡片松手回弹、批量条滑入、移动端 Tab 有指示条
- [ ] **Step 5: Commit** — `git commit -m "feat(web): 拖拽落定/批量条入场/Tab指示条动效"`

### Task 19: 字号 token 应用 + 按钮体系收敛

**Files:**
- Modify: `src/modules/bookmark/components/EmptyState.vue`（empty-cta 复用 glass-pill-btn）
- Modify: `src/modules/bookmark/views/Home.vue`、`src/modules/bookmark/components/CollectionRow.vue`、`NoteRow.vue`、`VaultView.vue`、`DiskView.vue`、`src/shared/layout/SideNav.vue`（字号替换）

- [ ] **Step 1: EmptyState `.empty-cta` 对齐玻璃按钮体系**（模板元素追加 `glass-pill-btn` 类，保留尺寸自定义；删 scoped 中重复的 background/border-radius/shadow 声明，保留 padding/font-size）

```html
<button class="empty-cta glass-pill-btn jnclub-bouncy-slow" @click="emit('create')">
```

- [ ] **Step 2: 字号 token 替换**：各组件 scoped 中 `font-size: 11px` → `var(--fs-xs)`、`12px` → `var(--fs-sm)`、`13px` → `var(--fs-md)`、`14px` → `var(--fs-base)`（优先替换 Home.vue 公共区、SideNav、行组件；卡片内部字号可保留现状）
- [ ] **Step 3: 验证** — `npm run build` 零错误；浏览器字号与改动前视觉一致（仅 token 化，不改变观感）
- [ ] **Step 4: Commit** — `git commit -m "refactor(web): 字号 token 化，空状态按钮复用玻璃体系"`

### Task 20: 阶段 3 全量验收 + 收尾

- [ ] **Step 1: 全量构建** — `cd jnclub-web && npm run build` 零错误
- [ ] **Step 2: 浏览器全量验收**（Playwright）：明/暗 × 收藏/便签/云盘/密码库/回收站/欢迎页/Welcome 全部过一遍：层次感、模块性格、动效、激活语言、空状态、移动端视口（375px 宽度）
- [ ] **Step 3: 输出验收报告**：每页面截图 + computed style 抽查记录
- [ ] **Step 4: Commit** — `git commit -m "style(web): jnclub 样式打磨三阶段完成"`

---

## Self-Review 对照

- **Spec 覆盖**：1.1 层次→Task 3/5/8/9；1.2 缺陷→Task 4/8；1.3 语言统一→Task 1/2/6/7/9/10；2.1 收藏→Task 12；2.2 便签→Task 13；2.3 云盘→Task 14；2.4 密码库→Task 9/10；2.5 空状态→Task 15；3.1 动效→Task 17/18；3.2 收敛→Task 1/2/19 ✓
- **占位符扫描**：无 TBD/TODO；所有 CSS 均给出具体值 ✓
- **类型一致性**：`--radius-xs`/`--state-warning-soft`/`--gradient-success`/`--fs-*` 在 Task 1 定义、后续 Task 引用，命名一致 ✓；`fileKindColor` 在 Task 14 定义并只在本 Task 使用 ✓
