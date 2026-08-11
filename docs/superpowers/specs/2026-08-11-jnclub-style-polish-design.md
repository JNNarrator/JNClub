# jnclub-web 样式打磨设计（方案 C：综合升级）

- 日期：2026-08-11
- 项目：`JNClub/jnclub-web`（Vue 3 + Naive UI + Vite）
- 状态：待实施

## 背景与目标

用户对 jnclub-web 当前样式的整体感受为「一般，想要明显变化」。经对话确认打磨方向覆盖四个方面：**质感与层次、布局与结构、视觉个性、色彩氛围**，动效取向为**更丰富**，色彩方向为**粉主色 + 氛围光**。

本设计保留现有设计语言（Apple Pink 粉色系 + 玻璃拟态 + 双主题 token），在此基础上做一轮全面升级。方案 C 分三个阶段实施，每阶段独立可验证。

## 现状审计要点（已完成的代码审计结论）

### 视觉「平」的根因
- `Home.vue` 内容区是**玻璃卡套玻璃卡**：外层 `.folder-column`/`.collection-column` 为玻璃卡片（`glass-bg-trans` + blur + 边框 + 阴影），内层 CollectionCard/NoteCard/目录行/tag-bar 又是玻璃/半透明——多层半透明叠加，对比度与边界模糊。

### 真实缺陷（必须修）
1. `DiskView.vue` L426 使用未定义变量 `var(--radius)`，应改为 `--radius-sm`。
2. `MainLayout.vue` L91 移动端底部留白 `64px`，与 `MobileTabBar` 实际高度 `56px + safe-area` 不一致。

### 不统一清单（打磨重点）
- hover 反馈三种语言：NoteCard（抬升+`--pink-peach` 描边）、Vault/Disk 条目（仅 `--brand` 描边）、NoteRow（仅背景色）。
- 圆角硬编码散落：2/4/6/8/9/12/16/18px，token 现有 `--radius-sm/md/lg/pill`（10/16/20/999px）。
- 激活态两套：实心渐变+白字（`.btn-new`/`.chip-active`/ViewSwitcher）vs 浅底粉字（`.tag-chip-active`/NavItem active）。
- 状态色硬编码：VaultView 健康角标 `#d97706`/`#dc2626` 绕过 `--state-warning/--state-error`。
- 拖拽 ghost 样式在 VaultView/DiskView/NoteGrid/NoteList 四文件各复制一份。
- `.glass-input` 覆盖在 PasswordEditorModal scoped 与 main.css 重复。
- `.glass-panel` 定义在 VaultView scoped 与 main.css 重复。
- 空状态两极分化：EmptyState（花瓣+呼吸）华丽 vs 云盘/密码库默认 NEmpty 素。
- 三个渐变按钮实现并存：`.empty-cta`、`.gen-btn`、`.glass-pill-btn`/`.glass-primary-btn`。
- 字号零散（11/12/13/14px 混用），tokens 无字号层。
- 标题 hover 语言：CollectionCard 标题 `--text-1`、CollectionRow 标题 `--link` 蓝，两种视图品牌一致性弱。
- MainLayout/MobileTabBar 高度不一致（见缺陷 2）。

## 用户已拍板的决策

1. **层次方案**：外层玻璃 + 内层实底（氛围光背景 → 玻璃面板 → 实底内容卡）。
2. **激活语言**：浅底粉字（主操作按钮保留实心渐变）。
3. **便签风格**：纸张感（`pinkWhite` 浅底 + 柔边框）。
4. **密码生成按钮**：保留绿色渐变，提炼为 token 并统一圆角/阴影到玻璃体系。

---

## 阶段 1：层次重构 + 氛围光 + 修缺陷

### 1.1 三级层次（Home.vue + main.css）
- 外层 `.folder-column`/`.collection-column`：保留毛玻璃面板，blur 改用**面板专用局部值约 14px**（不改全局 `--glass-blur` token，避免影响弹框/抽屉等其它玻璃元素），去除内部多重径向渐变，仅保留细边框（`--glass-border`）+ 轻阴影。
- 内层内容卡（CollectionCard/NoteCard/CollectionRow/NoteRow/FileItem/VaultItem/目录行）：半透明玻璃底 → **实底 `--bg-card`** + `--shadow-1`；hover 按统一语言（见 1.3）。
- 页面背景：`content-area` 两侧加品牌色径向光晕（复用 `--glass-glow-top`/`--glass-glow-bottom`），告别纯 `--bg-page`。

### 1.2 修真实缺陷
- `DiskView.vue` L426：`var(--radius)` → `var(--radius-sm)`。
- `MainLayout.vue` L91：底部留白 `64px` → `56px`。

### 1.3 统一语言（第一阶段落 token）
- **hover 统一规则**：
  - 卡片/网格项：抬升 2px + 品牌描边（统一用 `--brand`）+ `--shadow-card-hover`。
  - 列表行：浅底（`--glass-chip-bg`）+ 左侧 3px 品牌描边，不抬升。
- **圆角**：新增 `--radius-xs: 6px`；favicon 2/4px → 6px；ViewSwitcher 6px → `--radius-sm`；其余硬编码圆角全部落 token（PasswordEditorModal 16/9/8/10px、VaultView 12/18/8/6px、DiskView 8px、FolderTree 8px）。
- **状态色**：VaultView 健康角标 → `--state-warning`/`--state-error`，新增 `--state-warning-soft`/`--state-error-soft`（浅底变体）到 tokens。
- **激活语言**：目录 `.chip-active`、标签 `.tag-chip-active`、ViewSwitcher、NavItem 激活统一为「浅粉底 + 品牌字 + 描边」；`.btn-new`/`.glass-pill-btn` 等主操作按钮保留实心渐变。

---

## 阶段 2：模块性格 + 空状态统一

### 2.1 收藏夹（CollectionCard/CollectionGrid/CollectionRow）
- favicon 盒 hover：品牌光晕放大（`--glow-icon` 加强 + 轻微 scale）。
- 卡片标题区更突出（标题 `--text-1` 加粗、域名行保留 `--text-3`）。
- CollectionRow 标题从 `--link` 蓝改为 `--text-1`（与卡片视图一致）。

### 2.2 便签（NoteCard/NoteGrid/NoteRow）
- 便签卡片：`--pink-white` 纸张感浅底 + 柔边框，icon 盒保留品牌粉；与收藏夹玻璃卡片形成模块区分。

### 2.3 云盘（DiskView）
- 文件类型图标按扩展名彩色化（图片/文档/压缩包/音频/其他，映射到 tokens 的 pink 阶与状态色）。
- 批量操作条玻璃化（`--glass-bg-trans` + 细边框，替代当前 `--brand` 实线边框）+ 入场动画（阶段 3 实现）。

### 2.4 密码库（VaultView/PasswordEditorModal）
- 安全徽章：状态色 token 化（见 1.3 状态色）。
- 主密钥引导块（`.guide-warn` 黄底手写 rgba）→ 玻璃警告条（`--state-warning-soft` 底 + `--state-warning` 描边/文字）。
- PasswordEditorModal：删除 scoped 中与 main.css 重复的 `.glass-input` 覆盖；圆角落 token。
- `.gen-btn` 绿色渐变：提炼 `--gradient-success`/`--shadow-success` 到 tokens，圆角/阴影统一到玻璃按钮体系。

### 2.5 空状态统一（EmptyState 下沉）
- `EmptyState.vue` 组件参数化（props：title/sub/icon/cta 文案 + 花瓣色）。
- 云盘/密码库/便签的空状态替换默认 `NEmpty` 为 EmptyState 组件。

---

## 阶段 3：动效 + token 收敛

### 3.1 动效
- 模块切换：内容区 fade + 轻微 slide 过渡。
- 列表 stagger：Grid（translateY 12px/0.35s/0.05s）与 List（8px/0.3s/0.04s）参数统一为一套。
- 拖拽落位：「落定」动画（drop 时短暂 scale 回弹）。
- 批量操作条：底部滑入。
- 按钮光效：主按钮 hover 光晕（已有 `glass-pill-btn` 的 brightness 基础上加 glow）。
- 移动端 Tab：激活态加滑块/指示条过渡。

### 3.2 token 与代码收敛
- 新增字号 token 层：`--fs-xs`(11px)/`--fs-sm`(12px)/`--fs-md`(13px)/`--fs-base`(14px)，替换零散字号。
- 新增 `--radius-xs`(6px)、`--state-*-soft`、`--gradient-success`、`--shadow-success`。
- 三个渐变按钮 → 统一 `glass-pill-btn` 体系（EmptyState `.empty-cta` 复用该类）。
- 4 份拖拽 ghost → main.css 全局一份 `.sortable-ghost`/`.sortable-chosen`。
- `.glass-input` 重复覆盖去重（PasswordEditorModal scoped 删除）。
- `.glass-panel` 重复定义去重（VaultView scoped 删除）。

---

## 不做（明确排除）
- 不换主色系，保持 Apple Pink。
- 不动数据模型/后端/路由。
- 不做大范围组件结构重构（以 `<style>` 与类调整为主，涉及结构的改动仅限 EmptyState 参数化与必要的小改）。
- 不动 `music-frontend`（独立暖橙主题，不在本次范围）。
- 不动浏览器扩展。

## 验证方式
- 每阶段完成：`npm run build`（vue-tsc + vite build）零错误；浏览器人工验收明/暗双主题 × 各模块。
- 最终：`npx vue-tsc --noEmit` + `npm run build` 通过，明暗双主题下所有页面截图对比。
