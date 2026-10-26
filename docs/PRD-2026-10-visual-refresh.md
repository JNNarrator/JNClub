# PRD · 2026-10 · JNClub 视觉体系重塑（增量）

> 类型：**增量 PRD**（只描述变更，不重述项目全貌）
> 阶段：产品定义 → 待架构设计 → 待实施
> 上游依据：`docs/PLAN-2026-19.md`（已实施）、`.ui-review/ui-review-report.md`（12 批已实施）、`.ui-review/decisions-ia.md`（6 项已决策并闭环）
> 结论先行：本轮**不换品牌色、不去玻璃、不去粒子背景、不推翻设计系统**。要修的是「配比与层级」——让粉色回到强调位、让玻璃回到浮起位、让表面之间第一次有阶梯。

---

## 一、项目信息

| 项 | 内容 |
| --- | --- |
| Language | 中文 |
| 技术栈 | 主站 `jnclub-web/`：Vue3 + TS + Vite + Naive UI + Pinia；音乐 `music-frontend/`：Vue3 + Vant（iframe 嵌入）。**本轮不改技术栈、不引入新依赖** |
| Project Name | `jnclub_visual_refresh_2026_10` |
| 影响范围 | `jnclub-web/src`（约 25 个样式文件 + 2 个 token 文件）；`music-frontend/` **不在本轮范围**（主题透传机制不动） |
| 原始需求复述 | 用户原话：「还是想针对 ui 视觉做一次优化」。经澄清选择：**方向＝视觉体系重塑**（不是修 bug）；**风格边界＝保留品牌粉 + 玻璃拟态，只提精度**——不换品牌色、不去玻璃、不去粒子背景，但要把「配比与层级」做对。 |

### 与上一轮的分工（避免重复）

| 轮次 | 解决的真问题 | 状态 |
| --- | --- | --- |
| `PLAN-2026-19` | **质量地基**：token CSS 化（修 FOUC）、对比度达标、焦点可见、选中态、弹层过渡统一、动效收敛、骨架屏铺开、暗色层次、圆角字号归零 | ✅ 已实施，**不重开** |
| `.ui-review` 12 批 | **一致性**：同一元素两套写法、页头不统一、chip 语言分裂、TabBar 溢出、状态色令牌混用、hover 语言分裂 | ✅ P0/P1/P2 基本闭环，**不重开** |
| **本轮（2026-10）** | **视觉层级与配比**：粉色从强调色膨胀成环境色；表面没有 page/surface/raised 阶梯；图表是第二套色系；玻璃用满等于没有层级 | ← 本文档 |

---

## 二、视觉目标与气质定义

用**可判定**的语言定义改造后的样子。每条都能被脚本或人眼直接判真伪。

### 2.1 三条一句话目标

1. **粉色重新变成「指示器」而不是「空气」**：任一屏里，粉色只出现在「需要你注意的地方」（CTA、激活项、进度、选中、焦点），不出现在「你在看的内容的背景上」。
2. **表面之间有阶梯**：page → surface → raised → overlay 四层，相邻两层必须能靠「明度差 + 阴影 + 描边」任一维度区分，不靠「粉色深浅」区分。
3. **玻璃是「浮起来」的语法**：只有**有内容从其下方经过**的表面才允许用毛玻璃；平铺在页面里的卡片一律实色。

### 2.2 可判定的气质定义

| # | 判定语句 | 判据 |
| --- | --- | --- |
| G1 | 任一屏的**粉色承载元素**（background / border-color 解析为品牌粉色相的 DOM 元素，1px 文字描边不计）不超过 **12 个** | DOM 脚本计数 |
| G2 | 任一屏的**粉色可见面积占比** ≤ **10%**，其中**饱和粉**（`--brand` 原值及渐变）≤ **2%** | 1440×900 截图色相采样 |
| G3 | 任一屏**同屏毛玻璃层数** ≤ **3**（顶栏 1 + 至多 2 个浮层/导航）；概览页 ≤ **3** | `backdrop-filter` 生效元素计数 |
| G4 | 任一屏**文本字号档位** ≤ **4 档**（不含 emoji、图标、图表轴标签） | 计算样式去重计数 |
| G5 | 任一 **L1 surface 的 background 必须是 alpha = 1 的实色**（不允许 rgba / 玻璃） | 计算样式断言 |
| G6 | 亮色下**相邻两层表面的描边必须可见**（对比度 ≥ 1.2:1，或改用中性 border 而非白色 border） | 计算样式 + 对比度 |
| G7 | 卡片不再靠「顶部 3px 品牌渐变条」表达身份；模块身份由**图标色 + 图标盒**表达 | 视觉走查 |
| G8 | 粉色不得作为**页面底色、内容面板底色、卡片底色、默认边框色**出现 | 视觉走查 + 计算样式 |

### 2.3 气质关键词（给设计/工程对齐用）

> **克制、有呼吸、材料分明** —— 不是「更淡」，是「该重的地方重、该退的地方退干净」。

明确**不要**的方向：不要「更粉、更梦幻、更渐变」；不要「全灰极简、去品牌感」；不要「深色玻璃 + 霓虹光晕」的仪表盘风。

---

## 三、表面层级体系（方案地基）

### 3.1 四层定义

| 层 | 语义 | 材质 | 谁用 | 允许的 token |
| --- | --- | --- | --- | --- |
| **L0 page** | 页面底。永远在最下，不承载内容 | **实色**，无玻璃、无品牌光晕（至多 1 层 ≤6% 品牌光晕，且全站只允许一处来源） | `MainLayout .app-content`、`Home .content-area` | `--bg-page` |
| **L1 surface** | 内容面板。承载主要信息的最小容器 | **实色** + 1px **中性**描边 + `--shadow-1` | `JPageShell .j-page-body`、`.collection-column`、`.folder-column`、`.panel`（概览）、`JStatCard`、`CollectionCard`/`NoteCard`、列表行容器 | `--surface-1`（复用 `--bg-card`）、`--surface-border`、`--shadow-1` |
| **L2 raised** | 从 L1 上浮起一档：hover、选中、拖拽、内嵌块 | **实色** + 更强阴影 + 可选 1px 品牌淡描边 | 卡片 hover 态、`.is-selected`、`.sortable-fallback`、图标盒、chip 底、列表行 hover | `--surface-raised`（= `--bg-card`）、`--shadow-card-hover`、`--brand-soft` |
| **L3 overlay** | 浮起于内容之上，有内容从其下方经过 | **玻璃**（`backdrop-filter: blur`）+ `--glass-shadow` | `n-modal` / `n-dialog` / `n-drawer` / `n-popover` / `n-message` / `n-notification`、`AppHeader .glass-header`、`MobileTabBar`、`JBatchBar`（批量操作条）、`ViewSwitcher` 悬浮态、trend 悬浮 tooltip | `--glass-*`、`--panel-bg`、`--overlay-bg` |

### 3.2 唯一的判定规则（给工程的硬规则）

> **玻璃 = 有内容从其下方经过。**
> 满足这一条才允许 `backdrop-filter`。不满足的一律实色。

推论（直接可执行）：
- ✅ **保留玻璃**：弹层/抽屉/气泡/Message、顶栏（内容滚动时从其下经过）、移动端 TabBar（内容从其下经过）、批量操作条、图表悬浮 tooltip。
- ❌ **改实色**：`JPageShell .j-page-body`（内容在它内部滚，不在它下方）、`.collection-column` / `.folder-column`（内容在内部滚）、`DashboardView .panel`、`JStatCard`、`greet-card`（常驻横幅，当前竟用了 `glass-card--modal` 弹窗级玻璃）、`SideNav`（内容不从其下经过，它是 layout sider）。
- ⚠️ **已经是现状但需收口**：`main.css` 里 `.side-nav / .folder-column / .collection-column` 已因**性能**被迫 `backdrop-filter: none !important` 改用渐变模拟玻璃 —— 这正是「玻璃用超了」的实证。本轮把它们正式改实色，删掉这段「模拟玻璃」的补丁，代码与设计意图重新一致。

### 3.3 明度阶梯（亮/暗两套）

| 层 | 亮色 | 暗色 | 约束 |
| --- | --- | --- | --- |
| L0 page | `#F5F5F7`（现状）→ 建议压到 `#F2F2F5`（**待确认 Q1**） | `#000000`（现状，**待确认 Q6**） | L0 必须是全站最低明度（亮色）/ 最高明度（暗色） |
| L1 surface | `#FFFFFF` | `#26262A` | 亮色 page↔surface ΔL\* ∈ **[3.5, 6]**（克制但可辨）；暗色 ΔL\* ≥ **16** |
| L2 raised | `#FFFFFF` + `--shadow-card-hover` | `#323238` | 亮色靠阴影抬升（不靠改底色）；暗色靠明度抬升（暗色下阴影无效） |
| L3 overlay | `rgba(255,255,255,.72)` + blur 12px | `rgba(38,38,42,.72)` + blur 12px | 必须能看见下方内容（这是玻璃存在的意义） |

**关键修正（亮色）**：当前 `--glass-border` 亮色是 `rgba(255,255,255,0.6)` —— **白底上的白色描边 = 隐形描边**。这是亮色「糊、没有分隔」的直接技术原因（暗色 `rgba(255,255,255,.14)` 可见，所以暗色反而更有结构）。L1 必须换用中性 `--surface-border`。

**关键修正（暗色）**：当前卡片底是 `rgba(38,38,42,.72)` 叠在 `#000` 上 ≈ `#1B1B1E`，**比 token 声明的 `#26262A` 更暗**，再叠品牌光晕后偏紫。L1 改实色后暗色层级会一次性变清楚。

### 3.4 表面层级示意（ASCII）

```
亮色现状（糊）                       亮色目标（有阶梯）
┌─────────────────────────────┐      ┌─────────────────────────────┐
│ L0 粉调渐变底                │      │ L0 中性 #F2F2F5（无粉）      │
│ ┌─────────────────────────┐ │      │ ┌─────────────────────────┐ │
│ │ L1 玻璃面板（白60%描边=隐形）│ │      │ │ L1 实色 #FFF + 中性描边  │ │
│ │ ┌───────┐ ┌───────┐     │ │      │ │ ┌───────┐ ┌───────┐     │ │
│ │ │玻璃卡 │ │玻璃卡 │     │ │      │ │ │实色卡 │ │实色卡 │     │ │
│ │ │+粉顶条│ │+粉顶条│     │ │      │ │ │中性边 │ │中性边 │     │ │
│ │ └───────┘ └───────┘     │ │      │ │ └───────┘ └───────┘     │ │
│ └─────────────────────────┘ │      │ └─────────────────────────┘ │
└─────────────────────────────┘      └─────────────────────────────┘
  四层同材质 → 只剩粉色深浅可辨        四层各有材质 → 粉色可以退场
```

---

## 四、品牌色配比规则

### 4.1 三种角色（只允许这三种）

| 角色 | 允许位置 | token | 面积约束 |
| --- | --- | --- | --- |
| **A1 · 实色强调** | 主 CTA 按钮（`--gradient-btn`）、激活态图标/文字、进度条填充、图表首序列、焦点环、选中态内描边 | `--brand` / `--brand-text` / `--focus-ring` / `--gradient-btn` | 单屏饱和粉面积 ≤ **2%** |
| **A2 · 淡底强调** | 选中行底、激活 chip 底、激活 tag 底、**单张**主卡图标盒、导航激活条 | `--brand-soft` / `--selected-bg` / `--gradient-nav-active` | 单屏淡粉面积 ≤ **8%** |
| **A3 · 淡描边** | 标签 chip 描边、hover 时的卡片描边（可选）、输入框 focus 外晕 | `--glass-chip-border` / `--focus-ring-soft` | 仅 1px，不计面积 |

### 4.2 明令禁止（这是本轮的核心约束）

| 禁止项 | 现状实证 | 处理 |
| --- | --- | --- |
| ❌ 粉色作为**页面底色** | `MainLayout .app-content`、`Home .content-area`、`JPageShell .j-page-body` **三层嵌套**径向品牌光晕（`--glass-glow-top` 16% 粉） | 收敛为**最多 1 层**，且透明度 ≤ 6%，只保留在 L0 |
| ❌ 粉色作为**内容面板底** | `.j-page-body` / `.collection-column` / `.folder-column` 全用 `--glass-bg-trans` + 粉光晕 | 改 L1 实色 |
| ❌ 粉色作为**卡片底** | `greet-card` 用 `linear-gradient(120deg, var(--brand-soft), …)` | 改 L1 实色 + 3px 品牌竖条（A1，1 处） |
| ❌ 粉色作为**默认边框** | `border-color: var(--glass-chip-border)` 全仓 **34 处**（粉 28%），概览 `.panel`、`JStatCard`、`JFilterBar` 全中 | 降到 **≤8 处**（仅弹层内输入框 + 激活/选中态） |
| ❌ 粉色作为**卡片身份标记** | `CollectionCard` / `NoteCard` 顶部 3px 品牌渐变条（`--gradient-top-bar`），网格页 12 张卡 = 12 条粉 | 改 1px `--surface-border` 或删除；模块身份交给图标 |
| ❌ 粉色作为**标签底** | `JFilterBar .j-filter-chip` 用 `--glass-chip-bg`（粉 12%）底 + 粉字，网格页同屏 ~48 个粉块 | 改**透明底 + 粉描边 + 粉字**，仅激活项给 `--brand-soft` 底（保留色相语义，面积降一个数量级） |
| ❌ 粉色作为**所有图标盒底** | `--pink-cherry` 用于 favicon 盒 / note 图标盒，与模块无关 | 改**模块色 soft 底 + 模块色压深图标**（见 4.3） |

### 4.3 模块色的启用（解决「图表是第二套色系」）

**现状矛盾**：`tokens.ts` 定义了 4 个模块色（`--module-bookmark` `#7C5CFF` 紫 / `--module-note` `#2F9DF7` 蓝 / `--module-file` `#0FBF8C` 青 / `--module-vault` `#F0A13A` 橙），但**全仓只被消费 4 次，全部在概览趋势图**。而卡片图标盒一律用品牌粉。结果：图标是粉的、图表是紫青黄绿的，同屏两套色系。

**方案**：把模块色从「只在图表里出现」提升为「模块身份的唯二载体」——
- 允许出现：**图标盒的图标描边**（20px 内）、**图表数据标记**（8px 圆点 / 9px 柱）。
- 禁止出现：背景、边框、文字、按钮、大面积填充。
- 面积上限：单屏模块色总面积 ≤ **3%**（全部是 20px 图标 + 8px 圆点）。

**顺带解决图表配色**：趋势图的 4 个序列正好对应 4 个模块，序列色直接沿用模块色，于是「图表色 = 图标色 = 模块身份」，不再是第二套语言。且把**收藏（第一序列）改为品牌粉**（`--module-bookmark` 由 `#7C5CFF` 改为品牌粉系），让粉色正式进入图表，成为「最重要的那条线」。

> 亮色下 `#2F9DF7` / `#0FBF8C` / `#F0A13A` 作为**图标描边**在浅底上对比度不足 3:1（实测约 2.1–2.6:1），必须新增亮色压深档（见 5. P0-3 的技术风险）。暗色沿用现值。

---

## 五、用户故事

| # | 场景 | 故事 |
| --- | --- | --- |
| US-1 | 桌面亮色 · 概览 | 作为每天开工先看概览的用户，我希望**一眼分清「欢迎区 / 数字总览 / 面板详情」三档**，以便 3 秒内知道今天该处理什么（回收站有几条、密码有没有重复），而不是被一整片粉色糊住视线。 |
| US-2 | 桌面亮色 · 收藏夹网格 | 作为在收藏夹里扫读的用户，我希望**卡片的标题是这一屏最重的东西**，标签和域名退到次要层级，以便快速定位目标条目，而不是先被 48 个粉色标签块淹没。 |
| US-3 | 桌面暗色 · 概览 | 作为夜间使用的用户，我希望**暗色下卡片之间能看出边界、数字比进度条更醒目**，以便在低亮度环境下不费眼地读数（当前暗色下最亮的是粉色进度条，不是数字）。 |
| US-4 | 桌面暗色 · 任意列表 | 作为夜间用户，我希望**暗色下的层级和亮色一致**（同一个信息在两种主题下处于同一层），以便切换主题时不需要重新建立空间认知。 |
| US-5 | 移动端 390px · 收藏夹 | 作为手机上快速找收藏的用户，我希望**标签筛选不占据半屏高度**、卡片不被粉色描边和顶条挤占空间，以便一屏能看见至少 2 张完整卡片。 |
| US-6 | 任意端 · 弹层 | 作为操作用户，我希望**弹窗/抽屉仍然有毛玻璃的「浮起来」质感**（这是我喜欢的部分），以便清楚知道自己在操作浮层而不是页面内容。 |

---

## 六、需求池

> 优先级：**P0 = 决定成败**（不做则本轮无效）／**P1 = 应做**（不做则效果打折）／**P2 = 可选**。
> 预估影响面按「涉及页面 / 组件」列出，供架构师拆任务。

### P0-1 · 建立四层表面体系，把 L0/L1/L2 全部改实色

| 项 | 内容 |
| --- | --- |
| **问题** | 四层（page / surface / card / overlay）用同一套「半透明白 + blur + 白描边」，层级只能靠粉色深浅区分。亮色下 `--glass-border` 是白 60%，在浅底上等于**没有描边**，这是「糊」的直接原因。`main.css` 已因性能被迫给三个大面板关掉 blur 改渐变模拟，说明玻璃已超载。 |
| **期望效果** | L0 = 实色 page；L1 = 实色 surface + **中性** 1px 描边 + `--shadow-1`；L2 = 实色 + `--shadow-card-hover`；L3 = 玻璃（仅弹层/顶栏/TabBar/批量条/tooltip）。玻璃层从「铺满」回到「浮起」。 |
| **验收方式** | ① 脚本断言：所有 L1 选择器的 `background-color` alpha = 1；② 概览页 `backdrop-filter` 生效元素数 ≤ 3；③ 收藏夹页 ≤ 2；④ 亮色下 page↔surface 的描边对比度 ≥ 1.2:1（中性 border 达标，白色 border 不达标）；⑤ 暗色 surface 计算值 = `#26262A`（而非叠出来的 `#1B1B1E`）。 |
| **影响面** | `themes/tokens.ts`（新增 `--surface-border` / `--surface-raised` 等）、`assets/main.css`（`.glass-card` 体系分流、删除 `.side-nav/.folder-column/.collection-column` 的「模拟玻璃」补丁）、`shared/layout/JPageShell.vue`、`shared/layout/SideNav.vue`、`shared/layout/MainLayout.vue`、`shared/views/DashboardView.vue`、`shared/components/ui/JStatCard.vue`、`modules/bookmark/views/Home.vue`、`modules/bookmark/components/ModuleToolbar.vue` / `DiskView.vue` / `ViewSwitcher.vue`、`shared/views/RecycleView.vue`、`shared/components/ui/JSkeletonGrid.vue` / `JSkeletonList.vue` / `JEmptyState.vue` / `JInlineEmpty.vue`。共约 **20 个文件**（全仓 `--glass-bg-trans` 消费 50 处 / 25 个文件）。 |

### P0-2 · 品牌粉配比收敛：把「环境粉」清出去

| 项 | 内容 |
| --- | --- |
| **问题** | 粉色从强调色膨胀成环境色：① 页面底色有**三层嵌套**品牌光晕（`app-content` → `content-area` → `j-page-body`）；② 面板/卡片默认边框是粉（34 处）；③ 每张卡有 3px 粉顶条；④ 每个图标盒是粉；⑤ 每个标签 chip 是粉底粉字（网格页同屏 ~48 个）；⑥ 侧栏底部还有 `--pink-cherry` 径向光（55% 不透明）。 |
| **期望效果** | 粉色只剩 A1/A2/A3 三种角色。具体：页面光晕**三层 → 一层且 ≤6%**；粉边框 **34 → ≤8 处**；卡片顶条 **3px 粉渐变 → 1px 中性边（或删除）**；标签 chip **粉底 → 透明底 + 粉描边 + 粉字，仅激活给粉底**；侧栏粉色底光删除。 |
| **验收方式** | ① DOM 脚本：单屏「粉色承载元素」数量 概览 ≤10、收藏夹网格 ≤12、云盘 ≤8；② 截图色相采样：粉色可见面积 ≤10%、饱和粉 ≤2%；③ `grep` 计数：`border` 中出现 `var(--glass-chip-border)` ≤8 处；④ 走查：`--gradient-top-bar` 消费处 ≤1（或 0）。 |
| **影响面** | `assets/main.css`（`.glass-card`、`.chip`、`.card-top-bar`、`.sidebar-glow`）、`shared/layout/MainLayout.vue`、`modules/bookmark/views/Home.vue`（`.content-area` 光晕）、`shared/layout/JPageShell.vue`、`shared/components/ui/JFilterBar.vue`、`modules/bookmark/components/CollectionCard.vue` / `NoteCard.vue`、`shared/views/DashboardView.vue`（`.greet-card` / `.panel` / `.disk-bar`）、`shared/layout/SideNav.vue`。 |

### P0-3 · 图标盒与图表统一到模块色，品牌粉进入图表首序列

| 项 | 内容 |
| --- | --- |
| **问题** | ① 所有模块的图标盒都是 `--pink-cherry`，模块身份丢失（`NoteCard.vue` 注释已承认「模块识别交给图标」，但图标本身也统一是粉色系）；② 趋势图用紫/蓝/青/橙，与品牌粉无关系，同屏出现「第二套色系」；③ 4 个模块色 token 全仓仅被消费 4 次，属「定义了但没启用」。 |
| **期望效果** | 图标盒 = **模块色 soft 底 + 模块色压深图标**；趋势图 4 序列 = 4 个模块色，其中**收藏序列改为品牌粉**（`--module-bookmark` → 品牌粉系）。于是「图标色 = 图表色 = 模块身份」，粉色成为图表里最重要的那条。模块色仅允许出现在 20px 图标与 8px 数据标记上。 |
| **验收方式** | ① 走查：任一屏模块色出现位置仅限图标与图表标记，无背景/边框/文字；② 模块色单屏面积 ≤3%；③ 亮色下模块色图标对 L1 表面底对比度 ≥3:1（WCAG SC 1.4.11，图形类）；④ 趋势图 4 序列色值全部来自 `--module-*`，无独立色值。 |
| **影响面** | `themes/tokens.ts`（新增 `--module-*-soft` ×4、`--module-*-icon` ×4 亮色压深档；改 `--module-bookmark`）、`shared/components/ui/JStatCard.vue`、`modules/bookmark/components/CollectionCard.vue`（`.favicon-box`）/ `NoteCard.vue`（`.icon-box`）、`shared/views/DashboardView.vue`（`TREND_SERIES` 色值）。**⚠️ 技术风险见 6.1。** |

### P0-4 · 概览页视觉重量再平衡

| 项 | 内容 |
| --- | --- |
| **问题** | ① 欢迎横幅用了 `glass-card--modal`（**弹窗级**玻璃）却只是常驻横幅，占 96px 垂直空间但信息量只有「问候 + 日期 + 一句引语」；② 6 张统计卡同权重、数字 24px/weight 800（DM Sans 上 800 过重，与 24px 组合发糊）、标签 12px 极小，比例失衡；③ 同屏字号档位过多（11/12/13/18/24/32 共 6 档，不含 emoji）；④ 暗色下**粉色进度条比数字更亮**，视觉焦点错位。 |
| **期望效果** | ① 欢迎横幅降级为 L1 表面（去 `glass-card--modal`），高度 ≤76px，emoji 32→24px，粉色收敛为左侧 3px 竖条；② 统计卡数字 weight 800→700、标签 12→13px 且 `--text-3` 提到 `--text-2`、卡片内距与图标盒 38→32px 收紧（顺带缓解 6 位数字溢出）；③ 同屏文本字号档位 ≤4；④ 进度条从 `--gradient-btn`（饱和渐变）改为 `--brand` 单色或模块色，让数字保持最亮。 |
| **验收方式** | ① 概览页 1440×900 同屏文本字号档位 ≤4（脚本去重计算样式）；② 欢迎横幅渲染高度 ≤76px；③ 统计卡数字 `font-weight` = 700，标签 `font-size` ≥13px；④ 暗色截图走查：数字与进度条的亮度对比不再倒置（数字为最亮层）；⑤ 6 位数字 `scrollWidth ≤ clientWidth`（复用 `JStatCard` 既有降档逻辑，不得回归）。 |
| **影响面** | `shared/views/DashboardView.vue`、`shared/components/ui/JStatCard.vue`。**注意**：统计数字基准 24px 是 `decisions-ia.md` 决策 6 的结论（选 A 维持 24px），本轮**不改字号档位**，只改字重/标签/内距，不得推翻该决策。 |

### P1-1 · 侧栏与目录栏改实色 surface

| 项 | 内容 |
| --- | --- |
| **问题** | `SideNav` 是常驻 layout sider，内容不从其下经过，却用 `--glass-bg-trans` + 品牌光晕 + `sidebar-glow`（`--pink-cherry` 径向 55%）→ 侧栏整体发粉，与页面底难以区分。`.folder-column` 同样。 |
| **期望效果** | 侧栏 = L1 实色 + 右侧 1px 中性描边；激活项保留 `--gradient-nav-active` + `--brand-text`（A1/A2 角色，1 处）；删除 `sidebar-glow` 粉色底光。 |
| **验收方式** | ① 侧栏计算 `background-color` alpha = 1；② 侧栏内粉色元素 ≤2（激活项底 + 激活图标）；③ 侧栏与内容区在亮/暗两套下均能靠 1px 描边区分（对比度 ≥1.2:1）。 |
| **影响面** | `shared/layout/SideNav.vue`、`shared/layout/NavItem.vue`、`assets/main.css`（`.sidebar-glow`）、`modules/bookmark/views/Home.vue`（`.folder-column`）。 |

### P1-2 · hover / 选中语言统一到 raised 层

| 项 | 内容 |
| --- | --- |
| **问题** | 卡片 hover 当前用 `border-color: var(--brand-text)`（粉边）+ `translateY(-2px)` + `--shadow-card-hover`；列表行 hover 用 `--hover-bg` / `--glass-chip-bg`。两种 hover 语言，且卡片的 hover 是「变粉」——粉色被当作状态色用了 12 次/屏。 |
| **期望效果** | hover = **抬升到 L2**：中性边不变或略深 + `--shadow-card-hover` + `translateY(-1px)`；粉色只在**选中态**（`.is-selected`）出现。`translateY` 从 -2px 收到 -1px（减少网格抖动）。 |
| **验收方式** | ① 走查：hover 态不新增粉色承载元素；② `CollectionCard`/`NoteCard`/`CollectionRow`/`NoteRow` 四者的 hover 视觉参数一致（阴影 + 位移 + 边框三项逐项相同）；③ `translateY` 位移 ≤1px。 |
| **影响面** | `modules/bookmark/components/CollectionCard.vue`、`NoteCard.vue`、`CollectionRow.vue`、`NoteRow.vue`、`assets/main.css`（`.is-selected` 保留粉内描边不动）。 |

### P1-3 · 移动端 390px 密度回正

| 项 | 内容 |
| --- | --- |
| **问题** | 实拍 `b12-10-mobile-grid-390.png`：标签筛选 chip 占 **5 行约 200px**，一屏（812px 高）里内容区只剩不到 400px，只能看见 1.5 张卡；卡片顶部粉条 + 粉描边 + 粉阴影在窄屏上占比更高，观感更「糊」。 |
| **期望效果** | ① 标签筛选默认折叠为 **2 行 + 「更多 N」**（展开后完整显示）；② 卡片去顶条、去粉描边；③ TabBar 玻璃**保留**（内容从其下经过，符合 3.2 规则）；④ 一屏可见 ≥2 张完整卡片。 |
| **验收方式** | 390×812 截图：① 标签区高度 ≤100px（折叠态）；② 首屏完整卡片数 ≥2；③ TabBar 仍为玻璃（`backdrop-filter` 生效）。 |
| **影响面** | `shared/components/ui/JFilterBar.vue`、`MobileTabBar.vue`（仅确认不回归）、`CollectionCard.vue` / `NoteCard.vue` 的移动端媒体查询。**注意**：折叠交互属轻微功能变更，需架构师确认是否纳入本轮（见 Q5）。 |

### P2-1 · 空态 / 骨架屏材质对齐新层级

| 项 | 内容 |
| --- | --- |
| **问题** | `JEmptyState` 用 `--glass-glow-top` 径向粉光 + 玻璃；`JSkeletonGrid/List` 用 `--glass-bg-trans`。新层级落地后会与新体系不一致。 |
| **期望效果** | 空态 = L1 实色 + 中性描边；骨架 = L1 实色 + `--skeleton-base`，去掉玻璃与粉光。 |
| **验收方式** | 走查：空态/骨架不含 `backdrop-filter`；骨架块与 L1 表面同底。 |
| **影响面** | `shared/components/ui/JEmptyState.vue`、`JSkeletonGrid.vue`、`JSkeletonList.vue`、`JInlineEmpty.vue`、`JErrorState.vue`。 |

### P2-2 · 暗色模块色饱和度微调

| 项 | 内容 |
| --- | --- |
| **问题** | 暗色下 4 个模块色（`#8B7CFF` / `#5AA9F7` / `#2FCFA3` / `#F0A13A`）饱和度高于品牌粉，在纯黑底上偏刺眼，模块色启用后会成为新的视觉噪声。 |
| **期望效果** | 暗色模块色饱和度下调约 10%，明度与 `--brand` `#FF8FAB` 拉齐到同一视觉重量。 |
| **验收方式** | 4 个模块色在暗色 L1 表面上的对比度落在 **[4.5, 6.5]** 区间（既有辨识度又不刺眼）。 |
| **影响面** | `themes/tokens.ts`（darkTokens 的 4 个 module 值）。 |

---

## 六·附. 技术风险（架构师需重点评估）

| # | 风险 | 说明 | 建议 |
| --- | --- | --- | --- |
| R1 | **模块色作为图标描边的对比度不达标** | 亮色 `--module-note` `#2F9DF7` / `--module-file` `#0FBF8C` / `--module-vault` `#F0A13A` 在 `#FFFFFF` / `#F2F2F5` 上约 2.1–2.6:1，低于 WCAG SC 1.4.11 的 3:1（图标属「图形对象」）。P0-3 若直接启用会**新增无障碍缺口**，而上一轮刚把对比度做到 100% 达标。 | 必须新增亮色压深档 `--module-*-icon`（建议在原色基础上压到 L\* ≈ 45–50），并跑一次对比度复核脚本（复用 `PLAN-2026-19` 的 `/tmp/a11y3.mjs` 思路）。暗色沿用现值（实测 5.3–9.3:1）。 |
| R2 | **改 `--bg-page` 的连锁影响** | `light.ts` 的 `common.bodyColor`、`Layout.color`、naive 的 `cardColor` 都从 token 派生。压深 `--bg-page` 会同时改变弹层、抽屉、输入框的底（因为 naive 部分组件用 `bodyColor` 做「凹陷底」）。 | 只改 `--bg-page` 一个值，改完必须逐屏走查弹层内输入框、`n-base-selection` 下拉、`n-empty`。若连锁过广，退回「不改 page，只靠中性描边 + 阴影建立阶梯」（G6 仍可达成）。 |
| R3 | **删除 `.side-nav/.folder-column/.collection-column` 的「渐变模拟玻璃」补丁** | `main.css:498-511` 有一段 `backdrop-filter: none !important` + 手写 radial-gradient 的补丁，注释明确写着「大面积 backdrop-filter 在 Windows 合成器开销高」。改实色后这段可删，但要确认**删掉后不会出现「页面底透出来」的意外**（因为实色已不透明，理论上更安全）。 | 建议保留这段补丁的注释作为历史记录，把规则本身删除；或改为只对 `.side-nav` 生效的兜底。 |
| R4 | **概览页 14 层 blur 的移除可能暴露既有层级 bug** | 现在很多「分隔感」是 blur 边缘和阴影叠出来的假象。改实色后，原本被模糊掩盖的 1px 缝隙、滚动容器裁切、`contain: paint` 边界可能显形。 | 建议 P0-1 先在概览页单独落地并截图对比，确认无破版后再铺到收藏/云盘/密码库。 |
| R5 | **`--module-bookmark` 改色是全局语义变更** | 虽然当前只被消费 1 处（图表），但 token 名承载「模块色」语义，未来若用于别处会继承「粉色=收藏」的语义。 | 建议**不直接改** `--module-bookmark`，而是新增 `--chart-series-1` 语义 token 指向品牌粉，把「模块色」与「图表序列色」解耦。更保守。 |
| R6 | **`greet-card` 移除 `glass-card--modal` 类** | 该类同时提供 `background` / `backdrop-filter` / `border` / `box-shadow` 四件事，`DashboardView.vue` 的 scoped `.greet-card` 已覆盖其中三项但**没覆盖 `backdrop-filter`**，所以 blur 实际在生效。删类时要确认 scoped 样式能独立成立。 | 删类 + 补全 scoped 的 `border` / `box-shadow`，避免「删了类但样式没接住」。 |

---

## 七、UI 设计说明（文字 + 层级示意）

> 不需要出图。以下描述改造后的形态，供实现时对照。

### 7.1 概览页（病症最集中）

```
┌─ L0 page：中性 #F2F2F5，无粉光晕（或仅 1 层 ≤6%） ────────────────┐
│ ┌─ L1 内容面板：实色 #FFF + 1px 中性边 + shadow-1 ─────────────┐ │
│ │                                                              │ │
│ │ ▌ 欢迎横幅（L1，76px 高，无玻璃）                             │ │
│ │ ▌ 左 3px 品牌竖条 │ 👋 24px │ 上午好，欢迎回来 18px/700        │ │
│ │ ▌                 │         10月19日 星期一 12px             │ │
│ │ ▌                                     「日拱一卒…」12px 斜体  │ │
│ │                                                              │ │
│ │ 数据总览（12px/700 overline，--text-3）                       │ │
│ │ ┌────┐┌────┐┌────┐┌────┐┌────┐┌────┐  ← 6×L1 卡，88px 高     │ │
│ │ │▣ 12││▣  6││▣  5││▣  5││▣  8││▣  8│    ▣=32px 模块色图标盒 │ │
│ │ │收藏││便签││云盘││密码││标签││回收│    数字 24px/700        │ │
│ │ └────┘└────┘└────┘└────┘└────┘└────┘    标签 13px/500 --text-2│ │
│ │                                                              │ │
│ │ ┌─ L1 面板：数据趋势 ────────────────────────────────────┐   │ │
│ │ │ 柱色 = 模块色，收藏序列 = 品牌粉（第一序列）             │   │ │
│ │ │ 网格线 1px dashed --surface-border                     │   │ │
│ │ │ 悬浮 tooltip = L3 玻璃（唯一允许的模糊）                 │   │ │
│ │ └────────────────────────────────────────────────────────┘   │ │
│ │                                                              │ │
│ │ ┌─ L1 云盘占用 ────┐ ┌─ L1 密码库健康 ──────────────┐        │ │
│ │ │ 进度条 = --brand │ │ 条目数 / 重复密码             │        │ │
│ │ │ 单色，非渐变     │ │ 警告态 = danger-soft 底       │        │ │
│ │ └──────────────────┘ └───────────────────────────────┘        │ │
│ └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
   粉色只剩：左竖条 + 6 个图标之一（收藏）+ 进度条 + 图表首序列 ≈ 9 处
```

### 7.2 收藏夹 / 便签网格

- **卡片**：L1 实色 + 1px 中性描边 + `--shadow-1`；**删除顶部 3px 粉渐变条**；图标盒 40px 改「模块色 soft 底 + 模块色压深图标」；标题 14px/600 `--text-1`（保持这一屏最重）；标签 chip **透明底 + 粉描边 + 粉字**（不再粉底）；域名 `--text-3`（从 `--brand-text` 降级，粉色只留给可点击的主标题）。
- **标签筛选条**：默认中性描边，激活项才给 `--brand-soft` 底 + `--brand-text` 字 + 600 字重。同屏粉块从 ~48 降到 1。
- **hover**：中性边略深 + `--shadow-card-hover` + `translateY(-1px)`；**选中态**才出现 `--brand` 内描边 + `--selected-bg`（沿用 `main.css` 既有 `.is-selected`，不改）。
- **目录栏**：L1 实色，去粉光晕。

### 7.3 云盘

- 目录栏 / 文件列表容器 = L1 实色，去玻璃与粉光晕。
- 文件行 = L1 内嵌 L2 实色块（`--hover-bg` 底）+ 中性描边；文件类型图标用 `--file-type-*`（已是既有 token，属「模块色」家族，允许）。
- 「上传文件」主 CTA 保留 `--gradient-btn`（A1，全屏唯一饱和粉块）。

### 7.4 密码库

- 密码条目行 = L1/L2 实色；**强度指示**用 `--state-success/warning/error`（既有语义色，不属品牌粉，保持）。
- 密码揭示气泡 = L3 玻璃（符合 3.2 规则，**保留**）。
- 空态与警告态走 `--danger-soft` / `--state-warning-soft`，不引入粉色。

### 7.5 侧栏导航

```
┌──────────────────┐
│ ♡ JNClub         │  ← L1 实色 #FFF，右侧 1px 中性描边
│──────────────────│     无粉光晕、无 sidebar-glow
│ ▣ 概览           │
│ ▣ 收藏夹  ◀━━━━━━┿━ 激活项：--gradient-nav-active 底
│ ▣ 便签           │         + --brand-text 图标（A2，1 处）
│ ▣ 云盘           │
│ ▣ 密码库         │  其余：--text-3，hover 才 --hover-bg
│ ▣ 音乐           │
│ ▣ 回收站         │
│ ▣ 下载中心       │
│                  │
│ ⚙ 个性化         │  ← 底部，无粉色底光
└──────────────────┘
```

### 7.6 移动端 390px

```
┌────────────────────────┐
│ ☰ 开发          🔍 🔔 ⋮ │ ← L3 玻璃（保留）
├────────────────────────┤
│ [+ 新建收藏] [检测失效] │
│ 开发 设计 阅读          │ ← 目录 chip：中性描边
│ 全部 前端12 设计11      │ ← 标签 chip：粉描边粉字
│ 工具10 阅读9 [更多 8]   │    （折叠 2 行，粉底仅激活项）
├────────────────────────┤
│ ┌────────────────────┐ │ ← L1 实色卡，无顶条无粉边
│ │ ▣ 示例收藏 1        │ │   图标盒 = 模块色 soft
│ │ 示例收藏 1 — Vue…   │ │   标题 14/600 最重
│ │ 🔗 example0.com    │ │
│ └────────────────────┘ │
│ ┌────────────────────┐ │
│ │ ▣ 示例收藏 2        │ │
│ └────────────────────┘ │
├────────────────────────┤
│ 概览 收藏 便签 云盘 …  │ ← L3 玻璃（保留，内容从其下经过）
└────────────────────────┘
   一屏可见 ≥2 张完整卡片（现状 1.5 张）
```

---

## 八、量化验收标准

> 风格对齐 `docs/PLAN-2026-19.md` 第九节。所有指标均需脚本或截图可复核，不接受「看起来更好」。

### 8.1 主表

| 维度 | 指标 | 目标 | 复核方式 |
| --- | --- | --- | --- |
| **配比** | 单屏「粉色承载元素」数量（background/border-color 解析为品牌粉色相的 DOM 元素；1px 文字描边不计） | 概览 ≤**10**、收藏夹网格 ≤**12**、云盘 ≤**8**、密码库 ≤**8** | DOM 脚本：遍历可见元素，读 `getComputedStyle`，按色相判定 |
| **配比** | 粉色可见面积占比（1440×900 截图） | ≤**10%**；其中饱和粉（`--brand` 原值及渐变）≤**2%** | 截图色相采样（HSV 色相 320°–350°，S>15%） |
| **配比** | 模块色可见面积占比 | ≤**3%**（仅 20px 图标 + 8px 标记） | 同上 |
| **配比** | 粉边框：`border` 中出现 `var(--glass-chip-border)` 的处数 | **34 → ≤8** | `grep` 计数 |
| **配比** | 卡片顶部 3px 品牌渐变条（`--gradient-top-bar`）消费处 | **1 → 0**（或改为 1px `--surface-border`） | `grep` 计数 |
| **层级** | L1 surface 的 `background-color` alpha | **100% = 1**（不允许 rgba/玻璃） | 计算样式断言 |
| **层级** | 亮色 page↔surface 描边可见性 | 对比度 ≥**1.2:1**（中性 border 达标） | 计算样式 + 对比度脚本 |
| **层级** | 亮色 page↔surface 明度差 ΔL\* | **∈ [3.5, 6]** | L\* 计算 |
| **层级** | 暗色 page↔surface 明度差 ΔL\* | ≥**16** | L\* 计算 |
| **玻璃** | 同屏 `backdrop-filter` 生效元素数 | 概览 ≤**3**、收藏夹 ≤**2**、云盘 ≤**2**、密码库 ≤**2** | 脚本遍历 `getComputedStyle(el).backdropFilter !== 'none'` |
| **玻璃** | 全仓 `backdrop-filter: blur` 出现次数 | **95 → ≤30** | `grep` 计数 |
| **玻璃** | 玻璃层的合法性 | 100% 符合「有内容从其下方经过」 | 人工走查清单（弹层/顶栏/TabBar/批量条/tooltip = 合法） |
| **排版** | 同屏文本字号档位（不含 emoji/图标/图表轴） | ≤**4** | 计算样式 `font-size` 去重 |
| **排版** | 统计卡数字 `font-weight` | **800 → 700** | 计算样式 |
| **排版** | 统计卡标签 `font-size` | **12 → 13px** | 计算样式 |
| **排版** | 概览欢迎横幅渲染高度 | **96 → ≤76px** | `getBoundingClientRect().height` |
| **无障碍** | 亮色正文类对比度达标率 | **100% ≥4.5:1（不回归）** | 复用 `PLAN-2026-19` 对比度脚本 |
| **无障碍** | 亮色非文本图形（图标/焦点环/边界）达标率 | **100% ≥3:1（不回归）** | 同上；**模块色图标必须纳入本次复核** |
| **无障碍** | 暗色同两项 | **100% 达标（不回归）** | 同上 |
| **token** | token 覆盖率 | **100% 不回归**（当前 146 个变量全部有消费） | 定义集 × 使用集求差 |
| **token** | 新增 token | 全部有消费，无死 token | 同上 |
| **token** | 样式内硬编码 hex | **不新增**（保持 4 处 vendor 豁免） | 去注释后 `grep` 计数 |
| **token** | 硬编码圆角 / 字号 | 保持 **0**（`PLAN-2026-19` 已归零，不得回退） | 同上 |
| **移动端** | 390×812 首屏完整卡片数 | **1.5 → ≥2** | 截图 |
| **移动端** | 标签筛选区高度（折叠态） | **~200 → ≤100px** | `getBoundingClientRect().height` |
| **一致性** | 四类卡/行组件的 hover 参数一致 | 阴影/位移/边框三项逐项相同 | 计算样式对比 |
| **性能** | 概览页首屏合成层数 | 不高于改造前（blur 层减少应带来净收益） | DevTools Layers / Performance 面板 |

### 8.2 复核脚本建议（供架构师/QA 参考）

1. **粉色元素计数**：遍历 `document.querySelectorAll('*')`，取 `getComputedStyle` 的 `backgroundColor` / `borderColor` / `color`，解析为 HSL，色相落在 320°–350° 且 `S>15%`、非 `transparent` 即计入。排除 `font-size ≤ 1px` 的纯文字描边。
2. **玻璃层计数**：`[...document.querySelectorAll('*')].filter(el => { const s = getComputedStyle(el); return s.backdropFilter && s.backdropFilter !== 'none' })`，过滤 `display:none` / 零尺寸。
3. **字号档位**：收集可见文本节点的 `font-size`，去重计数（排除 `svg`、`[class*=emoji]`、图表轴容器）。
4. **L1 实色断言**：对每个已知 L1 选择器读 `backgroundColor`，解析 alpha 必须为 `1`。

### 8.3 截图复核清单（需在亮/暗 × 1440/390 四态各出一组）

| 页面 | 亮 1440 | 暗 1440 | 亮 390 | 暗 390 |
| --- | --- | --- | --- | --- |
| 概览 | ✔ | ✔ | ✔ | ✔ |
| 收藏夹（网格） | ✔ | ✔ | ✔ | ✔ |
| 收藏夹（列表） | ✔ | ✔ | — | — |
| 便签 | ✔ | ✔ | ✔ | ✔ |
| 云盘 | ✔ | ✔ | ✔ | ✔ |
| 密码库 | ✔ | ✔ | ✔ | ✔ |
| 回收站 | ✔ | ✔ | ✔ | ✔ |
| 弹窗 / 抽屉（验证玻璃保留） | ✔ | ✔ | ✔ | ✔ |

---

## 九、边界与不做的事

### 9.1 明确不做

| 不做 | 原因 |
| --- | --- |
| ❌ 不改功能逻辑、不改路由、不改后端接口、不改数据模型 | 本轮纯视觉体系，零功能风险 |
| ❌ 不换品牌色（`--brand` `#EC5B8E` / 暗 `#FF8FAB` 保持不变） | 用户明确要求 |
| ❌ 不换字体（DM Sans + Noto Sans SC 不动）、不改字号阶梯的 14 档定义 | 用户明确要求；且 `PLAN-2026-19` 已把硬编码字号归零 |
| ❌ 不去玻璃拟态 | 用户明确要求；本轮只做「玻璃的准入规则」 |
| ❌ 不去粒子背景、不改光标特效、不改动效时长/曲线体系 | 用户明确要求；且 `PLAN-2026-19` 已收敛 |
| ❌ 不改 token 生成机制（`scripts/gen-theme-css.mjs`）、不改 naive-ui 主题覆盖机制 | 只新增 token，不动管线 |
| ❌ 不改 `music-frontend/`（iframe 内） | 独立前端，主题透传机制不动；视觉体系在其内部不适用 |
| ❌ 不改 `src/modules/devtools`（开发者工具） | 独立视觉语汇（代码高亮色），且 `JsonTree.vue`/`ToolCurl.vue` 的 hex 属语法色，不纳入品牌体系 |
| ❌ 不推翻设计系统、不重写 `main.css` | 用户明确要求「渐进式」 |
| ❌ 不改 `--radius-*` 阶梯、不改间距体系 | `PLAN-2026-19` 已收敛，本轮不动 |

### 9.2 上一轮已闭环、**不要重开**的项

| 已闭环项 | 出处 | 本轮处理 |
| --- | --- | --- |
| token 从 JS 注入改 CSS 声明（修 FOUC）、146 个变量 × 4 选择器块 | `PLAN-2026-19` M1-1 | 只**新增** token，不动机制 |
| 亮色对比度全部 ≥4.5:1、焦点环实色 ≥3:1 | M1-2 / M1-3 | **不得回归**，新增模块色图标必须补测 |
| 选中态统一（`.is-selected` 全局一份）、批量选择视觉 | M1-4 | 保留不动，仅把「粉色只用于选中」的语义强化 |
| 弹层过渡统一（6 类 × enter/leave）、Message/Notification 玻璃化 | M2-1 | **保留**（L3 合法玻璃） |
| 主题切换 View Transition 圆形揭示 | M2-2 | 保留不动 |
| 动效时长 1 种字面量、reduced-motion 全响应 | M2-3 | 保留不动 |
| 骨架屏铺开（首屏骨架 vs 增量刷新遮罩） | M2-4 | 只改材质（P2-1），不改逻辑 |
| 暗色层次提升与主题泄漏点收口 | M2-5 | 本轮在其基础上继续做明度阶梯 |
| 硬编码圆角 / 字号归零 | M2-6 | **不得回退** |
| 音乐前端主题透传（`MutationObserver` + `?theme=`） | M3-1 | 不动 |
| `shared` → `modules` 反向依赖清理（仅剩 3 个路由聚合豁免） | M4-1 | 新增代码不得引入新反向依赖 |
| 死 token / 死 CSS 清理（死 token 0，删 9 个死类） | M4-2 | 新增 token 必须有消费；删掉的类不得复活 |
| 12 批一致性修复（页头统一、chip 几何统一、TabBar 溢出、状态色分档、列表行/卡片 hover 语言） | `.ui-review` | 全部保留；本轮 P1-2 只在「一致」的基础上调「轻重」 |
| IA-1 侧栏 module/route 无区分 → 决策 A 维持现状 | `decisions-ia.md` 决策 1 | **不重开**，不加任何区分标记 |
| IA-2 面包屑首项 → 已删除首项 | 决策 2 | 不重开 |
| IA-4 抽屉标题「命令面板」→ 已改「搜索」 | 决策 3 | 不重开 |
| IA-5 返回按钮 → 已改智能返回 | 决策 4 | 不重开 |
| 网格虚拟滚动 `itemHeight: 220` → 决策 A 维持 | 决策 5 | **不重开**；P1-3 只改 chip 折叠与卡片视觉，不改 `itemHeight`、不给卡片定高 |
| 统计数值 24px 基准 → 决策 A 维持 | 决策 6 | **不重开**；P0-4 只改字重/标签/内距，不改 24px 档位 |

### 9.3 团队既有约定（必须遵守）

- **不要在 SFC 的 `<template>` 根层级写 HTML 注释** —— 会把单根变 Fragment，导致 `App.vue` 的 `<Transition mode="out-in">` 状态机卡死、整页白屏（`decisions-ia.md` 已记录该回归）。注释写进 `<script setup>` 的 JSDoc 或根元素内部。
- 改样式前先确认消费链：`tokens.ts` → `gen-theme-css.mjs` → `tokens.generated.css`（4 个选择器块）。新增 token 后需重新生成。

---

## 十、待确认问题

| # | 问题 | 选项 | 我的建议 |
| --- | --- | --- | --- |
| **Q1** | 亮色 `--bg-page` 是否允许从 `#F5F5F7` 压深到 `#F2F2F5`？ | A. 压深（page↔surface ΔL\* 从 ~3.5 提到 ~5，阶梯更清楚，但全站底色略沉）<br>B. 不动（靠中性描边 + 阴影建立阶梯，G6 仍可达成） | **B**。风险最小，且 G6（描边可见）是当前最大的缺失项，改描边即可解决 80% 的「糊」。压深底色的收益边际，连锁影响（naive `bodyColor`）不值得。 |
| **Q2** | 卡片顶部 3px 品牌渐变条：删除还是改为 1px 淡线？ | A. 删除（最干净，模块身份完全交给图标）<br>B. 改 1px `--surface-border`（保留「有顶条」的结构记忆）<br>C. 改 1px `--brand-soft`（保留一丝粉） | **A**。顶条本质是「用粉色给每张卡盖同一个章」，它不携带信息（12 张卡全是同一条），删除即净收益。若担心卡片失去「有设计」的感觉，用图标盒的模块色承担。 |
| **Q3** | 趋势图首序列（收藏）改为品牌粉，怎么实现最稳？ | A. 直接改 `--module-bookmark` 为品牌粉（1 处消费，改动最小，但污染「模块色」语义）<br>B. 新增 `--chart-series-1..4` 语义 token，把「图表序列色」与「模块色」解耦（多 4 个 token） | **B**。`--module-bookmark` 叫「模块色」，让它变成品牌粉会让「模块色」这个命名失去意义（收藏=粉，其他三个=各自色，规则不统一）。新增 chart token 更干净，且未来换图表配色不动模块身份。 |
| **Q4** | 侧栏去玻璃改实色后，与页面底同色（`#FFF` vs `#F2F2F5`），只靠 1px 描边区分 —— 可接受吗？ | A. 接受（描边 + 明度差已足够，macOS Finder 就是这样）<br>B. 侧栏保留极淡玻璃（`rgba(255,255,255,.9)` + 无 blur），保留一点材料差异 | **A**。侧栏是常驻结构，不该有「浮起」暗示；靠明度差（page `#F2F2F5` vs sider `#FFF`）+ 1px 描边已经足够，而且这正是「层级清晰」的体现。 |
| **Q5** | P1-3 移动端标签筛选「折叠 2 行 + 更多」涉及交互变更（新增展开/收起状态），是否纳入本轮？ | A. 纳入（视觉问题必须靠折叠解决，否则 200px 的 chip 区无法通过纯视觉优化消化）<br>B. 不纳入，仅做视觉（去粉底/收紧内距），chip 区仍占 4 行<br>C. 改为横向滚动单行（无新状态，但可发现性下降） | **C**。横向滚动是纯 CSS（`overflow-x: auto` + `scroll-snap`），零新增状态、零功能风险，且符合移动端 chip 条的通用范式。若用户觉得可发现性不够，再退回 B。 |
| **Q6** | 暗色 `--bg-page` 是否从纯黑 `#000000` 提到 `#0A0A0C`？ | A. 提到 `#0A0A0C`（纯黑下 L1 `#26262A` 的阶梯更「实」，且能减少 OLED 上的拖影）<br>B. 保持纯黑（用户可能偏好 OLED 真黑） | **B**。纯黑是当前明确的选择，且暗色阶梯问题主要来自「L1 用了半透明叠在黑上变成 `#1B1B1E`」，改实色后 `#26262A` 对 `#000` 的 ΔL\* 已达 16.6，够用。不动 page 更保守。 |
| **Q7** | 概览页 6 张统计卡是否考虑改布局（如 4 主 + 2 辅，或 6 列改 5 列）以彻底解决「数字拥挤 + 同权重」？ | A. 不改布局（仅调字重/标签/内距/图标尺寸）<br>B. 改 4 主 + 2 辅（主卡大、辅卡小）<br>C. 6 列改 5 列（单卡变宽，为未来数字增长留余量） | **A**（本轮）。改布局是结构变更，与「视觉体系重塑」不是同一件事，且会牵动 `dash.order` 的用户偏好与移动端断点。若改造后仍觉拥挤，再单独立项做 C。 |
| **Q8** | 模块色启用后，「4 个模块色 + 品牌粉 = 5 个色相」会不会反而更花？ | A. 只允许极小面积（20px 图标 + 8px 标记），色相数量增加但视觉噪声下降（因为粉色大面积退场）<br>B. 只启用 2 个模块色（收藏=粉、其余统一中性灰） | **A**。判定标准是「色相数量」还是「色块面积」—— 当前问题是**面积**（48 个粉块），不是**色相数**。5 个色相各占 20px 远好过 1 个色相占满屏。但需在验收时严守 3% 面积上限，否则会失控。 |

---

## 十一、附：本轮诊断的代码实证

> 所有结论均来自实际读代码，非推测。供架构师核对。

| 结论 | 代码位置 | 实证 |
| --- | --- | --- |
| 页面粉底来自**三层嵌套**品牌光晕 | `MainLayout.vue:100-103`（`.app-content`）、`Home.vue:656-659`（`.content-area`）、`JPageShell.vue:62-66`（`.j-page-body`） | 三处均为 `radial-gradient(... var(--glass-glow-top) ...)` 叠在 `--bg-page` 上，`--glass-glow-top` 亮色 = `rgba(236,91,142,0.16)` |
| 亮色描边**隐形** | `tokens.ts:260` `glassBorder: 'rgba(255, 255, 255, 0.6)'` | 白底上的白色描边；暗色为 `rgba(255,255,255,.14)` 可见 —— 这解释了「暗色反而比亮色有结构」 |
| 面板底与卡片底**同材质** | `JPageShell.vue:65-66`、`Home.vue:677-680`（`.folder-column`）、`DashboardView.vue:653-664`（`.panel`）、`JStatCard.vue:65-70` | 全部 `background: var(--glass-bg-trans)` + `backdrop-filter: blur(var(--glass-blur))` |
| 常驻横幅用了**弹窗级**玻璃 | `DashboardView.vue:347` `<div class="greet-card glass-card--modal">` | `glass-card--modal` 在 `main.css:256-265` 定义，含双 radial 品牌光晕 + `--glass-shadow`（`0 24px 64px` 弹窗级投影） |
| 大面板玻璃已被**迫关闭** | `main.css:498-511` | `.side-nav / .folder-column / .collection-column` 被 `backdrop-filter: none !important` 覆盖，改手写 radial-gradient 模拟；注释：「大面积 backdrop-filter 在 Windows 合成器开销高」 |
| 粉边框 **34 处** | `grep "border[^;]*var(--glass-chip-border)"` | `--glass-chip-border` 亮色 = `rgba(236,91,142,0.28)` |
| 标签 chip **粉底粉字** | `JFilterBar.vue:69-71` | `border: 1px solid var(--glass-chip-border); background: var(--glass-chip-bg); color: var(--glass-chip-text)` —— 三者全粉 |
| 卡片顶条 **3px 粉渐变** | `main.css:148-153`（`.card-top-bar`）、`CollectionCard.vue:115`、`NoteCard.vue:108` | `background: var(--gradient-top-bar)` = `linear-gradient(90deg, #EC5B8E, #FFB3C6)` |
| 图标盒**全模块统一粉** | `CollectionCard.vue`（`.favicon-box`）、`NoteCard.vue:230`（`.icon-box`） | `background: var(--pink-cherry)`（亮 `#FFE4EF`） |
| 模块色**只被消费 4 次** | `grep "var(--module-"` | 4 处，全部在 `DashboardView.vue:96-99` 的 `TREND_SERIES` |
| 侧栏粉色底光 | `main.css:221-232`（`.sidebar-glow::after`） | `radial-gradient(ellipse at bottom, var(--pink-cherry) 0%, transparent 72%)`，`opacity: .55` |
| 概览页 **14 层 blur** | 逐元素统计（概览走 `OverviewLayout` → `JPageHeader`，无 `.home-header`） | `.j-page-body` 1 + `.greet-card` 1 + `JStatCard` ×6 + `.panel` ×6（trend / disk / vault / recent×3）= **14** |
| 全仓 blur 计数 | `grep -ro "backdrop-filter: blur"` | **95** 处；`var(--glass-blur)` **88** 处；`var(--glass-bg-trans)` **50** 处 / **25** 个文件 |
| 玻璃拟态是**上一轮的有意选择** | `main.css:376-387` 注释 | 「全站 205 处 `message.*` 调用，是最高频的反馈面；用实色白卡会和其余弹层割裂」—— 这条对 **L3 依然成立**，本轮不动 |

---

## 十二、下一步

1. **本文档 → 架构师**：产出技术方案（token 增补清单、`.glass-card` 体系分流方案、逐文件改动顺序、R1–R6 风险的处置决定）。
2. **待用户拍板 Q1–Q8**（尤其 Q2 卡片顶条、Q3 图表 token 解耦、Q5 移动端 chip 方案）。
3. **实施顺序建议**（供架构师参考，非强制）：P0-1（表面体系）→ P0-2（粉色配比）→ P0-3（模块色/图表）→ P0-4（概览配重）→ P1 → P2。**P0-1 必须在 P0-2 之前**，因为「粉色退场」需要「实色表面」来接住层级，否则页面会变成一片灰。
