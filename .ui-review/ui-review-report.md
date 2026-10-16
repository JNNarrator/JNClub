# JNClub 主站 UI 评审报告

**评审对象**：jnclub-web（Vue 3 + naive-ui + 自研 CSS 变量设计令牌）
**样本**：`.ui-review/` 下 9 张截图（1440×900 ×8，390×844 ×1），覆盖收藏夹/便签/云盘/密码库/概览/回收站/暗色/搜索抽屉/移动端
**约束**：不更换视觉语言、不重构设计体系，仅在现有风格内收敛与对齐
**方法**：截图目视 + 源码数值核对（令牌 → 全局样式 → 组件 scoped 样式逐层对照）

---

## 一、总体结论

1. **令牌层是健康的**。颜色/字号/圆角/阴影/动效都已集中到 `jnclub-web/src/themes/tokens.generated.css`，亮暗两套结构对称，`--fs-*` 阶梯（10→40px）、`--radius-*`（4/6/10/16/20/28/999）、`--dur-*` 与 `--ease*` 齐备。绝大多数组件确实在用变量，**不存在配色或圆角的系统性跑偏**。
2. **主要问题不在「值」而在「档位选择」**。同一个语义（筛选 chip、列表行、面板内边距、区块标题）在不同模块里被赋予了不同的档位，导致同一页面内并存 2–4 种规格。这是全站视觉一致性的主要来源。
3. **页面壳有两套页头语言并存**：主壳 4 模块（收藏夹/便签/云盘/密码库）走 `AppHeader` 面包屑，概览与回收站走 `JPageShell + JPageHeader` 大标题。这是本次评审中**唯一的结构性视觉断裂**，且用户从侧栏切换时会直接看到跳变。
4. **移动端底部导航在 390px 下必然溢出**（7 项 × 固定 64px = 448px），属确定性缺陷。
5. 另发现一处**死代码**：`DashboardView.vue` 的 `.stat-card` 系列样式（638–699 行）已无模板引用，实际渲染走 `JStatCard.vue`，两者数值字号差一档（24px vs 32px）。
   > **2026-10-16 修正**：此处原写作「概览页统计数值因此比原始设计意图小一档」。第四批已删除该死代码，并实测证明**那套 32px 规格在 1440px 的 6 列布局下不可行**（连 4 位数字都会溢出）—— 死代码是未落地的草稿，不是应被追补的设计意图。现状 24px 予以保留。详见 11.1。

---

## 二、P0 — 必须收敛（影响一致性判断与移动端可用性）

### P0-1【视觉+IA】两套页头语言并存，模块间切换时页头跳变

> **状态：已实施（第二批，2026-10-15）** —— 采用下方方案 A（保留两套、对齐数值）。实施记录与实测数据见 9.2。

**证据**
| 页面 | 页头实现 | 关键数值 |
|---|---|---|
| 收藏夹/便签/云盘/密码库 | `jnclub-web/src/shared/layout/AppHeader.vue:421` `.home-header` | `height:var(--header-height,60px)`；`padding:0 var(--layout-page-gutter,24px)`；`border-bottom:1px solid var(--border)`；标题为面包屑，`.breadcrumb-current` 加 `font-weight:600`，链接 `font-size:var(--fs-md)`(13px) |
| 概览/回收站 | `jnclub-web/src/shared/components/ui/JPageHeader.vue` `.j-page-header` | `padding:4px 2px 14px`；`.j-page-header-title{font-size:var(--fs-3xl)(20px);font-weight:800}`；**无 `border-bottom`** |

同时内容区起始位置也不同：
- `jnclub-web/src/modules/bookmark/views/Home.vue:643` `.content-area{padding:20px var(--layout-page-gutter,24px)}` → 内容距顶栏 20px
- `jnclub-web/src/shared/layout/JPageShell.vue:45` `.j-page-shell{padding:var(--layout-page-padding,12px 24px 0)}`，令牌值 `12px 24px 0` → 内容距顶栏 12px

**后果**：从「收藏夹」点侧栏「概览」，页头从 60px 面包屑条（有分隔线）变成无分隔线的大标题块，内容区上边距从 20px 变 12px，左侧主标题位置由 13px 面包屑变 20px/800 标题。

**建议**（择一，推荐 A）
- **A（保留两套、对齐数值）**：给 `.j-page-header` 补 `border-bottom:1px solid var(--border)` 与固定高度；把 `JPageShell.vue:45` 的 `padding` 由 `var(--layout-page-padding,12px 24px 0)` 改为 `20px var(--layout-page-gutter,24px) 0`，与 `Home.vue:643` 对齐。标题字号由 `var(--fs-3xl)`(20px) 降到 `var(--fs-2xl)`(18px)/`font-weight:700`，使其更接近顶栏语境而非落地页大标题。
- **B（统一为一套）**：概览/回收站也改用 `AppHeader` + 面包屑，`JPageHeader` 仅保留 actions 行。

**类型**：页头语义不统一属**信息架构问题**（用户无法从页头判断自己处于「模块内」还是「独立页面」）；上边距 12px/20px 与分隔线缺失属**视觉问题**。

---

### P0-2【视觉】同屏两种「筛选 chip」语言：白底直角 vs 粉底胶囊

> **状态：已实施（第一批统一几何 + 第二批按 8.2 拆回色相）** —— 最终为「几何统一、色相分工」。

**证据**（`01-bookmarks-1440.png` 中这两组 chip 上下相邻，仅隔 `margin-bottom`）

| 组件 | 选择器 | 规格 |
|---|---|---|
| 目录 chips | `jnclub-web/src/assets/main.css:156` `.chip` | `padding:6px 14px`；`border-radius:var(--radius-sm)`(**10px**)；`border:1px solid var(--border)`(**#D1D1D6 中性灰**)；`background:var(--bg-card)`(**纯白**)；`color:var(--text-2)`；`font-size:var(--fs-md)`(**13px**) |
| 标签 chips | `jnclub-web/src/shared/components/ui/JFilterBar.vue:63` `.j-filter-chip` | `padding:5px 14px`；`border-radius:var(--radius-pill)`(**999px**)；`border:1px solid var(--glass-chip-border)`(**粉**)；`background:var(--glass-chip-bg)`(**粉**)；`color:var(--glass-chip-text)`(**#B82A63**)；`font-size:var(--fs-sm)`(**12px**) |

两组在 `Home.vue` 中直接堆叠：`.chip-bar{margin-bottom:18px}`（698 行）→ `.tag-bar{margin-bottom:14px}`（707 行）。

**建议**：把 `.chip`（main.css:156–182）整体收敛到 `.j-filter-chip` 的档位，五处属性改为：`font-size:var(--fs-md)`→`var(--fs-sm)`、`border-radius:var(--radius-sm)`→`var(--radius-pill)`、`background:var(--bg-card)`→`var(--glass-chip-bg)`、`border-color:var(--border)`→`var(--glass-chip-border)`、`color:var(--text-2)`→`var(--glass-chip-text)`；`padding` 由 `6px 14px` 收到 `5px 14px` 与 JFilterBar 完全一致。`.chip-active`（176 行）已是 `--brand-soft/--brand-text`，与 `.j-filter-chip-active`（80 行）一致，无需改。

**收益**：一次改动同时修好收藏夹、便签两个模块的「目录/标签」两行视觉断层，并让 `03`/`04` 之外的模块筛选语义归位。

**类型**：视觉问题。

---

### P0-3【视觉】移动端底部导航 390px 下必然溢出，「更多」入口被裁

> **状态（2026-10-16）**：**已实施**，见第十二章 12.1。实施时对原建议做了一处修正：`justify-content` 未改为 `stretch`（`flex:1` 已铺满，`stretch` 在 flex 容器中等同 `flex-start`，改了反而误导），保留 `flex-start` 并加注释；另附加了 `.tab-label` 的单行截断兜底。

**证据**：`jnclub-web/src/shared/layout/navConfig.ts` 中 `MOBILE_KEYS = ['overview','bookmarks','notes','files','vault','recycle']`（6 项），`MobileTabBar.vue` 模板再追加固定「更多」项 = **7 项**；而 `MobileTabBar.vue:170-171` `.tab-item{flex:0 0 64px}` 为固定宽。7 × 64px = **448px** > 390px 视口。

**建议**（`jnclub-web/src/shared/layout/MobileTabBar.vue:170-171`）
```css
.tab-item { flex: 1 1 0; min-width: 0; }   /* 取代 flex: 0 0 64px */
```
并把 `nav.mobile-tabbar` 的 `justify-content:flex-start` 改为 `stretch`，`overflow-x:auto` 可保留作为超窄屏（<320px）兜底。若希望保持 64px 触控宽度，则需把 `MOBILE_KEYS` 裁到 5 项（5×64+64=384px ≤ 390px），其余进「更多」抽屉。

**类型**：信息架构问题（导航项数超出容器容量），表现为视觉溢出。

---

### P0-4【视觉·无障碍】状态色令牌被同时当作「填充色」和「文本色」，文本侧不达 WCAG AA

> **状态：已实施（第二批，2026-10-15）** —— 令牌拆成「填充档 / 文本档」两层，19 处误用引用归位。见 9.1。

**证据**：`jnclub-web/src/themes/tokens.ts:223-232`（亮）与 `:375-384`（暗）中，`stateSuccess` / `stateWarning` / `stateError` / `dangerText` 在**亮暗两套里取值完全相同**，且都是中浅色调：

| 令牌 | 值 | 亮色白底对比度 | 暗色 #26262A 底对比度 | AA 要求 |
|---|---|---|---|---|
| `--danger-text` | `#FF8A97` | **2.25:1** ✗ | 6.70:1 ✓ | 4.5:1 |
| `--state-success` | `#7AC686` | **2.05:1** ✗ | 7.36:1 ✓ | 4.5:1 |
| `--state-error` | `#E87878` | **2.84:1** ✗ | 5.30:1 ✓ | 4.5:1 |
| `--state-warning` | `#F3C470` | **1.62:1** ✗ | 9.29:1 ✓ | 4.5:1 |
| （对照）`--brand-text` / `--link` | `#C9256A` | 5.31:1 ✓ | — | 4.5:1 |
| （对照）`--glass-chip-text` | `#B82A63` | 5.92:1 ✓ | — | 4.5:1 |

（对比度按 WCAG 2.x 相对亮度公式逐项验算，非估算。）

**问题**：这些令牌在**亮色下被直接用作文本颜色**，共 24 处（其中 `DashboardView.vue:672` 落在 P1-8 认定的死代码区间内，实际生效 23 处）：
- `--danger-text`（14 处）：`JErrorState.vue:58`、`JStatCard.vue:78`、`DashboardView.vue:672`(死代码)/`773`/`776`、`DeadLinkModal.vue:197`/`219`/`238`、`NoteVersionsModal.vue:465`/`504`、`RecycleView.vue:348`/`374`/`400`/`491`
- `--state-success`（10 处）：`DashboardView.vue:780`、`DeadLinkModal.vue:196`、`NoteEditor.vue:1299`/`1517`/`1563`、`VaultView.vue:729`/`733`、`NoteVersionsModal.vue:464`/`496`/`499`

对比 `--brand-text` 与 `--glass-chip-text` 都做了亮暗分档（亮色深、暗色浅）并且达标 —— 说明**这套「分档」机制已经存在且正确，只是状态色漏做了**。

**建议**（在现有机制内补齐，不新增体系）
1. 在 `jnclub-web/src/themes/tokens.ts` 的亮色对象（`:223-232`）中，把四个状态色拆成「填充」与「文本」两档：
   - `stateSuccess`/`stateWarning`/`stateError` 保持现值（供 `.strength-fill`、`.diff-add` 背景、`.stat-warn-dot` 等**填充/图形**用途）
   - 新增 `stateSuccessText: '#1E7A46'`（白底 **5.35:1**）、`stateErrorText: '#C0334A'`（白底 **5.51:1**）、`stateWarningText: '#8A5A00'`（白底 **5.93:1**）
   - `dangerText` 亮色由 `#FF8A97` 改为 `#C0334A`（白底 **5.51:1**）
   - **暗色对象（`:375-384`）保持全部现值不变** —— 现值在 `#26262A` 上分别为 6.70 / 7.36 / 5.30 / 9.29:1，全部达标
2. 在 `tokens.generated.css` 中导出对应 CSS 变量，并把上列文本用途改用新变量（填充用途不动）。
3. 若不想新增变量，最小改动是把亮色 `--danger-text` 直接改为 `#C0334A`，可立刻修复 14 处中的 13 处生效点（其余 `--state-success` 等仍需处理）。

**类型**：视觉问题（无障碍）。这是本次评审中唯一可用对比度公式确证的硬缺陷，且修复方式完全落在既有「亮暗分档」机制内。

---

## 三、P1 — 应修（观感与层级）

### P1-1【视觉】列表行 hover 复用了侧栏激活态的 inset 描边语言

> **状态（2026-10-16）：已实施。** 详见 [十、第三批实施与验收](#十第三批实施与验收2026-10-16-追加)。

**证据**
- `jnclub-web/src/modules/bookmark/components/CollectionRow.vue:174-175` `.collection-row:hover{background:var(--glass-chip-bg); box-shadow:inset 3px 0 0 var(--brand)}`
- `jnclub-web/src/modules/bookmark/components/NoteRow.vue:204-205` `.note-row:hover{...同上}`
- 而 `jnclub-web/src/shared/layout/NavItem.vue` 激活态也用 `box-shadow:inset 3px 0 0 var(--brand)`

同一 `inset 3px 0 0 var(--brand)` 被「侧栏激活（持久状态）」与「列表行 hover（瞬时状态）」共用。同时卡片视图 hover 只改阴影（`CollectionCard.vue:210-213` 改 `--shadow-card-hover`），列表视图 hover 却出现 3px 粉色左条 —— 同一模块内两种 hover 反馈。

**建议**：`CollectionRow.vue:174-175` 与 `NoteRow.vue:204-205` 的 hover 去掉 `box-shadow:inset 3px 0 0 var(--brand)`，仅保留 `background:var(--glass-chip-bg)`；`:active`（`CollectionRow.vue:178-180`、`NoteRow.vue:207-209`）保留 inset 描边以表达按压。把 inset 品牌描边留给「选中/激活」语义（与 `.is-selected` 配合）。

**类型**：视觉问题。

---

### P1-2【视觉】五处列表行间距/内边距分两档，同一「列表」概念两种容器语言

> **状态（2026-10-16）：已实施。** 详见 [十、第三批实施与验收](#十第三批实施与验收2026-10-16-追加)。

| 视图 | 列表容器 | 行内边距 | 行间距 | 行容器样式 |
|---|---|---|---|---|
| 收藏夹列表 | `CollectionList.vue:126-129` `.list-items{gap:2px}` | `CollectionRow.vue:167` `padding:12px 16px` | **2px** | 无背景无边框 |
| 便签列表 | `NoteList.vue:103-106` `.list-inner{gap:2px}` | `NoteRow.vue:198` `padding:12px 16px` | **2px** | 无背景无边框 |
| 云盘 | `DiskView.vue:678` `.file-list{gap:8px}` | `DiskView.vue:689` `padding:10px 14px` | **8px** | `background:var(--bg-card)` + `border:1px solid var(--border)` |
| 密码库 | `VaultView.vue:807` `.vault-list{gap:8px}` | `VaultView.vue:818` `padding:12px 14px` | **8px** | `background:var(--bg-card)` + `border:1px solid var(--border)` |
| 回收站 | `RecycleView.vue:420` `.recycle-list{gap:8px}` | `RecycleView.vue:432` `padding:10px 14px` | **8px** | `background:var(--glass-bg-trans)` + `border:1px solid var(--glass-border)` |

**建议**：统一为 `gap:8px`（把 `CollectionList.vue:129` 与 `NoteList.vue:106` 的 `gap:2px` 改 `8px`），行内边距统一为 `12px 14px`（`CollectionRow.vue:167` 与 `NoteRow.vue:198` 由 `12px 16px` 改 `12px 14px`；`DiskView.vue:689` 与 `RecycleView.vue:432` 由 `10px 14px` 改 `12px 14px`）。

**行容器语言**二选一（`CollectionRow.vue:162-167` / `NoteRow.vue:194-198` 当前为无背景无边框，与云盘/密码库/回收站三处的「有底有框」分叉）：
- **推荐**：给 `.collection-row` 与 `.note-row` 补 `background:var(--bg-card)` + `border:1px solid var(--border)`，与 `DiskView.vue:685-695` 的 `.file-item`、`VaultView.vue:814-823` 的 `.vault-item` 一致。注意此时 `gap` 应取 `8px`（相邻卡片需呼吸），与云盘/密码库对齐。
- 备选：反向把云盘/密码库/回收站的行去掉边框（仅留 `background`），保留 2px `gap`。改动面更大且会削弱行的可点区域感，不推荐。

`RecycleView.vue:428-437` 的 `.recycle-item` 用 `--glass-bg-trans` + `--glass-border` 可保留，因其渲染在 `JPageShell` 的玻璃面板内，需要与面板底区分层级。

**类型**：视觉问题。

---

### P1-3【视觉】卡片视图 hover 只变阴影，列表视图 hover 变底色 —— 同模块两套反馈（与 P1-1 同源，此处单列影响面）

> **状态（2026-10-16）：已实施。** 详见 [十、第三批实施与验收](#十第三批实施与验收2026-10-16-追加)。

**证据**：`CollectionCard.vue:210-213`（hover 仅 `transform` + `--shadow-card-hover`）vs `CollectionRow.vue:173-175`（hover 变 `background` + inset 描边）。

**建议**：统一为「hover 变 `background:var(--hover-bg)` + `--shadow-card-hover`」，两处都加。卡片已有 `box-shadow` 过渡（`CollectionCard.vue:206` 起），只需补 `background` 过渡。

**类型**：视觉问题。

---

### P1-4【视觉】便签卡与收藏卡分叉为两套卡片底色/描边

> **状态（2026-10-16）：已实施。** 详见 [十、第三批实施与验收](#十第三批实施与验收2026-10-16-追加)。

**证据**
- `CollectionCard.vue:201-202`：`background:var(--bg-card)`（亮 **#FFFFFF** / 暗 **#26262A**）；`border:1px solid var(--border)`（亮 **#D1D1D6** / 暗 **#45454A**）
- `NoteCard.vue:187-188`：`background:var(--pink-white)`（亮 **#FFF5F8** / 暗 **#2D1F26**）；`border:1px solid color-mix(in srgb, var(--pink-peach) 40%, transparent)`（亮粉 / 暗 **#5A3A45**）

两者其余规格完全一致（`.card-body{padding:14px 16px 16px;gap:8px}`、`.card-top-bar` 3px 渐变、图标盒 40px/`--radius-sm`/`--pink-cherry`、标题 `--fs-base`/600）。

**问题**：便签卡的粉色**不来自模块色系统**（`--module-note` 是 #2F9DF7 蓝），是历史遗留的品牌粉。结果是暗色模式下 `#2D1F26`（粉棕）与 `#26262A`（中性）两种卡片底并存。

**建议**：`NoteCard.vue:187-188` 改为与 `CollectionCard.vue:201-202` 相同的 `background:var(--bg-card)` + `border:1px solid var(--border)`。模块差异由 `.card-top-bar` 渐变（现两者都用 `--gradient-top-bar`）或图标色承担 —— 若要保留区分，把 `NoteCard` 的顶条改为 `--module-note` 到透明的渐变，这是既有模块色令牌的正当用法。

**类型**：视觉问题。

---

### P1-5【视觉】ModuleToolbar 同排按钮尺寸不齐

> **状态（2026-10-16）：已实施。** 详见 [十、第三批实施与验收](#十第三批实施与验收2026-10-16-追加)。

**证据**：`jnclub-web/src/modules/bookmark/components/ModuleToolbar.vue`
- `.btn-new`（main.css:184，`--gradient-btn` + `--radius-pill`）走 naive **默认尺寸**（medium，固有高度 34px）
- `.io-export-btn` / `.archived-toggle` 走 `size="small"`（固有高度 28px）

同排高度差 6px，且左侧是品牌渐变实心、右侧是玻璃描边。

**建议**：给 `btn-new` 显式加 `size="small"`（或把右侧两个改回默认尺寸）。考虑主次关系，推荐统一为 `size="small"`，`.btn-new` 的 pill 与渐变保留 —— 主按钮靠**色彩**区分，而非靠**尺寸**。

**类型**：视觉问题。

---

### P1-6【视觉】列表行标题字重不一致（500 vs 600）

**证据**：`CollectionRow.vue:214` `.title-link{font-size:var(--fs-base);font-weight:500}` vs `NoteRow.vue:232` `.row-title{font-size:var(--fs-base);font-weight:600}`。而卡片侧两者都是 600（`CollectionCard.vue:277`、`NoteCard.vue:258`）。

**建议**：`CollectionRow.vue:214` 的 `font-weight:500` → `600`，与其余三处对齐。

**类型**：视觉问题。

---

### P1-7【视觉】列表行元信息间距 16px vs 4px

**证据**：`CollectionRow.vue:231-238` `.row-meta{gap:16px}` vs `NoteRow.vue:270-276` `.row-meta{gap:4px}`；回收站 `RecycleView.vue:456-461` `.item-sub{gap:12px}`。同为「行尾元信息组」。

**建议**：统一为 `gap:12px`（与回收站对齐，视觉呼吸更均匀）。

**类型**：视觉问题。

---

### P1-8【视觉】概览页统计数值比设计意图小一档（死代码掩盖）

> **状态：已处理（第四批，2026-10-16），但结论与原建议相反** —— 死代码已删除；「提档到 32px」经实测**否决**（1440px 下 5 位数字即溢出），保留 24px。详见 11.1。

**证据**
- `jnclub-web/src/shared/views/DashboardView.vue:638-699` 定义了 `.stat-card` 系列（`min-height:112px`、`padding:24px`、`gap:18px`、`.stat-value{font-size:var(--fs-7xl)}`(**32px**)、`.stat-icon{44px}`）
- 但模板 `DashboardView.vue:363` 实际渲染的是 `<JStatCard>`，其样式在 `jnclub-web/src/shared/components/ui/JStatCard.vue:39-92`：`min-height:96px`、`padding:16px 18px`、`gap:14px`、`.j-stat-value{font-size:var(--fs-5xl)}`(**24px**)、`.j-stat-icon{38px}`

**后果**：概览页数值实渲染 24px，而非 `.stat-card` 意图的 32px；`.stat-card`、`.stat-text`、`.stat-icon`、`.stat-value`、`.stat-label`、`.stat-warn-dot`、`@keyframes dash-blink`、以及 `DashboardView.vue:690-698` 的移动端覆盖全部为死代码。

**建议**：先删 `DashboardView.vue:638-699`（含 690–698 的移动端块）与 `dash-blink` 关键帧，避免两套规格继续漂移；随后决定概览数值档位 —— 若采纳原意图，把 `JStatCard.vue:80-82` 的 `.j-stat-value` 由 `var(--fs-5xl)`(24px) 提到 `var(--fs-7xl)`(32px)，并把 `min-height` 由 96px 提到 112px、`padding` 由 `16px 18px` 提到 `20px 22px`（与 `DashboardView.vue:715` 的 `.panel{padding:20px}` 对齐）。

**类型**：视觉问题（数值层级不足）+ 代码卫生。

---

### P1-9【视觉】概览页同屏四种面板内边距、两种区块标题规格

> **状态：部分实施（第四批，2026-10-16）** —— 仅采纳「`.greet-card` 对齐 20px」；「`.quick-bar` 跟进 20px」与「两类标题统一 13px/600」经复核**不采纳**。详见 11.2。

**证据**（均在同一页）
| 元素 | 行号 | padding | 标题规格 |
|---|---|---|---|
| 统计卡 | `JStatCard.vue:44` | `16px 18px` | 值 24px/800，标签 12px/`--glass-text-secondary` |
| 问候卡 | `DashboardView.vue:1022` `.greet-card` | `18px 22px` | 标题 18px/700 |
| 普通面板 | `DashboardView.vue:715` `.panel` | `20px` | `.panel-title` 13px/600/`--text-1` |
| 快捷条 | `DashboardView.vue:832` `.quick-bar` | `12px 14px` | — |
| 区块标题 | `DashboardView.vue:613-617` `.dash-group-title` | — | **12px/700/`--text-3`**（对比 `.panel-title` 13px/600/`--text-1`） |

**建议**：面板类（`.greet-card`/`.panel`/`.quick-bar`）统一到 `20px`；`JStatCard` 到 `20px 22px`（见 P1-8）。区块标题 `.dash-group-title` 与面板标题 `.panel-title` 统一为 `font-size:var(--fs-md)`(13px) + `font-weight:600`，用**颜色**（`--text-3` vs `--text-1`）区分层级 —— 现在字号与字重同时变化，层级对比过强。

**类型**：视觉问题。

---

### P1-10【视觉】搜索抽屉内四种 chip 规格并存

> **状态（2026-10-16）：已实施（第七批）。** 详见 十四、第七批实施与验收。
> 收敛为两级：一级「主筛选」`5px 14px`（`.chip` / `.j-filter-chip`）、二级「次级筛选」`4px 12px`（`.history-chip` / `.type-filter-chip` / `.syntax-chip`）。
> **但本项验收暴露一个更严重的既有缺陷**：搜索结果区（含本项的两个目标元素 `.type-filter-chip` 与 `.syntax-chip`）在当前代码下**永不渲染** —— `doSearch` 从未把响应写回 `result`（回归于提交 `7c7f4f9`）。详见 14.2。
> **该缺陷已于第八批修复**（见第十五章），两个目标元素的 `4px 12px` / 12px / pill / h 26.5 由此获得真实 DOM 实测确认，本项闭环。

**证据**（均在 `jnclub-web/src/modules/bookmark/components/SearchDrawer.vue`）
| 元素 | 行号 | padding | 字号 | 圆角 |
|---|---|---|---|---|
| 类型筛选 chip | 929-935 `.type-filter-chip` | `3px 10px` | `--fs-xs`(11px) | pill |
| 语法 chip | 954-963 `.syntax-chip` | `3px 10px` | `--fs-xs` | pill |
| 历史 chip | 895-901 `.history-chip` | `4px 12px` | `--fs-sm`(12px) | pill |
| （对照）全局筛选 chip | `JFilterBar.vue:63` | `5px 14px` | `--fs-sm` | pill |

**建议**：`.type-filter-chip` 与 `.syntax-chip` 的 `padding` 统一为 `4px 12px`、`font-size` 统一为 `var(--fs-sm)`，与 `.history-chip` 和全局 `.j-filter-chip` 归为一档（全局档 `5px 14px` 作为「主筛选」，抽屉内 `4px 12px` 作为「次级筛选」，形成两级而非四级）。

**类型**：视觉问题。

---

### P1-11【视觉】概览/回收站与主壳的滚动容器不同

> **状态（2026-10-16）：已实施（第六批），但结论与原建议相反。** 详见 十三、第六批实施与验收。
> 根因不是「滚动容器选错」，而是**虚拟列表用 `calc(100vh - 240px)` 猜高度**，与主壳实际 chrome 高度失配（桌面差 41px、移动端差 28.5px）——猜出来的高度大于可用空间，才制造出第二个滚动条。故本批改为**自上而下传递高度**（`.collection-column` 成为 flex 列、虚拟列表 `flex:1`），而不是把滚动容器换成 `.content-area`：原建议的「让 `.content-area` 滚」会让虚拟列表失去确定高度、退化成「外层滚 + 列表不虚拟」，实测已否决。

**证据**：`Home.vue:685-693` `.collection-column{flex:1;overflow-y:auto}` —— 滚动发生在**面板内部**，面板边框固定于视口。而 `JPageShell.vue:49-64` `.j-page-body{flex:1;overflow-y:auto}` —— 概览/回收站的玻璃面板**随内容增长整体滚动**。

**后果**：收藏夹页面板底边始终贴视口底；概览页面板高度随内容变化，滚动条位置与玻璃边框的相对关系不同。

**建议**：把 `Home.vue` 的 `.collection-column` 改为不滚动（`overflow:visible`），让 `Home.vue:643` 的 `.content-area` 成为滚动容器，与 `JPageShell` 一致。或在 `JPageShell` 侧反向对齐。**推荐前者**，因为 `.content-area` 已有 `flex:1` 与内边距，改动面更小（`Home.vue:685-693` 去掉 `overflow-y:auto`，`:643` 补 `overflow-y:auto`）。

**类型**：视觉问题（观感差异），但涉及布局结构，建议与 P0-1 一并处理。

---

## 四、P2 — 可选（细节与代码卫生）

| 编号 | 问题 | 证据 | 建议 | 类型 |
|---|---|---|---|---|
| P2-1 | 面板内边距 fallback 值与令牌实际值不符 | `Home.vue:693` `padding:var(--layout-panel-padding,18px 20px)`，令牌实际为 `20px 24px`（fallback 永不生效，但代码具误导性） | 删掉 fallback，写 `var(--layout-panel-padding)`；`JPageShell.vue:22` 已是正确写法 | 代码卫生 |
| P2-2 | 移动端底部预留高度硬编码 | `MainLayout.vue:81` `padding-bottom:calc(56px + env(safe-area-inset-bottom))`，而 `MobileTabBar` 用 `var(--tabbar-height,56px)` | 改为 `calc(var(--tabbar-height,56px) + env(safe-area-inset-bottom))`；否则紧凑模式（`--tabbar-height:50px`）下多出 6px 空白 | 视觉问题 |
| P2-3 | 空态两个 CTA 圆角不同 | `JEmptyState.vue:62` `.empty-cta.glass-pill-btn`（pill）vs `:70` `.empty-cta-ghost{border-radius:var(--radius-md)}`（16px） | 两者由 `variant` 互斥、不同屏出现，非当前同屏冲突；建议把 `:206` 的 `--radius-md` 改为 `var(--radius-pill)` 以统一 | 视觉问题 |
| P2-4 | 概览页侧栏高亮依赖硬编码模块名 | `OverviewLayout.vue:43` 与 `RecycleLayout.vue:56` 均传 `active-module="'bookmarks'"`；当前靠 `SideNav.vue:102` 的 `route.name === 'app'` 条件挡住，不会误亮 | 改为传空值或 `'overview'`/`'recycle'`，去掉对判定条件的隐式依赖 | 代码卫生 |
| P2-5 | naive-ui 组件字号未接入 `--fs-*` 阶梯 | `jnclub-web/src/themes/light.ts` / `dark.ts` 的 `common` 未覆盖 `fontSize*` 字段 | naive 默认（14/13/12px）恰好接近 `--fs-base/--fs-md/--fs-sm`，视觉上暂无问题；建议在 `light.ts` 显式绑定，防止后续 naive 升级漂移 | 代码卫生 |
| P2-6 | 侧栏宽度与目录栏宽度无令牌 | `SideNav.vue` `:width="240"` / `:collapsed-width="64"`；`Home.vue:670` `.folder-column{flex:0 0 220px}` | 若后续要支持密度切换，建议抽 `--layout-sider-width:240px`、`--layout-folder-width:220px` | 代码卫生 |
| P2-7 | 列表行图标盒尺寸四处不同 | `CollectionRow.vue:187` `.row-favicon{20px}`、`NoteRow.vue:219` `.row-icon{20px}`（一致）；但 `DiskView.vue:706` `.file-icon{36px}` vs `VaultView.vue:829` `.item-icon{38px}` | 同为「行内图标盒」，36/38px 取其一（建议 38px，与 `JStatCard.vue:70` 的 `.j-stat-icon{38px}` 一致） | 视觉问题 |
| P2-8 | 密码库与云盘行 hover 反馈不同 | `VaultView.vue:824-827` hover 仅变 `background:var(--glass-chip-bg)`；`DiskView.vue:695` hover 变 `border-color:var(--brand-text)` + `--shadow-card-hover` | 统一为云盘的写法（描边 + 阴影），与 P1-3 的卡片 hover 语言一致 | 视觉问题 |

> **状态（2026-10-16）：P2-3 ~ P2-8 已全部处理完毕**（第九批，见第十七章）。其中：
> **P2-8 经复核无需改动**（第三批实施 P1-1 时已顺带统一）；**P2-3 的真实缺陷比本表描述更严重**——`.empty-cta-ghost` 之所以「永不渲染」，是因为全仓 0 处使用 `variant="error"`，而唯一需要错误态的地方（`Home.vue` 的 `loadError`）用默认 empty 变体凑了一个「重试」按钮，导致错误态显示**书签图标 + 带加号的品牌主按钮**；**P2-4 的建议不可行**（`active-module` 的类型 `ModuleKey` 不含 `'overview'`/`'recycle'`），真正的修法是在 `SideNav.vue` 的 `moduleLabel` 补齐路由分支。各条的实际处置以第十七章为准。

---

## 五、信息架构问题（与视觉问题分列）

### IA-1 侧栏「模块」与「独立页面」两类入口无视觉区分

> **状态（2026-10-16）：维持现状，记为已知取舍。** 产品决策结论，见第十八章 18.1。P0-1 已让两套页头视觉同构，剩余差异只是结构（面包屑条 vs 大标题条），跳变感已削弱；而给 `kind:'route'` 加标记等于把开发者的架构分层教给用户，用户学到后无可操作用途。

`jnclub-web/src/shared/layout/navConfig.ts` 中 `kind:'module'`（收藏夹/便签/云盘/密码库，同壳切换）与 `kind:'route'`（概览/音乐/回收站/下载中心，整页跳转）在侧栏中渲染完全相同（同一 `NavItem`）。用户点击后遭遇页头结构突变（见 P0-1），却没有预期。
**建议**：给 `kind:'route'` 项加一个轻量标记（如 `NavItem` 中加 `font-size:var(--fs-xs)` 的外链小图标，颜色 `--text-4`），或至少让 P0-1 的页头统一消除突变。

### IA-2 面包屑首项「JNClub」不是可导航层级

> **状态（2026-10-16）：已实施**，见第十八章 18.3。首项已从模板删除，桌面与移动端统一为「模块 / 当前目录」两项；原移动端用于隐藏首项的 `:first-child{display:none}` 规则与 400px 断点的 `:nth-child` 序号同步调整。
`AppHeader.vue` 面包屑为「JNClub / 模块名 / 当前目录」，但首项是品牌名，点击无去处（`breadcrumb-root` 语义模糊）。在 1440px 下它占用了顶栏最左的视觉起点，挤占了真正有信息量的「模块名 / 目录名」。
**建议**：移除首项，面包屑改为「模块名 / 目录名」；品牌已由侧栏 `BrandLogo` 承担（`BrandLogo.vue`，`--fs-3xl`/800）。移动端 `AppHeader.vue:650-698` 已有隐藏逻辑，说明首项冗余已被察觉。

### IA-3 目录 chips 与左侧目录树表达同一层级

> **状态（2026-10-16）**：**已实施**，见第十二章 12.2。采用了 CSS 断点方案（基础规则 `display:none` + 移动端媒体查询恢复），未用 `v-if`——避免在 JS 里重复判断视口，也保证移动端 chip 的横滑样式仍挂在同一元素上。

`Home.vue:467-480` 的 `.chip-bar` 渲染 `topLevelDirs`（`Home.vue:148-150`：`directories.filter(d => !d.parentId)`），与 `Home.vue:670` 的 `.folder-column`（`FolderPanel` → `FolderTree`）是同一份顶层目录数据的两种呈现，在 1440px 下同屏并列。
**建议**：桌面端（`folder-column` 可见时）隐藏 `.chip-bar`，仅在 `@media (max-width:767px)`（`folder-column` 已 `display:none`，`Home.vue:797`）显示。这既消除冗余，又让移动端保留快速切换能力。改动点：`Home.vue:467` 加 `v-if` 或 CSS 断点控制。

### IA-4 搜索抽屉自称「命令面板」

> **状态（2026-10-16）：已实施**，见第十八章 18.3。标题改为「搜索」；命令列表仍作为空输入时的回退保留。

`SearchDrawer.vue:471` 标题为「命令面板」，快捷键提示 `Ctrl / ⌘ + K`（`:477`），但主体是跨模块搜索（placeholder：「搜索收藏 / 便签 / 文件 / 密码 / 音乐…」，`:484`）。面板内确实有 `cmd-list`（`:906-921`）命令区，但命令是次要内容。
**建议**：标题改为「搜索」或「全局搜索」，保留命令区；或把命令区提升为默认态（空输入时显示命令而非历史），使命名与内容相符。

### IA-5 概览页与主壳模块的返回关系单向

> **状态（2026-10-16）：已实施**，见第十八章 18.2。新增 `jnclub-web/src/shared/composables/useSmartBack.ts`：有可回退历史时 `router.back()`，无历史时退回收藏夹。原报告建议的「直接改 `router.back()`」会让「直接打开 /overview」变成死按钮，故未采纳。

`JPageHeader` 的 `back` 按钮在 `OverviewLayout.vue` 与 `RecycleLayout.vue` 中均 `router.push('/')`，即「返回收藏夹」而非「返回上一页」。用户从便签页进入概览再返回，会落到收藏夹。
**建议**：`OverviewLayout.vue` / `RecycleLayout.vue` 的 `@back` 改为 `router.back()`，或在 `back` 语义上明确标注为「返回首页」。

---

## 六、执行顺序建议

> **实施进度（截至 2026-10-16）**
>
> | 批次 | 项 | 状态 |
> |---|---|---|
> | 第一批 | P0-2、P1-6、P1-7、P2-1、P2-2 | ✅ 已实施（第八章） |
> | 第二批 | P0-2b、P0-4、P0-1 | ✅ 已实施（第九章） |
> | 第三批 | P1-1、P1-2、P1-3、P1-4、P1-5 | ✅ 已实施（第十章） |
> | 第四批 | P1-8（死代码已清；提档建议经实测否决）、P1-9（仅 greet-card 一条） | ✅ 已实施（第十一章） |
> | 第五批 | P0-3、IA-3 | ✅ 已实施（第十二章） |
> | 第六批 | P1-11（滚动容器统一）、`.j-page-body` 移动端内边距失效 | ✅ 已实施（第十三章） |
> | 第七批 | P1-10（搜索抽屉内 chip 规格收敛为两级） | ✅ 已实施（第十四章） |
> | 第八批 | 搜索结果区功能缺陷 + 类型筛选空态判断错误（非计划内，第七批验收时发现） | ✅ 已修复（第十五章） |
> | 复核 | `7c7f4f9` 批量删除的字段级误删全仓复核（非计划内，第八批后追加） | ✅ 已完成（第十六章） |
> | 第九批 | P2-3、P2-4、P2-5、P2-6、P2-7、P2-8（P2-8 经复核无需改动） | ✅ 已实施（第十七章） |
> | 第十批 | IA-1（维持现状）、IA-2、IA-4、IA-5；白屏回归修复（非计划内，IA 批实测时发现） | ✅ 已实施（第十八章） |
> | 第十一批 | 遗留清理与部署前加固（非计划内）：音乐页/Manifest 假象判定、模板根层级自动化防护、紧凑模式修复、依赖声明加固 | ✅ 已完成（第十九章） |

1. **第一批（1 次提交，纯数值收敛，风险低）**：P0-2（`.chip` 对齐 `--glass-chip-*`）、P1-6、P1-7、P2-1、P2-2。
2. **第二批（结构对齐）**：P0-1（页头统一）、P1-11（滚动容器统一）、P2-4。
3. **第三批（模块间视觉归位）**：P1-1、P1-2、P1-3、P1-4、P1-5。
4. **第四批（概览页专项）**：P1-8（含死代码清理）、P1-9。
5. **第五批（移动端）**：P0-3、IA-3。
6. **IA 项**：IA-1/IA-2/IA-4/IA-5 需产品决策，建议与第一批并行讨论。

---

## 七、复评建议（本次未能覆盖的）

本次截图样本缺少以下状态，收敛后建议补拍以验证：
- **空态**：`JEmptyState`（`--empty-pad:40px`、图标环 80px、标题 `--fs-2xl`/700/`--brand-text`）在 `DiskView.vue:661` `.disk-empty{padding:var(--empty-pad) 0}` 与 `VaultView.vue:803` `.vault-empty{padding:var(--empty-pad) 0}` 中被二次包裹，实际上下留白为 `40px + 64px = 104px`，可能过空。
- **加载态**：`JSkeletonGrid` / `JSkeletonList` 与最终内容的尺寸对齐情况。
- **错误态**：`JErrorState` 与 `DashboardView.vue:609` `.dash-error{padding-top:60px}` 的一致性。
- **批量选择态**：`.batch-check`（main.css）+ `.is-selected`（`--selected-bg` + `inset 0 0 0 1px var(--brand)`）在卡片与列表两种视图下的表现，以及与 P1-1 中「列表行 hover inset 描边」的语义冲突。
- **紧凑模式**：`.app-density-compact`（main.css）已覆盖 20+ 选择器，需验证是否与本次建议的数值产生新的分叉。

---

## 八、第一批实施与验收（2026-10-15 追加）

第一批 5 项已实施完毕，构建通过（`npm run build` exit 0），并在浏览器实测复核。

| 编号 | 文件 | 改动 |
|---|---|---|
| P0-2 | `jnclub-web/src/assets/main.css:157-181` | `.chip` 六处属性对齐 `--glass-chip-*`：`padding:6px 14px→5px 14px`、`border-radius:var(--radius-sm)→var(--radius-pill)`、`background:var(--bg-card)→var(--glass-chip-bg)`、`border-color:var(--border)→var(--glass-chip-border)`、`color:var(--text-2)→var(--glass-chip-text)`、`font-size:var(--fs-md)→var(--fs-sm)`；hover 连带对齐（去掉 `background:var(--hover-bg)` 覆盖，改 `border-color/color:var(--brand-text)`） |
| P1-6 | `jnclub-web/src/modules/bookmark/components/CollectionRow.vue:212-217` | `.title-link` `font-weight:500→600`，并改为 `:deep(.title-link)`（见下） |
| P1-7 | `CollectionRow.vue:234-238`、`jnclub-web/src/modules/bookmark/components/NoteRow.vue:271-275` | `.row-meta` 间距统一 `gap:12px`（原 16px / 4px 两档） |
| P2-1 | `jnclub-web/src/modules/bookmark/views/Home.vue:692` | `padding:var(--layout-panel-padding,18px 20px)→var(--layout-panel-padding)`（令牌实测为 `20px 24px`，fallback 永不生效且误导） |
| P2-2 | `jnclub-web/src/shared/layout/MainLayout.vue:81` | `calc(56px + env(...))→calc(var(--tabbar-height,56px) + env(...))` |

### 8.1 实施中发现并修复的缺陷：`.title-link` 的 scoped 规则从未生效

P1-6 的表面问题是「字重 500 vs 600 不一致」，实际根因更深：

`CollectionRow.vue:121` 用 `<NEllipsis class="title-link">`。naive-ui 把 `class` 透传到其内部元素，**该元素不携带本组件的 scope id**。Vue scoped 生成的规则是 `.title-link[data-v-44237ddd]`，而 DOM 上 `.title-link` 的 `data-v` 属性列表为空 → 规则**静默失效**。

实测证据：改前 `font-weight` 计算值为 **400**（不是源码里的 500），`color` 也不是 `var(--text-1)`。也就是说这条规则从写下那天起就没生效过，只把 600 写进去同样不会生效。

**修复**：改为 `:deep(.title-link)`，编译为 `[data-v-44237ddd] .title-link`。复测 `font-weight:600`、`color:rgb(29,29,31)`（= `--text-1`），生效。

**同类风险全站扫描**：写了一段运行时检测（遍历 `document.styleSheets`，对形如 `.cls[data-v-xxx]` 的规则，找出「元素自身无该 scope id、但存在带该 scope id 的祖先」的失效命中），在 bookmarks/notes/files/vault/overview/recycle 六个页面执行，**命中 0 处**。当前是孤例，非系统性。

### 8.2 本轮新发现：P0-2 修得过头，两个筛选栏视觉层级塌陷

P0-2 的目标是消除 `.chip`（目录切换）与 `.j-filter-chip`（标签筛选）的「两套视觉方言」。但实施后两者**全部属性完全相同**：

```
.chip            padding 5px 14px · radius 999px · bg rgba(236,91,142,.12) · color #B82A63 · border rgba(236,91,142,.28) · 12px/500
.j-filter-chip   padding 5px 14px · radius 999px · bg rgba(236,91,142,.12) · color #B82A63 · border rgba(236,91,142,.28) · 12px/400
```

两者在收藏夹页上下相邻（`.chip-bar` top 149 h 29 → `.tag-bar` top 196 h 83，间隔 18px），语义却不同：上方是**目录导航**（单选、决定数据范围），下方是**标签筛选**（多选、在范围内过滤）。同款同色后，用户无法从视觉判断这两排胶囊是同一控件还是两种功能。

原 `.chip` 的**中性色相**（白底 `--bg-card` + `--text-2` 灰字）其实承载了「这是导航不是筛选」的语义。P0-2 真正需要统一的只是**几何档位**（10px vs pill 圆角、13px vs 12px 字号、6px vs 5px 内边距）——那才是视觉方言；色相差异是有效的语义编码。

**建议（新增 P0-2b，优先级与 P0-2 同级）**：在已统一的几何档位上，把色相拆回两档，但两档都取自现有令牌，不引入新值：

- `.chip`（目录导航）：走中性档 —— `background:var(--bg-card)`、`border:1px solid var(--border)`、`color:var(--text-2)`；激活态 `.chip-active` 保持现有 `--brand-soft` + `--brand-text` 粉底（导航的「当前位置」用品牌色强调，与侧栏 `NavItem` 激活语言一致）。
- `.j-filter-chip`（标签筛选）：保持品牌粉档 —— 现用的 `--glass-chip-bg` / `--glass-chip-border` / `--glass-chip-text`。

这样「几何统一、色相分工」：圆角/字号/内边距全部一致（P0-2 的原始目标达成），而两种控件靠「中性 vs 品牌」一眼可分。改动仅落在 `jnclub-web/src/assets/main.css:157-181` 五处属性回退，风险与第一批同级。

### 8.3 验收截图

| 文件 | 内容 |
|---|---|
| `.ui-review/after-01b-bookmarks-list-light-1440.png` | 修复前·亮色·卡片视图 |
| `.ui-review/after-01d-listmode-fixed-1440.png` | 修复后·亮色·极简列表（标题 600 生效） |
| `.ui-review/after-05-chipbar.png` / `after-06-tagbar.png` | 两个筛选栏裁切对比（8.2 的证据） |
| `.ui-review/after-07-dark-1440.png` | 暗色复验 |

暗色复验：`.chip` 实测 `rgba(255,143,171,.16)` / `#FFB3C6` / `rgba(255,143,171,.32)`，与 `--glass-chip-*` 暗色档完全一致，无回归。

### 8.4 未实施项状态

截至第一批结束时，第三批及之后（P0-3、P1-1~P1-5、P1-8~P1-11、P2-4、IA-1~IA-5）**均未改动**。其中 **P0-3（移动端 TabBar 溢出）复测仍存在**：viewport 390px，7 项 × 64px = 448px > 390px，`overflow:true`。

> **后续更新（2026-10-16）**：**P1-1 ~ P1-5 已于第三批实施完毕**（见第十章），本批同时修复了一个虚拟滚动缺陷。仍未改动的为：**P0-3、P1-8 ~ P1-11、P2-3 ~ P2-8、IA-1 ~ IA-5**。

> **再更新（2026-10-16）**：**P1-8、P1-9 已于第四批处理**（见第十一章）。其中 **P1-8 的「提档到 32px」经实测否决**、**P1-9 仅采纳 greet-card 一条**，两项的原始建议均以第十一章为准。仍未改动的为：**P0-3、P1-10、P1-11、P2-3 ~ P2-8、IA-1 ~ IA-5**。
>
> **三更新（2026-10-16）**：**P0-3、IA-3 已于第五批实施**（见第十二章）。至此**全部 4 项 P0 均已处理完毕**。仍未改动的为：**P1-10、P1-11、P2-3 ~ P2-8、IA-1、IA-2、IA-4、IA-5**；另有三项跨批遗留的技术债（`.j-page-body` 移动端内边距失效、网格模式 `itemHeight` 虚高、P1-8 数值层级待决策）。
>
> **四更新（2026-10-16）**：**P1-11 与 `.j-page-body` 移动端内边距已于第六批实施**（见第十三章），**P1-10 已于第七批实施**（见第十四章）。至此 **P0（4 项）与 P1（11 项）全部处理完毕**。仍未改动的为：**P2-3 ~ P2-8、IA-1、IA-2、IA-4、IA-5**；跨批遗留技术债两项（网格模式 `itemHeight` 虚高、P1-8 数值层级待决策），另加第七批新发现的**一处功能级缺陷**（搜索结果区永不渲染，见 14.2）。
>
> **五更新（2026-10-16）**：第七批发现的功能级缺陷**已于第八批修复**（见第十五章），并顺带修掉一个被它掩盖的空态判断错误。**至此所有 P0、P1 及已知功能级缺陷均已处理完毕**。仍未改动的为：**P2-3 ~ P2-8、IA-1、IA-2、IA-4、IA-5**；跨批遗留技术债两项（网格模式 `itemHeight` 虚高、P1-8 数值层级待产品决策）。
>
> **六更新（2026-10-16）**：`7c7f4f9` 的字段级误删已做全仓复核（第十六章，结论为孤例）；**P2-3 ~ P2-8 已于第九批全部处理**（第十七章，其中 P2-8 经复核确认无需改动、P2-3 的真实缺陷比原报告描述的更严重）。至此**除 IA-1、IA-2、IA-4、IA-5 四项待产品决策外，本报告全部 P0/P1/P2 条目均已闭环**。跨批遗留技术债剩两项：网格模式 `itemHeight` 虚高、P1-8 数值层级待产品决策。
>
> **七更新（2026-10-16）**：**IA-1、IA-2、IA-4、IA-5 四项已于第十批全部落地**（第十八章，IA-1 经决策维持现状），并在实测中发现并修复一处会导致整页白屏的回归。**至此本报告全部条目（P0/P1/P2/IA）均已处理完毕**。第十一批（第十九章）完成遗留清理与部署前加固：音乐页空白与 PWA Manifest 报错经查均为 **dev 环境假象**（非缺陷），并新增模板根层级构建时防护、修复紧凑模式在概览/回收站失效、加固依赖声明。仍未决的仅剩两项产品决策级技术债：**网格模式 `itemHeight` 虚高**（缩小有重叠风险，需先给卡片加上界）与 **P1-8 统计数值层级**（提档会牺牲数字位数余量）。

---

## 九、第二批实施与验收（2026-10-15 追加）

本批含 **P0-2b**（8.2 的色相拆分）、**P0-4**（状态色分档）、**P0-1**（页头统一）。三处改动均已 `npm run build` 通过（`✓ built in 5.38s`，PWA precache 181 entries），并在 1440×900 / 390×844 两种视口下用 Playwright 读回计算样式复核。

### 9.1 P0-4：状态色拆成「填充档 / 文本档」两层

**根因**：`--danger-text` / `--warning-text` / `--success-text` 三个令牌在 `tokens.ts` 里亮色与暗色**共用同一组浅色值**（`#FF8A97` / `#F0A13A` / `#37C978`），而 `--state-*` 填充档也是同一组值。也就是说项目里只存在「一档状态色」，它被同时用在进度条填充（`background`）和白底文字（`color`）上——后者实测 2.25 / 2.13 / 2.15:1，远低于 AA 要求的 4.5:1。

对照可见：`--brand-text` 与 `--glass-chip-text` **早已**按亮/暗分档（亮色 `#B82A63`、暗色 `#FFB3C6`），状态色只是漏做了这一步，并非设计意图。

**改动**（`jnclub-web/src/themes/tokens.ts`）

| 令牌 | 亮色（改后） | 白底对比度 | 暗色（不变） | 深底对比度 |
|---|---|---|---|---|
| `--danger-text` | `#C0334A` | 5.51:1 | `#FF8A97` | 6.70:1 |
| `--warning-text` | `#8A5A00` | 5.93:1 | `#F0A13A` | 9.29:1 |
| `--success-text` | `#1E7A46` | 5.35:1 | `#37C978` | 7.36:1 |

暗色侧沿用原值——深底上这组浅色本身就同时满足填充与文本两种用途（实测 5.30~9.29:1），所以暗色不需要分档，`stateSuccess` 等填充档在两套主题里都保持不变（进度条、diff 底色、图标描边均无视觉变化）。

**同时修正 19 处引用误用**——原先「该用文本档的地方写了填充档」和「该用填充档的地方写了文本档」两个方向的错误都有：

| 方向 | 处数 | 示例 |
|---|---|---|
| 文本 → 改用文本档 | 17 | `VaultView.vue:696`（`.guide-warn` 提示文字）、`VaultView.vue:731-733`（密码强度文字）、`VaultView.vue:881/886`（健康标签文字）、`NoteEditor.vue:1298/1299`（同步状态图标）、`NoteEditor.vue:1517/1518`（保存时间戳）、`NoteVersionsModal.vue:464/499`（diff 行文字）、`DashboardView.vue:780`、`DeadLinkModal.vue:196` |
| 填充 → 改回填充档 | 2 | `PasswordEditorModal.vue:312/313`（密码强度**条**的 `background` 误用了文本档） |

`PasswordEditorModal` 这两处是反方向误用：强度条（色块）当时取的是 `--warning-text`/`--success-text`。若不一起改，本次把文本档调深后强度条会连带变深，属于「修一处坏一处」——这正是把两个档位显式分开的价值。

**未采用的做法**：直接把 `--state-*` 三个填充档整体调深。那会让进度条、diff 底色、图标描边一起变重，改变已确认的视觉语言，且暗色侧本来就不需要动。

**遗留（不属本批）**：`VaultView.vue:698` 的 `border: 1px solid color-mix(in srgb, var(--state-warning) 25%, transparent)` 仍取填充档做边框——这是**图形**用途（非承载文字的 1px 描边），按分档规则应当如此，无需改。

### 9.2 P0-1：页头统一（方案 A）

**目标**：让「模块内页」（收藏夹/便签/云盘/密码库，走 `AppHeader` 面包屑条）与「独立页」（概览/回收站，走 `JPageHeader` 大标题）在页头高度、分隔线、标题量级、内容起始位置上一致，消除切模块时的跳变。

**改动一：`JPageHeader` 变成与 `AppHeader` 同规格的顶栏条**（`shared/components/ui/JPageHeader.vue`）

```css
.j-page-header {
  height: var(--header-height, 60px);          /* 原：padding: 4px 2px 14px（自适应高度） */
  padding: 0 var(--shell-gutter, var(--layout-page-gutter, 24px));
  margin: 0 calc(-1 * var(--shell-gutter, var(--layout-page-gutter, 24px)));
  border-bottom: 1px solid var(--border);      /* 新增，与 AppHeader 同一条线 */
  flex-shrink: 0;
}
.j-page-header-title { font-size: var(--fs-2xl); font-weight: 700; }  /* 原：--fs-3xl / 800 */
```

标题由 20px/800 收到 18px/700：原值属于**落地页大标题**量级，而它现在所在的位置是「顶栏」——与 `AppHeader` 里 13px 面包屑同处一条 60px 横条，20px/800 会把顶栏读成页面主标题区。

**改动二：`.j-page-shell` 只留水平 gutter，上边距移到 `.j-page-body`**（`shared/layout/JPageShell.vue`）

```css
.j-page-shell { padding: var(--layout-page-padding, 0 24px 0); }   /* 原：12px 24px 0 */
.j-page-body  { margin: 20px auto 0; }                             /* 原：margin: 0 auto */
```

**为什么这样绕**：若保留 shell 的上边距，页头就落在 padding 盒内，`border-bottom` 会在左右各缩进 24px 断掉——分隔线不通栏。把上边距下移到 `.j-page-body` 的 `margin-top` 后，页头紧贴容器顶边，再用等量负 margin 抵消 shell 的水平 padding，分隔线即可通栏（与 `AppHeader` 完全同构）。同时 `--layout-page-padding` 令牌的垂直分量归零，语义收敛为「只表达水平 gutter」。

**改动三：令牌与紧凑模式同步**（`themes/tokens.ts`、`assets/main.css`）

- `layoutPagePadding`：`12px 24px 0` → `0 24px 0`（亮/暗两套，共 2 处），已 `npm run gen:theme` 重新生成 `tokens.generated.css`。
- 删除 `.app-density-compact .j-page-header { padding-bottom: 8px }` 与 `.app-density-compact .j-page-header-title { font-size: var(--fs-2xl) }`：紧凑模式的页头高度与字号**已由 `--header-height`(52px) / `--fs-2xl` 令牌承载**，这两条是令牌化之前的遗留覆盖；保留 `padding-bottom:8px` 会把固定高度的顶栏条重新撑开（52px → 60px），反而破坏本批刚建立的对齐。
- 新增 `.app-layout.app-density-compact .j-page-body { margin-top: 12px }`：紧凑模式主壳的 `.content-area` 上边距是 12px（`main.css:539`），页壳必须同步压到 12px，否则紧凑模式下会**重新出现** 20px vs 12px 的错位——正是本批要消除的那个跳变。用双类提高优先级以压过 `JPageShell` 里 scoped 的 `.j-page-body[data-v-*]`。

**实测复核**（Playwright 读计算样式，非目测）

| 场景 | 页头 | 内容面板 top | 与主壳对照 |
|---|---|---|---|
| 概览 1440 舒适 | h 60px、`border-bottom 1px solid rgb(209,209,214)`、padding `0 24px`、margin `0 -24px` | 80px（= 60 + 20 margin） | 主壳 `AppHeader` h 60、`content-area` top 60、padding-top 20px ✓ |
| 回收站 1440 舒适 | h 60px，同上 | 80px | 同上 ✓ |
| 回收站 1440 紧凑 | h 52px、padding `0 16px` | 64px（= 52 + 12 margin） | 主壳 `AppHeader` h 52、`content-area` top 52、padding-top 12px ✓ |
| 概览 390 移动 | h 98.7px（两行标题+副标题，`min-height` 52px 未触发）、padding `8px`、margin `0 -8px` | 110.7px（= 98.7 + 12 margin） | `--shell-gutter: 8px` 由 shell 注入、页头正确消费 ✓ |

桌面端内容面板底边与视口底边对齐（`bodyBottom: 900` = shell 高度 900），与改动前一致——原先 shell 底部 padding 本就是 0。

### 9.3 本批遗留（供下一批参考）

1. **`.j-page-body` 的移动端内边距失效**：`JPageShell.vue` 模板里 `:style="{ padding: panelPadding }"` 是 inline style，优先级高于媒体查询里的 `.j-page-body { padding: 12px }`，实测 390px 视口下仍是 `20px 24px`。这属于既有缺陷（非本批引入），在 390px 宽度下左右各占 24px，内容仅剩 326px。建议下一批把 `panelPadding` 也改成令牌驱动（如移动端覆盖 `--layout-panel-padding`），不要用媒体查询硬碰 inline style。
2. **P0-3 仍在**：移动端 TabBar 7 项 × 64px = 448px > 390px。
3. **方案 B 未评估**：P0-1 采用的是「保留两套、对齐数值」的方案 A。若后续认为「独立页不应有面包屑条」，方案 B（概览/回收站改用 `AppHeader` + 面包屑）仍可把 `JPageHeader` 降级为纯 actions 行——本批的令牌化（`--header-height` / `--shell-gutter`）已为此打好基础，切换成本很低。

## 十、第三批实施与验收（2026-10-16 追加）

本批范围：**P1-1 ~ P1-5**（模块间视觉归位），以及实施过程中实测暴露出的一个**虚拟滚动缺陷**（见 10.2）。共改动 11 个组件文件，`npm run build` exit 0（`✓ built in 6.43s`，PWA precache 181 entries）。

### 10.1 P1-1 ~ P1-5：把「列表行」的语言统一到「卡片」的语言

这一批的共同判断是：**列表行与卡片不是两套设计，而是同一套设计在两种排布下的呈现**。此前它们被写成了两套，本批把列表行向卡片看齐。

**P1-1 + P1-3（一并解决）— hover 语义拆分**

原列表行 hover 用 `inset 3px 0 0 var(--brand)`，与侧栏 `NavItem.vue:64` 表示「当前所在位置」的持久符号**共用同一个视觉符号**。hover 是瞬时悬停、激活是持久状态，共用会让鼠标扫过的行读起来像「已选中」；且 `.file-item` / `.vault-item` 本身已有 1px 边框，叠加后成「灰边 + 粉条」双线。

改为与卡片同源的「边框转品牌色 + 浮起阴影」：

| 文件 | 位置 | 改动 |
|---|---|---|
| `jnclub-web/src/modules/bookmark/components/CollectionRow.vue` | `:173-186` | `inset 3px 0 0 var(--brand)` → `border-color: var(--brand-text); box-shadow: var(--shadow-card-hover);` |
| `jnclub-web/src/modules/bookmark/components/NoteRow.vue` | `:203-215` | 同上 |
| `jnclub-web/src/modules/bookmark/components/VaultView.vue` | `:824-828` | 同上 |
| `jnclub-web/src/modules/bookmark/views/RecycleView.vue` | `:440` | `.recycle-item:hover` 补 `box-shadow: var(--shadow-card-hover)` |

这与 `jnclub-web/src/modules/bookmark/components/DiskView.vue:695` 原有的 `.file-item:hover` 语言一致——**云盘此前是唯一写对的地方，本批把其余四处向它对齐**。`:active` 保留 `--brand-soft` 底 + 品牌边框（按下才是「响应」）。

**P1-2 — 列表行的「档位」统一**

原状态：收藏/便签的行是「无底无框、间距 2px」，云盘/密码库/回收站是「有底有框、间距 8px」。统一到后者：

| 文件 | 位置 | 改动 |
|---|---|---|
| `CollectionRow.vue` | `:167` | `padding` → `12px 14px`；补 `background: var(--bg-card)` + `border: 1px solid var(--border)` |
| `NoteRow.vue` | `:198` | 同上 |
| `DiskView.vue` | `:689` | `padding: 10px 14px` → `12px 14px` |
| `RecycleView.vue` | `:432` | `padding: 10px 14px` → `12px 14px` |
| `CollectionList.vue` | `.list-items` | `gap: 2px` → `8px` |
| `NoteList.vue` | `.list-inner` | `gap: 2px` → `8px` |

**P1-4 — 便签卡片底色归位**

`jnclub-web/src/modules/bookmark/components/NoteCard.vue:189-190` 原为 `background: var(--pink-white)` + `border: 1px solid color-mix(in srgb, var(--pink-peach) 40%, transparent)`，改为 `background: var(--bg-card)` + `border: 1px solid var(--border)`，与 `CollectionCard.vue:201-202` 一致。

依据：模块色 `--module-note` 实际是 **#2F9DF7（蓝）**，粉色是品牌粉的历史遗留，不是便签的模块识别色。模块识别交给图标盒（`.icon-box` 的 `--pink-cherry` 保留不动）。实测改后 `.note-card` 与 `.bookmark-card` 完全同规格。

**P1-5 — 工具栏按钮尺寸**

`jnclub-web/src/modules/bookmark/components/ModuleToolbar.vue` 三处 `.btn-new`（新建收藏 `:32`、新建便签 `:52`、上传文件 `:82`）各补 `size="small"`，与同排的 `.io-export-btn` / `.archived-toggle` 对齐。实测同排按钮均为 28px 高。

**附带补漏**：报告 P1-6 只列了收藏/便签两处标题字重 500，实际还有两处——`DiskView.vue` 的 `.file-name`、`RecycleView.vue` 的 `.item-title`，本批一并改为 600。

### 10.2 实施中发现并修复的缺陷：虚拟滚动 wrapper 占位从未生效

**这是本批最重要的发现，与视觉评审本身无关，但影响列表/网格的滚动正确性。**

现象：给列表行加 `margin-bottom` 以统一间距后，复验虚拟滚动，发现虚拟容器的 wrapper 元素**根本没有 `style` 属性**——而 `useVirtualList` 的 `wrapperProps` 必然携带 `style`（内容总高度占位 + `marginTop` 偏移）。

根因：四个组件都写成 `v-bind="virtual.wrapperProps"`。`virtual` 是 `useVirtualList` 返回的**普通对象** `{ list, scrollTo, containerProps, wrapperProps }`，其中 `wrapperProps` 是 `ComputedRef`。Vue 的模板 ref 自动解包**只作用于顶层的 ref**，嵌套对象里的 ref 不会解包——`v-bind` 展开的是 ComputedRef 自身的可枚举属性（`value` / `effect` / `dep`），而非它的 `.value`。结果：wrapper 无高度占位、无 `marginTop` 偏移。

后果：`scrollHeight` 由已渲染内容决定而非全量数据决定，滚动时 `start` 递增 → 前面的行被移除 → 容器变短 → 滚动位置被钳制 → 列表内容不断被替换。**虚拟滚动等于完全失效，退化为「边滚边替换内容」**。

证据链：`wrapStyle: null`（wrapper 无 style 属性）；列表模式实测 `scrollH` 从 1178 涨到 1590、`firstId` 从 9000 变到 9007。

**这是既有缺陷，不是本批引入**——`git diff` 显示四处 `v-bind="virtual.wrapperProps"` 均为原有代码。

修复（4 处，均补注释说明原因）：

| 文件 | 位置 | 改动 |
|---|---|---|
| `jnclub-web/src/modules/bookmark/components/CollectionList.vue` | `:87` | `v-bind="virtual.wrapperProps"` → `v-bind="virtual.wrapperProps.value"` |
| `jnclub-web/src/modules/bookmark/components/CollectionGrid.vue` | `:96` | 同上 |
| `jnclub-web/src/modules/bookmark/components/NoteList.vue` | `:58` | 同上 |
| `jnclub-web/src/modules/bookmark/components/NoteGrid.vue` | `:95` | 同上 |

修复后 `scrollHeight` 立即变为**精确值**：便签列表 120 条 = `120 × 80 = 9600` ✓；便签网格 120 条 = `40 行 × 220 = 8800` ✓；收藏网格 = `8800` ✓。这是 wrapper 高度占位恢复生效的直接证明。

**配套的 `itemHeight` 修正**：虚拟模式下 wrapper 内部**没有 `gap`**（间距必须做进 item 自身），故在 `CollectionList.vue:127` 与 `NoteList.vue:102` 的 `.virtual-item` 上加了 `margin-bottom: 8px`，并把该值计入 `itemHeight`（`NoteList.vue:34` 由 64 调到 80；`CollectionList.vue:55` 保持 64）。两处都写了注释：**改 `.virtual-item` 的间距必须同步改 `itemHeight`**。

### 10.3 实测复核（1440px 亮色，Playwright 读计算样式）

列表行规格——五种行现在同规格：

| 选择器 | 高度 | padding | 边框 | 背景 | 父 gap | 标题字重 |
|---|---|---|---|---|---|---|
| `.collection-row` | 50.9 | `12px 14px` | `1px var(--border)` | `var(--bg-card)` | 8px | 600 |
| `.note-row` | 69.59 | `12px 14px` | 同上 | 同上 | 8px | 600 |
| `.file-item` | 69.59 | `12px 14px` | 同上 | 同上 | 8px | 600 |
| `.vault-item` | 70.59 | `12px 14px` | 同上 | 同上 | 8px | 600 |
| `.recycle-item` | 69.59 | `12px 14px` | `--glass-border` | `--glass-bg-trans` | 8px | 600 |

卡片规格——`.bookmark-card` 与 `.note-card` 改后完全一致：亮色 `bg rgb(255,255,255)` / `border rgb(209,209,214)` / `radius 16px` / `shadow rgba(0,0,0,0.04) 0 1px 2px 0`；暗色 `bg rgb(38,38,42)` / `border rgb(69,69,74)` / `shadow rgba(0,0,0,0.3) 0 1px 2px 0`。

hover 实测（`.collection-row`）：`borderColor rgb(201,37,106)`（= `--brand-text`）、`boxShadow` 为 `--shadow-card-hover` 两层、**背景不变、无 inset 左条**、`transform: none`。

虚拟滚动四种组合（120 条压测数据）：

| 组件 | `itemHeight` | 实际行占位 | 偏差 | 结论 |
|---|---|---|---|---|
| `CollectionList` | 64 | 58.9 | 5.1 | 可忽略 |
| `NoteList` | 80 | 77.59 | 2.41 | 可忽略 |
| `CollectionGrid` | 220 | 164.59 | 55.41 | 可见跳变 |
| `NoteGrid` | 220 | 183.59 | 36.41 | 可见跳变 |

滚动连续性抽验（便签网格，`scrollTop 4575`）：渲染 7 行、覆盖第 58~77 条、行间距恒为 16px、首行顶边 `-108.5`（在容器 `top 286.5` 之上）、末行底边 `1160.7`（在容器 `bottom 946.5` 之下）——**视口上下均被覆盖，无重叠、无空白** ✓。

### 10.4 本批遗留

1. **网格模式 `itemHeight` 虚高**（上表后两行）。`220` 是「标题换行 + 标签换行」的保守上界，对无标签/短标签的卡片高出 36~55px。偏差本身不影响功能，但会让**长列表滚动时出现周期性跳变**（每次 `start` 递增，内容整体位移 `itemHeight − 实际占位`）。没有在本批调小，原因是：改小有**重叠**风险（真实数据的标签行数、便签内容长度都可能超出现有 mock 覆盖），而虚高只影响滚动观感——**风险不对称**。若要彻底解决，正确做法是让卡片等高（固定 `grid-auto-rows`）或用 `ResizeObserver` 动态测量，而不是继续猜一个固定值。**建议用真实数据校准后再动**。
2. **P0-3 仍在**：移动端 TabBar 7 项 × 64px = 448px > 390px。
3. **`.j-page-body` 移动端内边距失效**（承 9.3 第 1 条）：仍未处理，属 inline style 与媒体查询的优先级冲突。
4. **虚拟滚动仅在 mock 压测数据下验证过**：120 条这一规模覆盖了 `isVirtual` 阈值（>80）与滚动中段，但未覆盖「标签换行导致行高变化」「便签长内容」等真实边界。

### 10.5 验收截图

| 文件 | 内容 |
|---|---|
| `.ui-review/b3-01-bookmarks-1440.png` | 收藏夹列表（有底有框 + 8px 间距） |
| `.ui-review/b3-02-notes-1440.png` | 便签列表 |
| `.ui-review/b3-03-vault-1440.png` | 密码库列表 |
| `.ui-review/b3-04-recycle-1440.png` | 回收站列表 |
| `.ui-review/b3-v-notes-grid-120.png` | 便签网格 + 120 条虚拟滚动（wrapper 占位修复后的中段） |

### 10.6 数据源说明（供复现）

本轮实测用的 mock 后端是 `/tmp/jnclub-mock/server.mjs`（**未写入仓库**，仅本地验证用），本轮为其补了两处压测数据：

- 收藏夹压测目录 `dir=199`（挂在 `type=1` 下）→ `/api/bookmarks?directoryId=199` 返回 120 条
- 便签压测目录 `dir=299`（挂在 `type=2` 下）→ `/api/notes?directoryId=299` 返回 120 条

访问入口：`http://localhost:5173/jnclub/?module=notes&dir=299`（主壳用 query 形式切模块与目录；`/notes` 这类路径不是列表路由，会 `No match found for location with "/notes"`）。视图切换控件在 `header .view-switcher`，是 `role=radio` 的「卡片」/「极简」两个按钮，默认「极简」。

主题切换必须点 `AppHeader` 里 title 含「暗黑模式 / 白天模式」的按钮——直接 `document.documentElement.setAttribute('data-theme', ...)` 会被 Vue 下一次渲染覆盖。

---

## 十一、第四批实施与验收（2026-10-16 追加）

本批原计划 P1-8 + P1-9。**实施过程中实测推翻了 P1-8 的核心建议**，因此本批结论与第二节原建议不一致，以本章为准。

### 11.1 P1-8：死代码已清，但「提档到 32px」经实测否决

**已做**：删除 `jnclub-web/src/shared/views/DashboardView.vue` 原 `638-698` 行的整套死样式 —— `.stat-card` / `.stat-card:hover` / `.stat-card.stat-warn` / `.stat-text` / `.stat-icon` / `.stat-warn .stat-icon` / `.stat-value` / `.stat-label` / `.stat-warn-dot` / `@keyframes dash-blink`，以及其中 `@media (max-width:767px)` 的第二套移动端覆盖（共 61 行）。删除后 `grep` 该文件对 `stat-card|stat-value|stat-icon|stat-label|stat-text|stat-warn|dash-blink` **零命中**，页面 DOM 查询这些选择器同样零命中。

**未做，且不建议做**：把 `.j-stat-value` 由 `var(--fs-5xl)`(24px) 提到 `var(--fs-7xl)`(32px)。原报告 P1-8 的依据是「死代码代表设计意图，实渲染比意图小一档」。实测证明**这个意图本身在 6 列布局下不成立**：

1440px 视口下 shell 宽 1200px，`.stat-grid` 为 `repeat(6, minmax(0,1fr))`，单卡 **169.3px**；扣掉 `padding 16px 18px`×2 + icon 38px + `gap 14px` 后，`.j-stat-text` 可用宽度仅 **81.3px**。实测各档位在不同位数下的表现（`clientWidth` vs `scrollWidth`，`.j-stat-value` 为 `white-space:nowrap`）：

| 规格 | 4 位 `1234` | 5 位 `12345` | 6 位 `123456` |
|---|---|---|---|
| 现状 24px / pad 16px 18px / icon 38 | 52 / 52 ✓ | 67 / 67 ✓ | 79 / **82 溢出** |
| 报告建议 32px / pad 20px 22px / icon 38 | 67 / 67 ✓ | 71 / **86 溢出** | 71 / **106 溢出** |
| 死代码原规格 32px / pad 24px / gap 18 / icon 44 | 57 / **67 溢出** | 57 / **86 溢出** | 57 / **106 溢出** |
| 折中 28px / pad 20px 22px / icon 38 | 60 / 60 ✓ | 71 / **77 溢出** | 71 / **94 溢出** |

结论有三层：

1. **死代码那套规格连 4 位数字都放不下**（57 / 67 溢出）。它是「6 列网格 + 24px padding + 44px icon + 32px 数字」四者叠加的产物，从未在真实布局下验证过 —— 这恰恰说明它是**未落地的草稿**，而不是「被遗漏的设计意图」。
2. **报告的折中版（20px 22px + 32px）安全上限只有 4 位**，5 位即溢出。而现状 24px 的安全上限是 5 位。也就是说提档会**用「数字位数余量」换「1.33 倍字号」**。
3. **溢出只在 1440px 出现**：1280px 与 1024px 下 `.stat-grid` 列数变化、单卡变宽（301.3px / 216px），各档位均无溢出。而 1440px 正是本项目评审的基准视口，也是最紧的布局 —— 风险恰好落在最常见的场景上。

`b4-05-stat-overflow-proof.png` 是这一判断的视觉证据：同一行 6 张真实卡片，上排 24px 显示 `12345` 完整落在卡内，下排 32px 的 `12345` 已越出卡片右边界压到相邻卡片。

**保留 24px 的理由**：24px/800 相对标签 12px 已是 2 倍层级差，在 169px 宽的卡片里视觉权重足够；而统计数字是**会增长的量**（收藏数、便签数随使用累积），为字号牺牲位数余量是拿确定的稳健性换不确定的观感。若将来确实需要更强的数字层级，正确做法是 `font-size: clamp()` 或按位数降档，而不是全局提到 32px。

**顺带确认**：`jnclub-web/src/shared/components/ui/JStatCard.vue:97-107` 本身已有完整的移动端档（`flex-direction:column`、`gap:8px`、`min-height:104px`、`padding:14px`、icon 32px、值 `--fs-4xl`），死代码里那段 `@media (max-width:767px)` 是令牌化之前的第二套覆盖，删除无副作用。

### 11.2 P1-9：只采纳其中一条，另两条经复核不采纳

原报告 P1-9 含三条建议，逐条复核结果：

**① 面板内边距统一到 20px —— 部分采纳。** `.greet-card` 由 `18px 22px` 改为 `20px`（`DashboardView.vue:961`，已加注释说明理由）。它与 `.panel` 同为「内容卡片」语义、同为 `--radius-md` 圆角、同为 `--glass-*` 底与 `--shadow-1`，仅内边距差 2px 属无意义漂移。改后实测两者 padding 均为 `20px`（greet-card 高 89.2→93.2px）。

**`.quick-bar` 不跟进 20px**（保持 `12px 14px`）。原报告把它与 `.greet-card`/`.panel` 并列为「面板类」，但实测它是**按钮组容器**：7 个 `small` 按钮（各 28px 高）、无标题、无内容区，与「标题 + 内容」的面板不是同一语义。对比图 `b4-06-quickbar-padding-cmp.png` 显示：`12px 14px` 时高 54px，按钮组与容器边界关系清晰；`20px` 时高 70px，28px 按钮被 20px 留白包围显得孤立，且概览页首屏垂直空间净增 16px。判断依据是**语义分类**而非数值一致性 —— 若按数值强行拉平，得到的是「看起来一样但读起来不对」的一致。

**② 两类标题统一为 13px/600 —— 不采纳。** 原报告称 `.dash-group-title`(12px/700/`--text-3`) 与 `.panel-title`(13px/600/`--text-1`)「字号与字重同时变化，层级对比过强」。逐项实测两者的计算样式与**实际视觉贡献**：

| | 字号 | 字重 | 字距 | 大小写 | 颜色 |
|---|---|---|---|---|---|
| `.dash-group-title` | 12px | 700 | 0.48px | `uppercase` | rgb(110,110,115) |
| `.panel-title` | 13px | 600 | normal | none | rgb(29,29,31) |

用 Range 量真实文本宽度（`getBoundingClientRect` 对块级元素返回容器宽，不可用）得到两项**声明与实际增益的落差**：

- `letter-spacing: 0.48px` 对 4 字分组名「数据总览」的总增宽为 **1.92px**（49.92 → 48.00），即每字 0.48px ≈ 字号的 4%，视觉上几乎不可察觉；
- `text-transform: uppercase` 的增宽为 **0px** —— 当前两个分组名（「数据总览」「最近与快捷」）都是纯中文，**中文字符没有大小写，该声明不产生任何视觉变化**。

因此现状的真实层级区分只来自**字号差 1px、字重差 100、颜色（中灰 vs 近黑）**，其中颜色是唯一显著项 —— 原报告「层级对比过强」的描述与实际相反，现状的对比是**偏弱**的。

不采纳的理由是**语义分类**：`.dash-group-title` 是 overline / eyebrow（分组标签），`.panel-title` 是内容标题，两者是不同角色而非同一角色的两个档位。分组标签应当**小于**其下内容的标题（现状 12 < 13 正确）；统一到 13px/600 会让分组标签与其下的卡片标题**等大**（对比图 `b4-04-titles-compare.png` 第三行即报告建议版，可见「数据总览」被放大到与 `.panel-title` 同级），破坏「标签从属内容」的关系。`uppercase` 作为 overline 模式的声明式写法保留无害 —— 若将来出现含英文/数字的分组名，它会立即生效。

**③ `.panel` 保持 20px** —— 原报告此项与现状一致，无需改动（实测 `.panel` 已是 `padding:20px`、`gap:16px`）。

### 11.3 本批改动清单

| 文件 | 位置 | 改动 |
|---|---|---|
| `jnclub-web/src/shared/views/DashboardView.vue` | 原 `638-698` | 删除 `.stat-card` 系列死样式与 `dash-blink` 关键帧（61 行） |
| `jnclub-web/src/shared/views/DashboardView.vue` | `:961`（原 1022） | `.greet-card` `padding:18px 22px` → `20px` |

未触碰 `JStatCard.vue`（字号与尺寸均保持）。

### 11.4 验证

- **构建**：`cd jnclub-web && npm run build` → exit 0，`✓ built`，PWA precache 181 entries。
- **运行时实测（1440px 亮色，Playwright 读计算样式）**：`.greet-card` padding `20px`、`.panel` padding `20px`、`.quick-bar` padding `12px 14px`（保持）；死代码选择器 `.stat-card`/`.stat-text`/`.stat-icon`/`.stat-value`/`.stat-label`/`.stat-warn-dot` 在 DOM 中**零命中**；`.j-stat-card` 仍为 6 张、`.j-stat-value` 仍为 24px。
- **暗色回归（1440px）**：`data-theme=dark` 下 `.greet-card` 与 `.panel` padding 均 `20px`，greet-card 玻璃底 `rgba(38,38,42,0.72)`、高 93.2px（与亮色同高），统计卡 6 张 / 值 24px。
- **移动端回归（390×844）**：`.greet-card` padding `20px`、高 128.4px（内容换行）、`scrollWidth` 无溢出；文档无横向滚动；统计卡走自身移动端档（padding `14px`、值 `--fs-4xl`(22px)、单列 318px），死代码中那段移动端覆盖删除后行为不变。
- **未回归**：`.stat-grid` 的响应式档位（6 列 → 移动端 2 列 → 480px 单列）未改动，`b4-02-overview-after-1440.png` 与 `b4-01-overview-before-1440.png` 对比可见统计行、问候卡、面板三者相对关系不变。

### 11.5 本批遗留

1. **P1-8 的「数值层级」问题实际未解决**，只是确认了原建议不可行。若确有提升数值权重的需求，建议方向是 `clamp()` 自适应或按位数降档，需产品决策。
2. **P0-3（移动端 TabBar 溢出）仍在**，且已跨三个批次未处理。
3. **`.j-page-body` 移动端内边距失效**（承 9.3 第 1 条、10.4 第 3 条）仍未处理。
4. **网格模式 `itemHeight` 虚高**（承 10.4 第 1 条）仍未处理，需真实数据校准。
5. **P1-10、P1-11 未实施**（搜索抽屉 chip 规格、滚动容器）。
6. **`.dash-group-title` 的 `uppercase` 声明与中文文本不符**（本批实测发现）：声明存在但对纯中文分组名零效果，字距 0.48px 亦仅 1.92px/4 字。若希望分组标签有更明确的 overline 质感，可考虑把 `letter-spacing` 提到 `0.08em` 量级（约 1px/字）或改用更小的字号档；属 P2 级细节，本批未动。

### 11.6 验收截图

| 文件 | 内容 |
|---|---|
| `.ui-review/b4-01-overview-before-1440.png` | 概览页改前（含死代码未删状态） |
| `.ui-review/b4-02-overview-after-1440.png` | 概览页改后（greet-card 20px、死代码已清） |
| `.ui-review/b4-03-stat-grid-zoom.png` | 统计行 6 卡细节 |
| `.ui-review/b4-04-titles-compare.png` | 三类标题并排：现状 `.dash-group-title`(12px/700) / 报告建议版(13px/600) / 对照 `.panel-title`(13px/600 深色) |
| `.ui-review/b4-05-stat-overflow-proof.png` | **32px 提档的溢出证据**：同值 12345，上排 24px 完整、下排 32px 越界 |
| `.ui-review/b4-06-quickbar-padding-cmp.png` | `.quick-bar` 12px 14px vs 20px 对比 |
| `.ui-review/b4-07-overview-mobile-390.png` | 概览页 390×844（greet-card 20px、无横向溢出、统计卡走移动端档 14px/22px/单列） |
| `.ui-review/b4-08-overview-dark-1440.png` | 概览页暗色 1440×900（greet-card 与 panel 均 20px，玻璃底 rgba(38,38,42,0.72)） |

---

## 十二、第五批实施与验收（2026-10-16 追加）

本批处理**最后一项 P0**（P0-3 移动端 TabBar 溢出）与 IA-3（目录 chips 冗余），共改 2 个文件。

### 12.1 P0-3：TabBar 固定宽改自适应，并补标签截断兜底

**改动**（`jnclub-web/src/shared/layout/MobileTabBar.vue`）：

```css
/* .mobile-tabbar：justify-content 保持 flex-start，仅加注释说明它已不参与分配 */
.tab-item {
  flex: 1 1 0;      /* 原 flex: 0 0 64px */
  min-width: 0;
  ...
}
.tab-label {
  font-size: var(--fs-2xs);
  line-height: 1.2;
  white-space: nowrap;    /* 新增三行 */
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
```

**对原报告建议的两处修正**：

1. **`justify-content` 不改为 `stretch`**。`stretch` 在 flex 容器（非 grid）中与 `flex-start` 等效，对 `flex:1` 的子项零影响；写成 `stretch` 会让后来者误以为它在起作用，掩盖「是 `flex:1` 在平分」这一真实机制。保留 `flex-start` 并写明注释。
2. **补充 `.tab-label` 截断**。只改 `flex` 在极窄屏仍可能出问题：7 项平分后每项仅 `viewport/7`，320px 下为 45.7px，而「收藏夹/密码库/回收站」三字标签实测宽 30px——尚有余量，但若未来 `MOBILE_KEYS` 增项或用户自定义更多项，标签会换行并把 22px 图标挤出 56px 高度。加 `nowrap + ellipsis` 后失败模式从「布局错乱」降级为「文字截断」，且 `overflow-x:auto` 仍在，超窄屏仍可横滑。

**实测（Playwright 读计算样式，7 项全量）**：

| 视口 | nav clientW / scrollW | 溢出 | 单项宽 | 标签截断 | 项高 |
|---|---|---|---|---|---|
| 320 | 320 / 320 | ✗ | 45.7 | 无 | 56 |
| 360 | 360 / 360 | ✗ | 51.4 | 无 | 56 |
| 390 | 390 / **390**（原 448） | ✗ | 55.7 | 无 | 56 |
| 430 | 430 / 430 | ✗ | 61.4 | 无 | 56 |
| 767 | 767 / 767 | ✗ | 109.6 | 无 | 56 |

- 7 项标签实测纯文字宽（canvas 量取，`--fs-2xs`=10px）：概览/便签/云盘/更多 各 20.0px，收藏夹/密码库/回收站 各 30.0px——320px 下每项 45.7px 仍容纳最宽 30px，**无需截断**，截断规则是纯兜底。
- 暗色 390px 回归：`scrollW 390 = clientW`、单项 55.7px、导航底 `rgba(38,38,42,0.72)`、激活色 `rgb(255,143,171)`、非激活 `rgb(142,142,147)`。
- 「更多」抽屉功能未受影响：点击后 `.more-item` 渲染 2 项（音乐、下载中心），`.tab-active` 正确落到「更多」。
- 全视口 `document.documentElement.scrollWidth` 无横向溢出。

### 12.2 IA-3：桌面端隐藏目录 chip-bar

**改动**（`jnclub-web/src/modules/bookmark/views/Home.vue`）：

```css
/* 基础规则：桌面端隐藏 */
.chip-bar {
  display: none;      /* 新增 */
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
}

@media (max-width: 767px) {
  .chip-bar {
    display: flex;    /* 新增：移动端恢复 */
    margin-bottom: 12px;
  }
}
```

**为什么用 CSS 断点而不是 `v-if`**：移动端本就有一组 `.chip-bar` 的横滑样式（`flex-wrap:nowrap; overflow-x:auto`）挂在同一元素上，用 `v-if` 需要引入视口判断的 JS 状态并与该媒体查询保持同步；CSS 断点方案只有一处真相，且与 `.folder-column` 的显隐断点（同为 767px）天然对齐——**两者恰好在同一断点互为反相**，这正是 IA-3 想表达的关系。

**实测**：

| 视口 | `.chip-bar` display | `.folder-column` display | 说明 |
|---|---|---|---|
| 320 / 360 / 390 / 430 / 767 | flex | none | 移动端：chip 是选目录的唯一入口 |
| 768 / 1440 | **none** | flex | 桌面端：目录树已表达同一层级，消除冗余 |

- 移动端 chip 交互未受影响：点击「设计灵感」后 `.chip-active` 由「技术」变为「设计灵感」，URL 变为 `?module=bookmarks&dir=2`。
- 桌面端改后 `.collection-column` 内首元素仍为 `.module-toolbar`，工具栏与列表的间距未变（chip-bar 消失后由 `.module-toolbar` 的 `margin-bottom:14px` 接续，未产生双倍留白）。

### 12.3 验证

- **构建**：`cd jnclub-web && npm run build` → exit 0，`✓ built in 6.08s`，PWA precache 181 entries。
- **视口矩阵**：320 / 360 / 390 / 430 / 767 / 768 / 1440 七档全部实测，TabBar 与 chip-bar 的显隐、宽度、溢出均符合预期。
- **暗色回归**：390px 暗色下 TabBar 无溢出、配色取自令牌（未硬编码）。

### 12.4 本批遗留

1. ~~**`.j-page-body` 移动端内边距失效**（承 9.3-1、10.4-3、11.5-3）仍未处理。~~ → **已于第六批修复**（见 13.2）。
2. **网格模式 `itemHeight` 虚高 32%**（承 10.4-1、11.5-4）仍未处理，需真实多标签数据校准。
3. **P1-8 数值层级**（承 11.5-1）仍未解决，需产品决策。
4. ~~**P1-10、P1-11 未实施**（搜索抽屉 chip 规格、滚动容器）。~~ → **P1-11 已于第六批实施**（见 13.1）；P1-10 仍未实施。
5. **P2-3 ~ P2-8、IA-1、IA-2、IA-4、IA-5 未实施**。

### 12.5 验收截图

| 文件 | 内容 |
|---|---|
| `.ui-review/b5-01-tabbar-before-390.png` | **改前**：390px 下 TabBar 溢出，第 7 项「更多」被裁在视口外 |
| `.ui-review/b5-02-tabbar-after-390.png` | 改后 390×844 全屏 |
| `.ui-review/b5-03-tabbar-zoom-after.png` | 改后 TabBar 特写（390×56，7 项平分，无裁切） |
| `.ui-review/b5-04-tabbar-320.png` | 320px 极窄视口全屏 |
| `.ui-review/b5-05-tabbar-zoom-320.png` | 320px TabBar 特写（每项 45.7px，标签仍完整） |
| `.ui-review/b5-06-desktop-chipbar-hidden-1440.png` | 桌面端 1440×900：chip-bar 已隐藏，目录树独占目录导航 |
| `.ui-review/b5-07-tabbar-dark-390.png` | 暗色 390px TabBar 特写 |
| `.ui-review/b5-08-more-drawer-390.png` | 「更多」抽屉仍可用（音乐、下载中心） |



---

## 十三、第六批实施与验收（2026-10-16 追加）

本批处理两项：**P1-11（滚动容器统一）** 与 **`.j-page-body` 移动端内边距失效**（承 9.3-1、10.4-3、11.5-3、12.4-1 四次遗留）。两者最终落在同一处结构上：主壳内容区的**高度链路**。

### 13.1 P1-11：根因不是「滚动容器选错」，而是虚拟列表在猜高度

**原报告的判断需要修正。** 原报告把问题描述为「收藏夹面板内滚、概览页整页滚，两种容器不一致」，建议把 `.collection-column` 改为不滚动、让 `.content-area` 成为滚动容器。

勘察后发现的真实现象是**双滚动条**：收藏夹页同时存在 `.collection-column`（面板）与 `.virtual-scroll`（虚拟列表）两个可滚动元素。成因是四个虚拟滚动组件把高度写成：

```css
.virtual-scroll { max-height: calc(100vh - 240px); overflow-y: auto; }
```

`240px` 是**猜出来的 chrome 高度**（顶栏 + 工具栏 + 目录条 + 标签条 + 内边距）。实测这个猜测与实际不符：

| 场景 | 实际 chrome 高度 | `calc(100vh - 240px)` 给出的高度 | 偏差 |
|---|---|---|---|
| 桌面 1440×900 舒适 | 282px | 660px | **+41px**（列表比可用空间高 41px → 外层也溢出 → 第二个滚动条） |
| 桌面 1440×900 紧凑 | 262px | 660px | +19px |
| 移动 390×844 | 253.5px | 604px | +28.5px |

偏差为正时虚拟列表**高于**可用空间，`.collection-column` 于是也被撑出滚动条 —— 这就是双滚动条的来源。

**中途试过并撤销的方案**：引入 `--list-chrome-h` 变量（桌面 282px / 紧凑 262px），把 `calc(100vh - 240px)` 改成 `calc(100vh - var(--list-chrome-h, 282px))`。桌面端实测确实修好（外层溢出量 41→0），但这是**治标**：它把「猜一个常数」换成「猜一个会随视口、标签换行、模块而变的变量」，任何新增一条工具栏都会再次失配。**该方案已撤销，代码中无残留。**

**最终方案：让高度自上而下传递，而不是从视口倒推。** 完整链路：

```
.collection-column   display:flex; flex-direction:column; overflow-y:auto   ← 唯一的滚动容器
  └ .module-toolbar / .chip-bar / .tag-bar   flex-shrink: 0                 ← 固定 chrome，不参与压缩
  └ .spin-area        flex:1 1 auto; min-height:0; display:flex; column     ← 只传递高度，不设 overflow
      └ .module-content   flex:1 1 auto; min-height:0; display:flex; column
          └ .collection-list / .collection-grid / .note-list / .note-grid   flex:1（Home.vue 用 :deep() 够到）
              └ .n-spin-container / .n-spin-content                          flex:1
                  └ .virtual-scroll   flex:1 1 auto; min-height:0; overflow-y:auto
```

四个虚拟组件的 `.virtual-scroll` 均去掉 `max-height: calc(100vh - 240px)`，改为 `flex: 1 1 auto; min-height: 0;`（`overflow-y: auto` 保留 —— 它才是滚动元素）。

**为什么滚动容器仍是 `.collection-column`，而不是原建议的 `.content-area`**：`.content-area` 是主壳的滚动容器，若让它滚，虚拟列表就失去确定高度（内容全部撑开），虚拟滚动直接失效、退化成「外层滚 + 全量渲染 120 条 DOM」。实测验证过这条路，已否决。**虚拟列表必须有一个确定高度的祖先**，这是虚拟滚动的前提，不是可选的风格取舍。

**关键约束（改这块必须知道）**：
- 链路上**任一环**缺 `flex:1` 或 `min-height:0`，高度传递就断掉，虚拟列表撑开、退化成不虚拟。
- `.spin-area` **不能设 `overflow`**。一旦设 `overflow-y:auto`，滚动条会内缩到内容区右边缘（right 1391），与概览/回收站的 `.j-page-body`（滚动条贴玻璃面板边框内侧，right 1416）不一致。不设时，云盘/密码库（无虚拟滚动）内容超出会被 flex 压缩、内容溢出后由 `.collection-column` 滚，滚动条位置正好落在面板边框内侧。
- 子组件样式在各自 `scoped` 块里，Home.vue 必须用 `:deep()` 才够得着。
- 云盘 / 密码库（`.disk-view` / `.vault-view`）**不在此链路内**：它们无虚拟滚动，给它们加 `flex:1` 只会无谓改变内部布局。

### 13.2 `.j-page-body` 移动端内边距失效（跨四批的遗留）

**根因**：`JPageShell.vue:31` 用 `:style="{ maxWidth, padding: panelPadding }"` 把 `panelPadding` 写成 **inline style**。inline style 优先级高于媒体查询，于是 `:79` 的 `@media (max-width:767px) .j-page-body { padding: 12px }` **静默失效** —— 390px 下实际仍是 `20px 24px`，内容可用宽只剩 **324px**（390 − 24×2 − 面板自身内边距）。

**修复**：把 inline 的 `padding` 改为**注入 CSS 变量**，覆盖权交还给 CSS 层叠：

```diff
-      :style="{ maxWidth, padding: panelPadding }"
+      :style="{ maxWidth, '--page-panel-padding': panelPadding }"
```
```css
.j-page-body { padding: var(--page-panel-padding, var(--layout-panel-padding)); }
```

值仍来自组件 API（`--layout-panel-padding`、紧凑模式的 `14px 16px` 都能透传），只是不再以 inline 形式写死。改后 390px 下 `padding: 12px` 生效。

### 13.3 实施中引入并修复的回归：移动端目录条被 flex 压扁

`.collection-column` 改为 flex 列后，子项默认 `flex-shrink: 1`。实测 390×844 下 **`.chip-bar` 被压到 1.6px**，目录名「技术」完全不可见 —— 窄屏垂直空间不足时，flex 把固定 chrome 也一起压缩了。

修复：给固定 chrome 显式钉住（`Home.vue:705-708`）：

```css
.collection-column > .module-toolbar,
.collection-column > .chip-bar,
.collection-column > .tag-bar { flex-shrink: 0; }
```

只有 `.spin-area` 允许让出空间。改后移动端 `.chip-bar` h **28.5px**、chip 文字「技术」可见、`flexShrink: 0`；桌面端 chip-bar 仍 `display:none`（h 0）✓。

> 这条回归说明：把一个容器从「块级 + 溢出滚动」改成「flex 列」时，**所有原本靠自然高度存在的固定 chrome 都要显式声明 `flex-shrink: 0`**，否则窄屏下会被静默压缩。

### 13.4 验证

**双滚动条已消除**（`scrollHeight > clientHeight` 且 `overflow-y: auto|scroll` 的元素集合）：

| 页面 / 条件 | 改前滚动元素 | 改后滚动元素 | 滚动条 right |
|---|---|---|---|
| 收藏夹 虚拟 1440 | `.collection-column` + `.virtual-scroll`（2 个） | **仅 `.virtual-scroll`** | 1391 |
| 收藏夹 紧凑 1440 | 2 个 | 仅 `.virtual-scroll` | 1407 |
| 收藏夹 移动 390 | 2 个 | 仅 `.virtual-scroll` | 353 |
| 云盘（注入 3000px 撑高） | — | **仅 `.collection-column`** | 1416 |
| 密码库 移动 390 | — | 仅 `.collection-column` | 378 |
| 概览 1440 | `.j-page-body` | 仅 `.j-page-body` | **1416** |

- 收藏夹 1440：`.collection-column` h 800、scrollH 798 → **不滚**（面板边框固定于视口）；高度全部由 `.virtual-scroll` 承担。
- 云盘注入撑高后：`.spin-area` `overflowY: visible`、scrollH 3000 > clientH 716 但**不产生滚动条**，内容溢出到 `.collection-column` 由它滚，滚动条 right **1416 = 面板边框内侧**，与概览的 `.j-page-body` **完全一致** ✓ —— P1-11 的观感目标（面板固定、滚动条贴边框内侧）达成。
- 移动端各页滚动元素：收藏夹/便签 → `.virtual-scroll`；云盘 → 无滚动（内容不超出）；密码库 → `.collection-column`（right 378）；概览 → `.j-page-body`（right 382）；回收站 → 无滚动。（378 vs 382 是主壳 `content-area` 内缩 12px 与页壳 `--shell-gutter` 内缩 8px 的既有层级差，不属本批范围。）
- **移动端虚拟滚动可达末尾**（390×844，滚到底）：渲染 19 项，first「压测条目 102」、last「**压测条目 120**」，`scrollTop 6942.5` / `scrollH 7680`（=120×64，稳定）、`clientH 418`，行间距实测 `[8,8,8,8,8,8]` 恒为 8px、**无重叠**，行高 50.9。✓
- 紧凑模式 1440：`.collection-column` h 808、top 72、`padding: 14px 16px`（紧凑令牌生效）；唯一滚动元素 `.virtual-scroll`（right 1407）✓。
- 移动端 390：`.j-page-body` padding 已为 `12px`（13.2 修复生效）。
- **构建**：`cd jnclub-web && npm run build` → exit 0，`✓ built in 6.08s`，PWA precache 181 entries。

### 13.5 本批遗留

1. **网格模式 `itemHeight` 虚高 32%**（承 10.4-1、11.5-4、12.4-2）仍未处理。`CollectionGrid.vue:67` / `NoteGrid.vue:68` 用 `itemHeight: 220`，实测卡片行高 148.59（+16px 行距 = 164.59），虚高 71.4px，滚动到 400→440px 时内容跳变约 180px。改小有重叠风险（真实数据的标签行数、便签内容长度都可能超出 mock 覆盖），**风险不对称**，故保留。需真实多标签数据校准。
2. **P1-8 数值层级**（承 11.5-1）仍未解决，需产品决策（`clamp()` 或按位数降档）。
3. ~~**P1-10 未实施**（搜索抽屉内四种 chip 规格收敛）~~ → **已于第七批实施**（见第十四章）。
4. **P2-3 ~ P2-8、IA-1、IA-2、IA-4、IA-5 未实施**。
5. **虚拟滚动只经 mock 验证**：本批的高度链路结论全部基于 `/tmp/jnclub-mock` 的 120 条压测数据（dir=199 / dir=299），未在真实数据量与真实网络延迟下验证。
6. **紧凑 + 移动端组合未逐项覆盖**：紧凑模式实测在 1440 桌面完成；紧凑 + 390 移动端的 `content-area` padding（紧凑模式移动端为 `padding-top/bottom: 8px`、左右 12px，而 `.j-page-body` 移动端是 `12px`）存在同类错位，属 P0-1 家族，本批未处理。

### 13.6 验收截图

| 文件 | 内容 |
|---|---|
| `.ui-review/b6-01-bm-virtual-light-1440.png` | 收藏夹虚拟列表（120 条）改后 1440×900 亮色，单一滚动条贴面板内侧 |
| `.ui-review/b6-02-files-light-1440.png` | 云盘 1440×900 亮色（非虚拟分支回归） |
| `.ui-review/b6-03-overview-light-1440.png` | 概览 1440×900 亮色（对照：`.j-page-body` 滚动，滚动条位置一致） |
| `.ui-review/b6-04-bm-mobile-390.png` | 收藏夹 390×844（chip-bar 恢复可见、内边距 12px 生效） |
| `.ui-review/b6-05-overview-mobile-390.png` | 概览 390×844 |

---

## 十四、第七批实施与验收（2026-10-16 追加）

本批为 **P1-10**（搜索抽屉内 chip 规格收敛）。改动集中在 `jnclub-web/src/modules/bookmark/components/SearchDrawer.vue` 一个文件。构建通过（`✓ built in 7.70s`，PWA precache 181 entries）。

### 14.1 实施：抽屉内 chip 由四级收敛为两级

| 元素 | 位置 | 改前 | 改后 |
|---|---|---|---|
| `.type-filter-chip` | `SearchDrawer.vue:936` | `3px 10px` / `--fs-xs`(11px) | `4px 12px` / `--fs-sm`(12px) |
| `.syntax-chip` | `SearchDrawer.vue:963` | `3px 10px` / `--fs-xs` | `4px 12px` / `--fs-sm` |
| `.history-chip` | `SearchDrawer.vue:896` | `4px 12px` / `--fs-sm` | 不变（本档基准） |
| （对照）`.j-filter-chip` | `shared/components/ui/JFilterBar.vue:63` | `5px 14px` / `--fs-sm` | 不变（一级档） |
| （对照）`.chip` | `assets/main.css:160` | `5px 14px` / `--fs-sm` | 不变（一级档） |

收敛后的两级档位：

| 档 | 用途 | padding | 字号 | 圆角 | 实测高 |
|---|---|---|---|---|---|
| 一级「主筛选」 | 目录导航 `.chip`、标签筛选 `.j-filter-chip` | `5px 14px` | `--fs-sm` 12px | `--radius-pill` | **28.5px** |
| 二级「次级筛选」 | 抽屉内三个 chip | `4px 12px` | `--fs-sm` 12px | `--radius-pill` | **26.5px** |

改前 `.type-filter-chip` / `.syntax-chip` 的 11px 是本项目留给**元信息**（如 `.item-meta`）的字号，用在可点击控件上偏小；且与同屏的 `.history-chip` 并排时高度差 2px、字号差 1px，读起来像两个不相干的控件。

**色相保留三档语义**（几何统一、色相区分）：

- **中性档** —— `.type-filter-chip` / `.history-chip`：`--glass-chip-border` / `--glass-chip-bg` / `--glass-chip-text`
- **品牌档** —— `.syntax-chip`：`--brand` 描边 + `--brand-soft` 底 + `--brand-text` 字。语义是「这是你已输入并解析成功的条件，点 × 移除」
- **激活档** —— `.type-filter-chip.active`：`--brand-text` 描边 + `--brand-soft` 底

### 14.2 本批暴露的既有功能缺陷：搜索结果区永不渲染

> **这是本批最重要的发现，也是继 `.title-link` scoped 失效（8.1）、`v-bind="wrapperProps"` 失效（10.2）之后的第三处既有缺陷，且严重程度最高 —— 它是功能级的，不是视觉级的。**

**现象**（1440×900 亮色，mock 后端）：

输入 `vue` 后，`/api/search?keyword=vue&limit=20` 确实发出且返回 2 条书签（`curl` 直连 mock 确认返回 `data.bookmarks` 两条：「Vue 3 官方文档」「Pinia 状态管理」），但抽屉渲染的是：

```
命令面板 | Ctrl / ⌘ + K | 没有找到相关内容 | 换个关键词试试
```

DOM 探针结果：`hasSearchResults: false`、`hasNoResult: true`、`hasTypeFilter: false`、`hasSyntaxChip: false`。

**根因**：`SearchDrawer.vue` 的 `doSearch`（`:157-178`）在成功分支里**只设了 `searched.value = true`，从未把响应写回 `result`**：

```js
const res = await axios.get('/api/search', { params: { keyword: kw, limit: 20 }, signal: ctl.signal })
if (res.data.code === 200) {
  searched.value = true        // ← 只有这一句
  activeIndex.value = -1
  pushHistory(kw)
}
```

全文件对 `result` 的引用**只有读、没有写**：`result.value.bookmarks.length`（`:196`）、`result.value.parsed`（`:247`）、`for (const b of result.value.bookmarks)`（`:353`）等；无任何 `result.value = `、`result.value.X = `、`Object.assign(result` 或 `.push/.splice` 写入。故 `result` 恒为初始化时的空对象（`:34-41`）。

**后果链**：

- `total()`（`:196-197`）恒为 `0`
- `.search-results`（`:603`，`v-else`）永不渲染
- `.no-result`（`:592`，`v-else-if="searched && total() === 0"`）**恒显示「没有找到相关内容」**
- `.type-filter-chip`（`:493`，`v-if="searched && total() > 0"`）**永不渲染**
- `.syntax-chip`（`:502`，依赖 `syntaxChips` → `result.value.parsed`，`:246-247`）**永不渲染**

**溯源**（`git log` 逐提交核对该文件的 `result.value = ` 赋值数）：

| 提交 | 赋值数 |
|---|---|
| `331fd3b` | 3 处 |
| `fe6c9f3` / `6107c0a` | 4 处 |
| `18e4fb8`（M1 质量地基） | **0 处** |
| `63d677a` / `f7ddd44` / `51859cf` | 0 处（现状） |

**回归发生在 `7c7f4f9 refactor!: 移除待办/日历/WebDAV 三个模块`**：该提交用批量脚本删除 `todos` 字段时，把 4 行

```js
result.value = { bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [], todos: [] }
```

**整行删除**，而不是只删 `todos: []` 字段。被误删的 4 处：

1. `watch(() => props.show)` 打开抽屉时的重置
2. `doSearch` 空关键词时的重置
3. **`doSearch` 成功分支的 `result.value = res.data.data || { ... }`** ← 致命
4. `removeSyntax` 中清空时的重置

**修复建议（一行，本批未实施）**：在第 3 处位置恢复赋值 ——

```js
if (res.data.code === 200) {
  result.value = res.data.data || { bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] }
  searched.value = true
  activeIndex.value = -1
  pushHistory(kw)
}
```

**为何当时未实施**：它超出「UI 视觉收敛」的任务范围，属功能修复，需用户确认。**但它会让 P1-10 的两个目标元素恢复可见**，故建议优先处理。

> **已于第八批实施**（用户确认后），见第十五章。本章行号以第七批当时的文件为准；第八批新增 `emptyResult` 工厂与 `visibleTotal` 计算属性后，相关位置整体后移约 23 行。

### 14.3 验证

**CSS 规则层**（从 dev 环境 `document.styleSheets` 提取；因两个目标元素不渲染，规则层是唯一能直接观测它们的途径）：

| 选择器 | padding | 字号 | 圆角 |
|---|---|---|---|
| `.chip`（main.css） | `5px 14px` | `--fs-sm` | `--radius-pill` |
| `.j-filter-chip[data-v-41c80ce1]` | `5px 14px` | `--fs-sm` | `--radius-pill` |
| `.history-chip[data-v-fa02643a]` | `4px 12px` | `--fs-sm` | `--radius-pill` |
| `.type-filter-chip[data-v-fa02643a]` | `4px 12px` | `--fs-sm` | `--radius-pill` |
| `.syntax-chip[data-v-fa02643a]` | `4px 12px` | `--fs-sm` | `--radius-pill` |

**DOM 实测**：`.type-filter-chip` / `.syntax-chip` 永不渲染，故改用**同 scope 注入法** —— 从抽屉内真实元素取 `data-v-fa02643a` 属性挂到临时按钮上，再读计算样式，可验证 scoped 规则真实生效：

| 元素 | 亮色 | 暗色 |
|---|---|---|
| `.history-chip`（真实渲染，5 个） | pad `4px 12px` / 12px / pill / h 26.5 / bg `rgba(236,91,142,0.12)` / color `rgb(184,42,99)` / border `rgba(236,91,142,0.28)` | bg `rgba(255,143,171,0.16)` / color `rgb(255,179,198)` / border `rgba(255,143,171,0.32)` |
| `.type-filter-chip` | **与 `.history-chip` 逐项相同** ✓ | 同左 ✓ |
| `.type-filter-chip.active` | bg `rgba(236,91,142,0.08)` / color `rgb(201,37,106)` / border `rgb(201,37,106)` | bg `rgba(255,143,171,0.12)` / color `rgb(255,143,171)` / border `rgb(255,143,171)` |
| `.syntax-chip` | 同 `.type-filter-chip.active` 档 | 同左 |

**一级档对照实测**（收藏夹页 `.tag-bar`，亮色 1440）：9 个 `.j-filter-chip`，padding `5px 14px` / `12px` / pill / 高 **28.5px**。

**构建**：`cd jnclub-web && npm run build` → exit 0，`✓ built in 7.70s`，PWA precache 181 entries。

### 14.4 本批遗留

1. ~~**搜索结果区功能缺陷未修**（见 14.2）—— 需用户决策。~~ → **已于第八批修复**，见第十五章。
2. 网格模式 `itemHeight` 虚高 32%（承 10.4-1、11.5-4、12.4-2、13.5-1）。
3. P1-8 数值层级待产品决策（承 11.5-1、13.5-2）。
4. P2-3 ~ P2-8、IA-1、IA-2、IA-4、IA-5 未实施。
5. 虚拟滚动只经 mock 验证（承 13.5-5）。

### 14.5 验收截图

| 文件 | 内容 |
|---|---|
| `.ui-review/b7-01-search-chips-light-1440.png` | 搜索抽屉亮色：`.history-chip` 5 个（`4px 12px` / pill / h 26.5） |
| `.ui-review/b7-02-search-chips-dark-1440.png` | 搜索抽屉暗色：同上 |
| `.ui-review/b7-03-main-filter-chips-light-1440.png` | 一级档对照：收藏夹 `.tag-bar` 的 9 个 `.j-filter-chip`（`5px 14px` / h 28.5） |

> **截图说明**：`.type-filter-chip` 与 `.syntax-chip` 因 14.2 的缺陷无法出现在截图中；本批对其规格的验收依据 14.3 的规则层提取与同-scope 注入实测。

---

## 十五、第八批：搜索结果区功能缺陷修复（2026-10-16 追加）

> **批次性质说明**：前七批都在「视觉收敛」范围内，本批是**唯一一次功能修复**，起因是第七批验收时 P1-10 的两个目标元素始终不渲染，追下去发现整块结果区是死的（14.2）。用户确认后实施。改动文件仅 `jnclub-web/src/modules/bookmark/components/SearchDrawer.vue` 一个。

### 15.1 修复一：恢复被误删的结果写回

按 14.2 的溯源结论，`7c7f4f9` 批量删除 `todos` 字段时整行删掉了 4 处 `result.value = {...}` 赋值。本批**不逐处恢复内联字面量，而是先收敛出单一来源**：

```ts
type SearchResult = {
  bookmarks: any[]
  notes: any[]
  files: any[]
  vault: any[]
  tags: any[]
  tracks: any[]
  parsed?: any
}
const emptyResult = (): SearchResult => ({ bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] })
const result = ref<SearchResult>(emptyResult())
```

**为什么用工厂函数而不是恢复 4 行字面量**：这次的 bug 之所以能发生，正是因为同一个 7 字段字面量在 4 个地方各写了一遍 —— 批量脚本按「含 `todos` 的行」匹配，就必然整行命中。收敛到 `emptyResult()` 后，字段增删只需改一处，且任何一处赋值都不再可能「漏掉某个字段」或「被字段级脚本误删整行」。这是把「已修好」变成「不会再坏」的差别。

四处赋值现状（`grep -n "result.value = "` 确认）：

| 位置 | 用途 | 形态 |
|---|---|---|
| `watch(() => props.show)` 打开抽屉 | 重置上一次结果 | `result.value = emptyResult()` |
| `doSearch` 空关键词 | 重置 | `result.value = emptyResult()` |
| **`doSearch` 成功分支** | **写回响应（致命的那处）** | `result.value = res.data.data \|\| emptyResult()` |
| `removeSyntax` 清空关键词 | 重置 | `result.value = emptyResult()` |

成功分支的写法刻意保留 `|| emptyResult()` 兜底：后端若返回 `data: null`（异常但可能），`result` 不会退化成 `null` 而让下游 `result.value.bookmarks.length` 抛错。

### 15.2 修复二：类型筛选下的空态判断错误（修复过程中发现）

修完 15.1 后，结果区确实渲染了，但**筛选到「本类型为空」时会渲染成一片空白** —— 既无结果、也无「没有找到」提示。这是与 15.1 无关的第二个既有缺陷，被前一个 bug 掩盖了。

**根因**：空态条件用的是 `total()`（各类型条数之和），而分组渲染用的是 `shouldShowGroup()`（按 `typeFilter` 过滤）：

```js
// 模板：两个判断用的不是同一个量
<div v-if="shouldShowGroup('bookmarks') && result.bookmarks.length" class="result-group">   // 按类型过滤
<div v-else-if="searched && total() === 0" class="no-result">                               // 按总数判断
```

搜 `vue` 得 2 条收藏、0 条云盘。此时 `total() === 2 > 0` → 空态分支不成立；而筛到「云盘」时 `shouldShowGroup('bookmarks')` 为 false → 所有分组都被过滤掉。**两条分支同时为假，结果区渲染成空 div**。

**修复**：引入与分组判断同源的可见条数，并把两种空态文案区分开 ——

```ts
const visibleTotal = computed(() => {
  if (typeFilter.value === 'all') return total()
  return result.value[typeFilter.value].length
})
```

```html
<div v-else-if="searched && visibleTotal === 0" class="no-result">
  <JEmptyState
    :message="total() === 0 ? '没有找到相关内容' : '此类型下没有匹配内容'"
    :hint="total() === 0 ? '换个关键词试试' : '切换到其他类型，或换个关键词'"
  />
```

**为什么值得分开两套文案**：两种空态对用户的下一步指引不同。真无结果要改关键词；只是本类型无匹配则应当切类型（上方类型 chip 仍在，是可点的）。原实现连空态都不显示，等于让用户对着空白面板猜。

### 15.3 验证

**这是 P1-10 的两个目标元素首次在真实交互中可见**（第七批只能靠规则层提取与同-scope 注入间接验证，见 14.3）。构建：`cd jnclub-web && npm run build` → exit 0，`✓ built in 7.18s`，PWA precache 181 entries。

| 场景 | 输入 | 实测结果 |
|---|---|---|
| 正常搜索 | `vue` | `.result-group` 1 个（「收藏 2」，含 Vue 3 官方文档 / Pinia 状态管理）、`.no-result` 0 个、`.type-filter-chip` **7 个**全部渲染 |
| 类型筛选 chip 规格 | 同上 | 7 个均 `padding 4px 12px` / `font-size 12px` / `border-radius 999px` / 高 **26.5px** —— 与 14.3 的规则层预测逐项一致 ✓ |
| 语法 chip | `type:bookmarks vue` | `.syntax-chip` 渲染 1 个「类型：收藏 ×」，`4px 12px` / 12px / pill / h **26.5px** ✓ |
| 语法 chip 移除 | 点 × | keyword 由 `type:bookmarks vue` 回退为 `vue`、`.syntax-chip` 清空、结果区正常重渲染「收藏 2」✓ |
| 筛到空类型 | `vue` + 点「云盘」 | `.no-result` 1 个，文案「**此类型下没有匹配内容** / 切换到其他类型，或换个关键词」、`.result-group` 0 个、类型 chip 仍在（可切回）✓ |
| 完全无匹配 | `zzzznotfound` | `.no-result` 1 个，文案「**没有找到相关内容** / 换个关键词试试」、类型 chip 不渲染（`total()===0` 时按设计隐藏）✓ |
| 暗色回归 | `type:bookmarks vue` | `data-theme=dark` 下 `.syntax-chip` 渲染 1 个（`4px 12px` / 12px / pill / h 26.5 / bg `rgba(255,143,171,0.12)` / color `rgb(255,143,171)` / border 同色）；`.type-filter-chip.active` 同档，非激活项 bg `rgba(255,143,171,0.16)` / color `rgb(255,179,198)` / border `rgba(255,143,171,0.32)`；结果区正常渲染「收藏 2」✓ |

**P1-10 至此闭环**：第七批收敛了规格但无法在真实交互中验收；本批修复后补上了真实 DOM 实测，两个目标元素（`.type-filter-chip`、`.syntax-chip`）的 `4px 12px` / 12px / pill / h 26.5 得到直接确认。

### 15.4 本批遗留

1. **搜索结果区的空态分支仍未被系统性回归**：本次只手工验了「全空 / 类型空」两种，未覆盖 `type:` + `date:` + `#tag` 组合语法下的空态与 `parsed` 回显。
2. **同类「字段级批量删除整行」的风险未全站排查**：`7c7f4f9` 是脚本批量改的，本次只顺着 P1-10 的线索发现了这一处。建议对同批次的删除做一次 `grep` 复核（尚未做）。
3. 网格模式 `itemHeight` 虚高 32%（承 10.4-1、11.5-4、12.4-2、13.5-1、14.4-2）。
4. P1-8 数值层级待产品决策（承 11.5-1、13.5-2、14.4-3）。
5. P2-3 ~ P2-8、IA-1、IA-2、IA-4、IA-5 未实施（承 14.4-4）。
6. 虚拟滚动只经 mock 验证（承 13.5-5、14.4-5）。

### 15.5 验收截图

| 文件 | 内容 |
|---|---|
| `.ui-review/b8-01-search-result-fixed-1440.png` | 搜索 `vue`：结果区正常渲染「收藏 2」分组 + 7 个类型筛选 chip（修复前此处恒为「没有找到相关内容」） |
| `.ui-review/b8-02-search-filtered-empty-1440.png` | 筛到「云盘」（本类型无匹配）：显示区分后的空态「此类型下没有匹配内容」，类型 chip 仍在可切回（修复前此处为空白） |
| `.ui-review/b8-03-search-syntax-chip-dark-1440.png` | 暗色：`.syntax-chip`「类型：收藏 ×」与类型筛选 chip 同档渲染（修复前两者均不渲染） |


---

## 十六、`7c7f4f9` 批量删除复核（2026-10-16 追加）

### 16.1 复核缘起与判据

第八批修复的 `SearchDrawer.vue` 缺陷，根因是提交 `7c7f4f9`（`refactor!: 移除待办/日历/WebDAV 三个模块`，2026-10-10，**46 files changed, 174 insertions(+), 4936 deletions(-)**）在批量删除 `todos` 字段时，把**整行**删掉了，而那一行同时承载着 bookmarks/notes/files/vault/tags/tracks 六个仍然需要的字段：

```ts
result.value = res.data.data || { bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [], todos: [] }
//                              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ 这些字段都还活着
```

由此产生一条通用判据 —— 这类「字段级批量删除」的失效模式是：**某一行因一个字段而死，却带着其他仍存活的字段一起消失**。语法完全合法、构建完全通过、静态引用完整，只有运行时才暴露。本次复核即按此判据扫描整个提交。

### 16.2 复核方法与范围

三个方向并行：

1. **被删行逐条核对**：对「部分修改」（非整体删除）的文件，列出所有 `-` 开头且单行含多个字段/赋值的行，逐条判断「删掉它是否带走了别的东西」。
2. **残留引用检查**：被删符号是否仍在别处被引用（前端不参与 Maven 编译，需单独静态检查）。
3. **运行时验证**：遍历 7 条路由，监听 `pageerror` 与 `console.error`，并检查内容区关键选择器是否渲染 —— 这是唯一能抓住「语法完整但语义缺失」的手段。

### 16.3 逐文件核对结论

**前端（8 个部分修改文件）**

| 文件 | 改动 | 判定 |
|---|---|---|
| `SearchDrawer.vue` | 4 处 `result.value = {...todos: []}` 整行删除 | ❌ **误删**（已在第八批修复） |
| `main.css` | 删 `.app-density-compact .todo-item` 一条 | ✅ 干净（该选择器已无消费点） |
| `useSearchActions.ts` | 删 `case 'todo.new'` / `case 'go.todos'` 两个 case | ✅ 干净（每个 case 独立成行） |
| `useRecentItems.ts` | `RecentItemKind` 去掉 `'todo'` + 过滤条件同步 + 注释同步 | ✅ 干净（三处一致；旧 localStorage 里的 `kind:'todo'` 被过滤条件安全剔除） |
| `AppHeader.vue` | 删 `useRouter` import、`const router`、`TODO_REMIND` 映射、`refType === 'todo'` 跳转块 | ✅ 干净（全文已无任何 `router` 引用；`handleNotificationClick` 简化为只标记已读，无空 if 残留） |
| `SideNav.vue` | 删 `if (n === 'webdav') return 'WebDAV'` | ✅ 干净 |
| `navConfig.ts` | `NavKey`/`NAV_META`/`DEFAULT_ORDER` 各删 3 项；`MOBILE_KEYS` 8 项 → 6 项 | ✅ 干净（`normalizeNavKeys` 用 `!!NAV_META[k]` 过滤旧 localStorage 脏 key，安全） |
| `router/index.ts` | 删 3 条路由 + `TITLE_BY_NAME` 对应项 | ✅ 干净 |
| `DashboardView.vue` | 删 `todos`/`alerts` 字段、`alerts` computed、`goAlert`、「今日必办」整块模板与样式 | ✅ 干净（残留 grep：`alerts`/`goAlert`/`today-`/`ListTodo`/`todoCounts` 零命中；`AlertTriangle` 仍被其它告警使用，保留正确） |

**后端**

`mvn -q compile -DskipTests` → **exit 0**，无残留引用。曾重点怀疑 `StatsService.java` 中 `buildDesc(int, String, int, String)` 被删是否会留下孤儿调用方（该方法原被 todo 告警块使用，而回收站告警块位置相邻）——`buildDesc` 全仓 grep **零命中**，确认是私有方法随调用方一起消失。

`SearchService.java` 的删除经逐行核对：`filterByDate` 对 5 个模块、`typeFilter` 对 6 个模块各有一组**平行**的处理行，删掉的正是 `todos` 对应的那两行，其余模块的行完好。`StatsService.buildAlerts` 的签名由 3 参改为 2 参，调用点同步。**不是**「一行多字段」结构，无同类风险。

新建的两个文件 `NotificationTableInit.java`（承接 `t_notification` 建表 + `idx_user_read` 索引）与 `SearchHistoryTableInit.java`（承接 `t_search_history` 建表，索引在建表语句内）均完整。

### 16.4 运行时验证

| 页面 | URL | bodyLen | 内容区高 | 关键选择器命中 |
|---|---|---|---|---|
| 概览 | `/jnclub/overview` | 649 | 820 | 12（`.j-stat-card, .panel`） |
| 收藏夹 | `/?module=bookmarks&dir=11` | 237 | 840 | 4（`.collection-row, .bookmark-card`） |
| 便签 | `/?module=notes&dir=21` | 346 | 840 | 3（`.note-row, .note-card`） |
| 云盘 | `/?module=files&dir=31` | 151 | 840 | 2（`.file-item`） |
| 密码库 | `/?module=vault&dir=41` | 257 | 840 | 5（`.vault-item`） |
| 回收站 | `/jnclub/recycle` | 298 | 820 | 3（`.recycle-item`） |
| 音乐 | `/jnclub/music` | 0 | — | 404 |

**6 个主页面全部正常渲染，无 `PAGEERROR`** —— 即不存在因批量删除导致的运行时崩溃。

### 16.5 结论

**`7c7f4f9` 的字段级误删是孤例** —— 全仓仅 `SearchDrawer.vue` 的 4 处 `result.value` 一处，且已于第八批修复。其余所有删除（前端 7 个文件 + 后端 6 个文件 + 16 个整体删除的文件）都是干净的逐模块平行删除。

同时记录了**两个与本提交无关的既有问题**（超出本次复核范围，未处理）：

1. **音乐页空白**：`/jnclub/music` 渲染出 0 字符 + 一个 404 资源。`Music.vue` 存在且在 `7c7f4f9` 之后（Oct 13）仍被改动过，该提交的改动清单里也没有 `music` 相关文件 —— 非本提交引入。音乐是独立前端（`music-frontend/`），主站入口可能依赖外部服务或后端接口。
2. **PWA Manifest 语法错误**：所有页面均报 `Manifest: Line: 1, column: 1, Syntax error.`，来源是 `jnclub-web/vite.config.ts` 的 PWA 插件 `manifest` 配置（该文件未被 `7c7f4f9` 碰过）。影响面是「添加到主屏幕」的清单不可用，不影响页面本身。

---

## 十七、第九批：P2-3 ~ P2-8 实施与验收（2026-10-16 追加）

### 17.1 P2-3：空态 CTA 圆角 —— 真实缺陷比原报告严重

原报告把 P2-3 描述为「空态两个 CTA 圆角不同」。复核发现真实情况是：

- `JEmptyState.vue` 提供了完整的 `variant="error"` 分支（error 图标 + 「出错了」文案 + `.empty-cta-ghost` 重试按钮 + `@retry` 事件），但**全仓 0 处使用** → `.empty-cta-ghost` 是一条永不渲染的死样式。
- 唯一需要错误态的地方（`Home.vue` 的 `loadError` 分支）用默认 empty 变体凑了一个「重试」按钮。

**改前实测**（`page.route` 拦截 `/api/bookmarks*` 返回 500）：

```
title: 加载失败          btnText: 重试
btnClass: empty-cta glass-pill-btn     hasPlusIcon: true      ← 带「+」号
iconIsBookmark: true                                            ← 书签图标
```

即：**错误态显示的是书签图标 + 带加号的品牌渐变主按钮，按钮上写着「重试」** —— 语义与视觉双重错位。这才是那段样式「永不渲染」的原因，单纯改圆角等于修死代码。

**改动（2 个文件）**

1. `jnclub-web/src/modules/bookmark/views/Home.vue` 的 `loadError` 分支改用组件自带的错误语义：
   ```html
   <JEmptyState v-else-if="loadError" variant="error" message="加载失败"
                hint="请检查网络后重试" retry-label="重试" @retry="handleRefresh" />
   ```
   （原为 `cta-label="重试"` + `@create="handleRefresh"`。`variant="error"` 下组件自动隐藏 CTA、显示 ghost 重试按钮，见 `JEmptyState.vue` 的 `showCta && variant !== 'error'` / `showRetry && variant === 'error'`。）

2. `jnclub-web/src/shared/components/ui/JEmptyState.vue` 的 `.empty-cta-ghost` 圆角 `var(--radius-md)`(16px) → `var(--radius-pill)`(999px)。理由：它与 `.empty-cta.glass-pill-btn` 同为「空态 CTA」，虽由 variant 互斥、不同屏出现，但同一组件内的主按钮不应有两种形状。

**改后实测**

| 场景 | 按钮类 | 圆角 | error 图标 | 重试图标 | 溢出 |
|---|---|---|---|---|---|
| 亮色 1440 | `empty-cta empty-cta-ghost` | 999px | ✓ | ✓ | 无 |
| 暗色 1440 | 同上 | 999px | ✓ | ✓ | 无 |
| 移动 390 | 同上（宽 316px） | 999px | ✓ | ✓ | 无 |
| 紧凑模式 1440 | 同上 | 999px | ✓ | ✓ | 无 |

暗色下按钮为玻璃档：`color: rgb(255,143,171)`、`background: rgba(255,143,171,0.16)`、`border: rgba(255,143,171,0.32)`；不再有 `background-image`（原品牌渐变已消失）。

### 17.2 P2-4：侧栏副标题 —— 原建议不可行，改的是另一处

原报告建议「把 `active-module` 改为空值或 `'overview'`/`'recycle'`」。**该建议不可行**：`MainLayout` 的 `active-module` prop 类型是 `ModuleKey`，而 `ModuleKey` 的定义里**不含** `'overview'`/`'recycle'`（它们不是主壳模块）。

复核定位到真实缺陷：`SideNav.vue` 的 `moduleLabel` 里 `overview` 分支**缺失**，于是概览页掉进 `activeModule` 兜底分支，而 `OverviewLayout.vue` 传的占位值是 `'bookmarks'` → **概览页侧栏副标题错显「收藏夹」**。

**改动（3 个文件）**

1. `jnclub-web/src/shared/layout/SideNav.vue` 的 `moduleLabel` 补 `if (n === 'overview') return '概览'`（在 `recycle` 分支之前），并加注释说明「路由页一律按 `route.name` 判定；`activeModule` 只对主壳（`route.name === 'app'`）有意义，路由页的兜底值只是占位，不能当真实模块名」。
2. `jnclub-web/src/shared/layout/OverviewLayout.vue`、`jnclub-web/src/modules/bookmark/layout/RecycleLayout.vue` 各加模板注释：`active-module` 只是占位（类型上只能是 `ModuleKey`），激活态与副标题一律按 `route.name` 判定。

**未改传值**，因为类型不允许；修在 `moduleLabel` 才是「路由页按路由判定」的正确归属。

**实测**（桌面 1440，读 `.logo-sub` 与 `.nav-item.active`）：

| 路由 | 侧栏副标题 | 激活项 |
|---|---|---|
| `/jnclub/overview` | 概览 | 概览 |
| `/jnclub/recycle` | 回收站 | 回收站 |
| `/?module=vault&dir=41` | 密码库 | 密码库 |
| `/?module=files&dir=31` | 云盘 | 云盘 |

概览页改前错显「收藏夹」（掉进 `activeModule` 兜底），现已正确。亮色/暗色/紧凑下 `siderW` 均 240、`folderW` 均 220。

### 17.3 P2-5：naive-ui 组件字号接入 `--fs-*` 阶梯

`light.ts` 与 `dark.ts` 的 `common` 各补 7 项绑定（**取值与 naive 当前默认逐项等值，零视觉变化**，纯粹防止 naive 升级改默认值时静默漂移）：

```ts
fontSize: t.fsBase,        // 14px
fontSizeMini: t.fsSm,      // 12px
fontSizeTiny: t.fsSm,      // 12px
fontSizeSmall: t.fsBase,   // 14px
fontSizeMedium: t.fsBase,  // 14px
fontSizeLarge: t.fsLg,     // 15px
fontSizeHuge: t.fsXl,      // 16px
```

（原报告的措辞「naive 默认 14/13/12px」有误：`fontSizeMedium` 默认是 14px 而非 13px，本批以 naive 源码的实际默认值为准。）

**绑定是活的 —— 探测式验证**（这是「零视觉变化」类改动唯一的验证手段：若绑定根本没生效，等值也会看起来「正常」）：

1. 把 `fontSizeMedium` 改成 `'13px'` → `.n-button--small-type` 实测仍 14px。**一度误判为绑定失效**。
2. 查清原因：naive 的 Button 用 `createKey('fontSize', size)` 取字号，`size="small"` 取的是 `fontSizeSmall` 而非 `fontSizeMedium` —— 是探测字段选错了。
3. 改把 `fontSizeSmall` 设为 `'13px'` → `.n-button--small-type` **立刻变 13px**（`--n-font-size` 同步为 13px），`tiny-type` 仍 12px（取 `fontSizeTiny`）。**绑定链路确认生效**。
4. 恢复原值并用 `diff` 校验与探测前完全一致、无 `PROBE` 残留。

### 17.4 P2-6：侧栏宽度与目录栏宽度令牌化

**新增 3 个令牌**（`jnclub-web/src/themes/tokens.ts`，亮暗共 2 处 + `tokensToCSSVars` 映射）：

| 令牌 | 值 | 消费点 |
|---|---|---|
| `--layout-sider-width` | `240px` | `SideNav.vue` 的 `NLayoutSider :width` |
| `--layout-sider-collapsed-width` | `64px` | **仅作记录，无法被消费**（见下） |
| `--layout-folder-width` | `220px` | `Home.vue` 的 `.folder-column` |

`npm run gen:theme` 后 `tokens.generated.css` 的 4 个块（亮/暗 × 媒体查询内外）均输出这三个变量（:134-136、:285-287、:436-438、:586-588）。

**一个 naive 类型限制（重要，写进代码注释）**：`NLayoutSider` 的 `collapsedWidth` prop 类型是 `Number`，传 CSS 变量会被 Vue 的 prop 校验拦下，故 `:collapsed-width="64"` **保持字面量**；而 `:width` 接受 `String`，且 naive 的 `formatLength()` 对非纯数字字符串原样返回，因此可以安全传 `'var(--layout-sider-width, 240px)'`。这也是 `--layout-sider-collapsed-width` 只登记不消费的原因。

实测：亮色/暗色/紧凑/移动四种组合下 `siderW` 240、`folderW` 220（紧凑模式未给这两个宽度分档，属既有行为）；移动端无 `.side-nav`（走 TabBar），令牌存在但无消费点，文档横向无溢出。

### 17.5 P2-7：列表行图标盒统一为 38px

`jnclub-web/src/modules/bookmark/components/DiskView.vue` 的 `.file-icon` 由 `36px / --radius-xs / 无边框` 改为与 `VaultView.vue` 的 `.item-icon` 同规格：

```css
width: 38px; height: 38px;
border-radius: var(--radius-sm);
background: var(--glass-chip-bg);
border: 1px solid var(--glass-chip-border);
```

38px 是项目既定的图标盒档位（`JStatCard.vue` 的 `.j-stat-icon`、`Welcome.vue` 同值）。

**刻意保留的差异**：颜色。云盘图标色由 `fileKindColor()` 按文件类型给出（inline style），密码库固定 `--brand-text` 作模块识别。实测暗色下 `.file-icon` 图标色 `rgb(199,199,204)`、`.item-icon` 为 `rgb(255,143,171)`，但两者的 `w/h` 均 38px、`radius` 均 10px、底/框均为 `rgba(255,143,171,0.16)` / `rgba(255,143,171,0.32)` —— 形状与容器同规格，仅图标色按语义分家。

### 17.6 P2-8：经复核无需改动

`VaultView.vue` 的 `.vault-item:hover` 在**第三批实施 P1-1 时已顺带统一**为：

```css
border-color: var(--brand-text);
box-shadow: var(--shadow-card-hover);
```

与 `DiskView.vue` 的 `.file-item:hover` 完全同源，P2-8 报告所描述的 `background: var(--glass-chip-bg)` 已不存在。**本项零改动闭环**。

### 17.7 验证

- **构建**：`cd jnclub-web && npm run build` → exit 0，`✓ built in 6.67s`，PWA precache 181 entries（3639.22 KiB）。
- **全组合回归**：亮色/暗色 × 1440、移动 390、紧凑模式；错误态（拦截 API 返 500）在四种组合下均正确渲染 error 图标 + ghost 重试按钮 + pill 圆角。
- **全量 diff**：31 files changed, 424 insertions(+), 185 deletions(-)（累计，含前八批）。
- 本批直接涉及 6 个文件：`Home.vue`、`JEmptyState.vue`、`SideNav.vue`、`OverviewLayout.vue`、`RecycleLayout.vue`、`DiskView.vue`，加令牌层 `tokens.ts` / `tokens.generated.css` / `light.ts` / `dark.ts`。

### 17.8 本批遗留

1. **网格模式 `itemHeight` 虚高 32%**（承 10.4-1、13.5-1）：`CollectionGrid.vue` / `NoteGrid.vue` 的 `itemHeight: 220` 对实测行高 148.59 虚高，滚动时可见跳变。未动，因为缩小有重叠风险、虚高只影响滚动条观感，风险不对称。
2. **P1-8 数值层级待产品决策**（承 11.5-1、13.5-2）：统计数字 24px 保留，若需更强层级应走 `font-size: clamp()` 或按位数降档。
3. **`.dash-group-title` 的 `text-transform: uppercase` 对纯中文零效果**（承 11.5-6）：建议若要有 overline 质感，把 `letter-spacing` 提到 `0.08em` 量级（属 P2）。
4. ~~**音乐页空白 + PWA Manifest 语法错误**（本批复核时发现的既有问题，见 16.5）：均非 `7c7f4f9` 引入，未处理。~~ → **已于第十一批处理（第十九章）：两项经查均为 dev 环境假象，非缺陷。**
5. **紧凑模式未给 `--layout-sider-width` / `--layout-folder-width` 分档**：令牌已就位，若产品要求紧凑模式收窄侧栏，只需在 `main.css` 的 `.app-density-compact` 块里覆盖这两个变量。
6. **虚拟滚动只经 mock 验证**（承 13.5-5）：120 条压测数据来自本地 mock，非真实数据规模。

### 17.9 验收截图

| 文件 | 内容 |
|---|---|
| `.ui-review/b9-p23-loaderror-before.png` | 改前错误态：书签图标 + 带「+」号的品牌主按钮写着「重试」 |
| `.ui-review/b9-p23-loaderror-after.png` | 改后：error 图标 + ghost 重试按钮 + pill 圆角 |
| `.ui-review/b9-p23-error-dark-1440.png` | 暗色错误态 |
| `.ui-review/b9-p23-error-mobile-390.png` | 移动端错误态（宽 316px，无溢出） |
| `.ui-review/b9-p24-overview-sider-1440.png` | 概览页侧栏副标题「概览」+ 激活项正确（改前错显「收藏夹」） |
| `.ui-review/b9-p26-compact-1440.png` | 紧凑模式：侧栏 240 / 目录栏 220 / 面板内边距 14px 16px |
| `.ui-review/b9-p26-sider-collapsed.png` | 侧栏折叠态 |
| `.ui-review/b9-p27-files-icon.png` | 云盘行图标盒 38px（改前 36px） |
| `.ui-review/b9-p27-files-dark-1440.png` | 暗色云盘行 |
| `.ui-review/b9-p27-vault-dark-1440.png` | 暗色密码库行（与云盘同规格，图标色按语义分家） |
| `.ui-review/b9-p27-files-mobile-390.png` | 移动端云盘行图标盒 38px |

---

## 十八、第十批：IA 决策落地与白屏回归修复（2026-10-16 追加）

本批先按产品决策清单（`.ui-review/decisions-ia.md`）落实四项 IA 项，实测时发现并修复了一处**由本批自身引入的整页白屏回归**。

### 18.1 IA-1：维持现状（决策，无代码改动）

**决策**：不加区分标记，记为已知取舍。

**理由**：P0-1（第二批）已让两套页头视觉同构 —— 同为 `--header-height` 60px 的横条、同一条通栏 `1px solid var(--border)` 底线、标题同为 `--fs-2xl`/700。剩余差异只是结构（面包屑条 vs 大标题条）。而给 `kind:'route'` 加标记（外链小箭头、分隔线等）等于把「开发者的架构分层」教给用户 —— 用户学到后没有任何可操作结论：无论哪种点击，意图都是「去便签」。

**实测支撑**（1440×900 亮色，侧栏 8 项 `.nav-item-wrap`）：逐项高度均 40px、字号均 14px、文字色非激活 `rgb(110,110,115)`、图标色激活 `rgb(201,37,126)`，`childCount` 均 2 —— **两类入口在渲染层完全无差异**，`kind` 是只写不读的元数据（`NavItem.vue` 模板只有 `@click="$emit('click')"`，从不读 `kind`）。

**备选及未采纳原因**：
- 加分隔线分组：需重排 `DEFAULT_ORDER`，会与用户已有的自定义顺序（`localStorage` 的 `nav.order`）冲突。
- hover 时显示小箭头：语义不准 —— 中文语境里小箭头通常表示「外链 / 新窗口」，而这里只是同应用内换路由。

### 18.2 IA-5：智能返回（新增 `useSmartBack`）

**问题定性**：这不只是 IA 问题，而是**功能缺陷**。按钮的图标（`ArrowLeft`）、`title="返回"`、`aria-label="返回"` 三者一致承诺「返回上一页」，实际行为却是 `router.push('/')`（解析为主壳默认模块收藏夹）。改前实测：便签 → 概览 → 点返回，落在收藏夹。

**改动**：新建 `jnclub-web/src/shared/composables/useSmartBack.ts`：

```ts
export function useSmartBack(fallback = '/') {
  const router = useRouter()
  const goBack = () => {
    // 直接打开该 URL（无来路）时 history.state.back 为 null，此时退回 fallback。
    if (window.history.state?.back != null) router.back()
    else router.push(fallback)
  }
  return { goBack }
}
```

判据用 `window.history.state.back`：Vue Router 4 在每次导航后把 `{ back, current, forward, position }` 写进 `history.state`，直接打开页面时为 `null`。

**消费点**：`jnclub-web/src/shared/layout/OverviewLayout.vue` 与 `jnclub-web/src/modules/bookmark/layout/RecycleLayout.vue` 的 `@back="router.push('/')"` → `@back="goBack"`。

**未采纳原报告建议的「直接改 `router.back()`」**：那会让「直接打开 /overview」（新标签页、刷新后首次进入）变成**死按钮** —— 点下去毫无反应，比跳错页面更难排查。

**实测（两个分支都覆盖）**：

| 场景 | `history.state.back` | 点击结果 |
|---|---|---|
| SPA 内：便签 → 概览 → 返回 | `'/?module=notes&dir=21'`（非空） | 落回 `/jnclub/?module=notes&dir=21` ✓ |
| 新标签页直接打开 `/overview` → 返回 | `null` | 落回主壳 `/jnclub/` ✓ |
| 新标签页直接打开 `/recycle` → 返回 | `null` | 落回主壳 `/jnclub/` ✓ |

### 18.3 IA-2 与 IA-4：面包屑去冗余 + 抽屉更名

**IA-2 — 删除面包屑首项**：`jnclub-web/src/shared/layout/AppHeader.vue` 模板删除 `<NBreadcrumbItem @click="handleBreadcrumbRoot">JNClub</NBreadcrumbItem>` 整行。依据：它与第二项绑**同一个** `handleBreadcrumbRoot`、点击效果逐位相同（都回到「全部」），属重复层级；品牌名在侧栏 `BrandLogo` 区已有。`handleBreadcrumbRoot` 函数与 `breadcrumb-root` emit 保留（新的首项仍在用）。

连带调整两处移动端 CSS（项序前移一位）：
- 删除 `@media (max-width:767px)` 内的 `.jnclub-breadcrumb :deep(.n-breadcrumb-item:first-child) { display: none }` —— 该规则原用于隐藏品牌根，现在首项是模块名，不应隐藏。
- `@media (max-width:400px)` 的选择器 `:nth-child(2):has(~ :nth-child(3))` → `:nth-child(1):has(~ :nth-child(2))`。

**实测**：桌面 1440 便签页面包屑 `["便签", "工作"]`（2 项）；点首项后变 1 项 `["收藏夹"]`，「回到全部」语义仍有效。移动端 390 与 360：`["便签","工作"]`，可见性 `[false, true]`（模块名按 400px 断点隐藏，目录名保留），无横向溢出。

**IA-4 — 抽屉标题「命令面板」→「搜索」**：`jnclub-web/src/modules/bookmark/components/SearchDrawer.vue` 的 `<JGradientText text="命令面板"` → `text="搜索"`。理由：抽屉主体是跨模块搜索，命令列表只在空输入时作为回退出现；且本项目面向个人工作台用户，不预设其熟悉 Command Palette 这一开发者术语。实测标题读回 `"搜索"`。

### 18.4 ⚠ 白屏回归：`<template>` 根层级 HTML 注释导致组件变多根

**这是本批自身引入的回归，必须记录。**

**现象**：进入概览/回收站正常，但**离开时整页白屏**。实测回退瞬间 `#app` 的 `innerHTML` 从 59264 字符直接掉到 688（只剩 `n-config-provider` 空壳与 cursor-host），`.nav-item-wrap` 变 0，等待 10 秒不恢复；MutationObserver 显示 `page-fade-leave-active` 与 `page-fade-enter-active` **从未出现** —— 不是过渡卡住，是旧组件卸载后新组件没渲染。

**排除过程**（关键：证明是回归而非既有缺陷）：
1. `git stash push -u -- jnclub-web/src` 回到干净基线 → 同一操作序列 `appLen 67903`、`nav 8`，**基线完全正常** → 白屏是改动引入。
2. `git stash pop` 恢复，逐文件排查 → diff 本身干净（`useSmartBack` 逻辑正确、`@back` 绑定正确）。
3. 读取 dev server 编译产物 `http://localhost:5173/jnclub/src/shared/layout/OverviewLayout.vue`，发现根节点异常：

```js
return _openBlock(), _createElementBlock(
  _Fragment,                                     // ← 根节点是 Fragment（多根）
  [ _createCommentVNode(" active-module 只是占位：…"),
    _createVNode($setup["MainLayout"], {…}), … ],
  2112  /* STABLE_FRAGMENT, DEV_ROOT_FRAGMENT */
);
```

**根因**：我在 `OverviewLayout.vue` 与 `RecycleLayout.vue` 的 `<template>` **根层级**加了说明性 HTML 注释（用来解释 `active-module` 只是占位）。HTML 注释在 Vue 3 中会编译成真实节点（`_createCommentVNode`），于是组件从「单根」变成「多根 Fragment」（编译产物出现 `DEV_ROOT_FRAGMENT`）。而 `jnclub-web/src/App.vue:163-172` 用 `<Transition name="page-fade" mode="out-in">` 包裹路由组件 —— **`<Transition>` 只能作用于单根**，遇到 Fragment 后 `out-in` 的「先离场再进场」状态机卡死：旧组件已卸载、新组件永不挂载。

这解释了全部现象：**进入正常**（概览页本身渲染无碍）、**离开白屏**（离场时 Transition 卡住）、**module→module 正常**（主壳 `AppWrapper.vue` 是单根）。

**修复**：把两处根层级注释移入 `<script setup>` 的 JSDoc（并写明这条约束本身），模板恢复为单根 `<MainLayout>`。修复后编译产物恢复 `_createBlock($setup["MainLayout"], {…})`。

**同类隐患清理**：全仓扫描「SFC 根层级紧跟 `<!--`」的写法，命中 4 处，其中 1 处（`SideNav.vue` 的 P2-6 宽度令牌说明）是本批引入，已同样移入 script。另 3 处为既有代码，**经核实无风险**：
- `shared/components/RouteLoadingBar.vue` —— 该组件本身即「无渲染」，且不直接挂在路由 `Transition` 下。
- `shared/components/animation/JMagnet.vue` —— 用于 `Welcome.vue`（不在 `page-fade` Transition 内层）。
- `modules/bookmark/components/BatchActionModals.vue` —— 弹窗集合，挂在 `Home.vue` 内。
- `SideNav.vue` 本就是多根（`NLayoutSider` + 3 个 Drawer），且位于 `MainLayout` 内部、不直接受路由 Transition 包裹，故其注释未造成白屏；清理属消除同类写法。

**最终编译验证**：5 个路由组件（`AppWrapper`、`Welcome`、`Music`、`OverviewLayout`、`RecycleLayout`）的 `DEV_ROOT_FRAGMENT` 标记均为 0。

### 18.5 验证

| 场景 | 改前 | 改后 |
|---|---|---|
| 主壳 → 概览 → 浏览器 back | 白屏（appLen 688） | 正常（appLen 68119，nav 8） |
| 主壳 → 概览 → 点侧栏便签 | 白屏 | 正常（66880，落 `?module=notes&dir=21`） |
| 概览 → 回收站（route→route） | 白屏 | 正常（34933） |
| 回收站 → back → 概览 | 白屏 | 正常（59090） |
| 概览页点「返回」（SPA 内从便签来） | 落收藏夹 | 落便签 ✓ |
| 新标签页直接开 /overview 点「返回」 | 落收藏夹 | 落主壳 ✓ |
| 移动端 390：主壳 → 概览 → 返回 | 白屏 | 正常（TabBar 7 项，appLen 67307） |
| 暗色：概览 → 回收站 → back | 白屏 | 正常（59295，nav 8） |

**构建**：`cd jnclub-web && npm run build` → exit 0，`✓ built in 6.37s`，PWA precache 181 entries（3639.22 KiB）。

### 18.6 本批遗留

1. **IA-1 维持现状**：若后续用户反馈「侧栏点击后页头变了」造成困惑，可重新评估（选项见 18.1）。
2. **`SideNav.vue` 的多根结构未重构**：它本来就是多根（`NLayoutSider` + 3 Drawer），本批只移除了注释。若将来把 `SideNav` 直接挂到某个 `Transition` 下会复现同类问题。
3. ~~**同类约束无自动化防护**：本次靠人工扫描 + 编译产物检查发现。建议在 ESLint 或构建脚本里加一条「SFC 根层级不得有注释节点」的检查，或统一约定「所有模板说明写进 `<script setup>` 的 JSDoc」。~~ → **已于第十一批实施（19.4）：新增 `scripts/check-template-root.mjs` 并接入 `predev`/`prebuild`，违规直接中断构建。**
4. 承 17.8 的全部遗留（`.dash-group-title` 的 `uppercase`、音乐页空白、PWA Manifest 语法错误、紧凑模式宽度令牌未分档、虚拟滚动仅 mock 验证）。

### 18.7 验收截图

| 文件 | 内容 |
|---|---|
| `.ui-review/b10-01-overview-1440.png` | 概览页（IA-5 智能返回入口），白屏修复后正常渲染 |
| `.ui-review/b10-02-smartback-landing-1440.png` | 从便签进概览后点「返回」，落回便签页 |
| `.ui-review/b10-03-breadcrumb-1440.png` | 桌面端面包屑两项（「便签 / 工作」），品牌根已移除 |
| `.ui-review/b10-04-breadcrumb-390.png` | 移动端 390 面包屑（模块名按 400px 断点隐藏，目录名保留） |
| `.ui-review/b10-05-drawer-title-1440.png` | 搜索抽屉标题已改为「搜索」 |

---

## 十九、第十一批：遗留清理与部署前加固（2026-10-16 追加）

本批不新增视觉改动，只处理前几批累积的遗留项与部署风险。四项遗留中**两项经查是 dev 环境假象**（不是缺陷），另两项是真实缺陷。

### 19.1 音乐页「空白」——dev 环境假象，非缺陷

**判定依据（三条独立证据）**

1. 路由与文件都在：`jnclub-web/src/shared/router/index.ts:64-66` 指向 `../views/Music.vue`，解析到 `jnclub-web/src/shared/views/Music.vue`（存在，2994 bytes）。Playwright 实测 `/jnclub/music`：`document.title` = 「音乐 - JNClub」、`appLen 624`、`hasIframe true`、`frameSrc /music/?theme=dark` —— **主站壳正常渲染，音乐页组件正常挂载**。
2. 那条 404 来自 iframe 的 `src`：`Failed to load resource: 404 @ http://localhost:5173/music/?theme=dark`。音乐前端是**独立构建、独立部署**的应用（`music-frontend/`，`vite.config.ts` 的 `base: '/music/'`，端口也是 5173），生产环境由 nginx 静态托管 `/music/`。而 `jnclub-web` 的 dev server 只服务 `/jnclub/` 与代理 `/api`、`/sso`，**不托管 `/music/`** —— dev 下 iframe 必然 404。
3. 该路径不在本仓库主站 dev 的职责范围内，属「本地跑单前端时无法完整验证跨应用嵌入」的固有限制。

**结论**：音乐页在 dev 下的表现（iframe 空白 + 一条 404）是环境限制，不是代码缺陷。**上线后由 nginx 托管 `/music/` 即正常**，无需改动。若要在本地完整验证，需另起 `music-frontend` 的 dev server 或构建后用 nginx 托管。

### 19.2 PWA Manifest 语法错误——dev 环境假象 + 已顺手消除

**现象**：每次页面加载稳定打印两条 `Manifest: Line: 1, column: 1, Syntax error. @ http://localhost:5173/jnclub/manifest.webmanifest:0`。

**根因**：`vite-plugin-pwa` **只在构建时**产出 `manifest.webmanifest`。dev server 收到该请求后落到 SPA fallback，返回的是 `index.html`（HTML）——浏览器按 JSON 解析自然失败。

**验证生产无此问题**：`npx vite preview` 起生产产物后，`curl -i http://localhost:4173/jnclub/manifest.webmanifest` 返回 `200` + `Content-Type: application/manifest+json` + 合法 JSON（`name = JNClub 个人工作台`）。**线上不受影响**。

**仍决定修掉**：这条噪音每次加载都出现，极易被误读为真实缺陷（本次评审中就被误列过一次）。修法是在 `jnclub-web/vite.config.ts` 新增 `devManifestPlugin()`（`apply: 'serve'`，用 `server.middlewares.use` 直接响应 `/jnclub/manifest.webmanifest`），并把 manifest 对象抽成 `pwaManifest` 常量**同时供 VitePWA 与 dev 中间件使用**，避免两处内容漂移。

**未采用 `devOptions.enabled`**：那会连带启用 dev Service Worker，导致改代码后刷新拿到的仍是旧缓存，开发体验代价远大于收益。本方案只补一个静态响应，不碰 SW。

**验证**：dev 下 `curl -i http://localhost:5173/jnclub/manifest.webmanifest` → `200` + `application/manifest+json` + 合法 JSON ✓；页面 console **errors 归零**（改前每次 2 条）✓。

### 19.3 `apple-mobile-web-app-capable` 弃用警告

`jnclub-web/index.html:30` 只有 Apple 前缀版本，Chrome 报 `is deprecated. Please include <meta name="mobile-web-app-capable" content="yes">`。已在 `:30` 前补标准名标签，两者并存（Chrome 认标准名、Safari 认 Apple 名）。改后 console 的 warning 归零。

### 19.4 模板根层级注释：从「人工约定」升级为「构建时拦截」

**背景**（承 18.4、18.6-3）：第十批在 `OverviewLayout.vue` / `RecycleLayout.vue` 的 `<template>` 根层级写了两段说明性 HTML 注释，Vue 3 把 HTML 注释编译成真实节点（`_createCommentVNode`），组件从单根变成多根 Fragment（编译产物出现 `DEV_ROOT_FRAGMENT`），而 `App.vue` 用 `<Transition name="page-fade" mode="out-in">` 包裹路由组件、`<Transition>` 只能作用于单根 —— 状态机卡死，**离开该页即整页白屏**（`#app` 塌成 688 字符空壳），无 console 报错、无 PAGEERROR，纯静默失效。

**新增 `jnclub-web/scripts/check-template-root.mjs`**，接入 `predev` 与 `prebuild`（`package.json:9/11`），使违规直接以退出码 1 中断构建 —— **不让白屏有机会上线**。

**判据设计（关键）**：只要求「根层级无注释」会误报一批既有组件，它们本来就是刻意多根：

| 组件 | 根层级形态 | 是否危险 |
|---|---|---|
| `BatchActionModals.vue` | 2 元素 + 2 注释 | 否（弹窗集合） |
| `RouteLoadingBar.vue` | 0 元素 + 1 注释 | 否（无渲染组件） |
| `animation/JMagnet.vue` | 2 元素 + 1 注释 | 否（两个条件分支） |
| `AppHeader.vue` | 2 元素 + 1 注释 | 否（头部 + 弹窗） |
| `SideNav.vue` | 4 元素 + 3 注释 | 否（侧栏 + 3 抽屉） |

真正危险的形态是「**本意单根，却被一段注释撑成多根**」= **元素数恰好 1 且有注释**。脚本据此区分：`elements.length === 1` 报 error 并中断构建；其余仅打印提示不阻断。

**容错设计**：解析失败（SFC 语法错误）一律 `continue` 跳过。理由是**护栏不该比它检查的代码更容易让构建失败** —— 语法错误交给 `vue-tsc` / `vite` 报，本脚本只管根层级形态。

**正反向验证**：

| 场景 | 期望 | 实测 |
|---|---|---|
| 当前全仓 | 通过，仅提示 5 个刻意多根 | `EXIT=0` ✓ |
| 临时造一个「1 元素 + 1 注释」违规文件 | 中断构建 | `EXIT=1`，精确指出文件与行号 ✓ |
| 临时造一个语法错误的 SFC | 跳过不崩 | `EXIT=0` ✓ |
| 清理临时文件后 | 恢复通过 | `EXIT=0` ✓ |

### 19.5 紧凑模式在概览/回收站完全失效（真实缺陷）

**现象**：密度设为「紧凑」后，主壳（收藏夹/便签/云盘/密码库）正常收缩，但**概览页与回收站毫无变化** —— 仍是 60px 页头 + `20px 24px` 面板内边距。

**根因（两层，缺一不可）**

1. `app-density-compact` 类此前由 `AppWrapper.vue` 单独传给 `MainLayout`，而概览/回收站走 `OverviewLayout.vue` / `RecycleLayout.vue`，**不经过 `AppWrapper`**，类从未挂上。
2. 更隐蔽的一层：`useUiDensity` 只在 `prefs.ready` 变 true 时才把后端权威值同步进 `density`，而 `prefs.load()` 此前**也只有 `AppWrapper` 调用** —— 概览/回收站的 `ready` 永远是 `false`，即便挂上类也拿不到真实密度。

**修法（内聚到唯一布局根）**：`jnclub-web/src/shared/layout/MainLayout.vue` 是三个页壳（`AppWrapper` / `OverviewLayout` / `RecycleLayout`）**共用的唯一布局根**，而 `app-density-compact` 覆盖的 `--layout-page-gutter` / `--header-height` / `--layout-panel-padding` 恰好都是它的下游令牌。故把两件事都收敛到 `MainLayout`：

- 模板上 `:class="{ 'is-mobile': isMobile, 'app-density-compact': density === 'compact' }"`
- `onMounted(() => { prefs.load() })`
- `AppWrapper.vue` 中对应的 `useUiDensity` 引入、`density` 变量、`:class` 传参**全部删除**（单一来源）

**配套：`load()` 改为幂等**。`MainLayout` 会随路由切换重新挂载，若不做保护则每次切页都重发 `/api/user-preferences`。`useUserPreferences.ts` 增加 `loaded` 早返回 + `inflight` Promise 复用（并发调用共享同一请求）。

**实测（localStorage `jn-pref-ui.density` = `"compact"`）**

| 场景 | 改前 | 改后 |
|---|---|---|
| 概览页 `hasCompactClass` | false | **true** ✓ |
| 概览页页头高 | 60px | **52px** ✓ |
| 概览页 `.j-page-body` padding | `20px 24px` | **`14px 16px`** ✓ |
| 概览页 `.j-page-body` margin-top | 20px | **12px** ✓ |
| 主壳收藏夹 `.collection-column` padding | `14px 16px` | `14px 16px`（未回归）✓ |
| 主壳收藏夹页头高 | 52px | 52px（未回归）✓ |

两端终于取到同一份紧凑值。

### 19.6 依赖声明加固：`@vue/compiler-sfc` 显式化

新增的 `check-template-root.mjs` 依赖 `@vue/compiler-sfc`，但它**此前未在 `package.json` 中声明**，只是被 `vue` 提升到 `node_modules` 顶层的隐式依赖。风险具体且严重：该脚本跑在 `prebuild`，一旦依赖树变化导致解析失败，**服务器构建会中断并触发自动部署回滚**。

已在 `devDependencies` 显式声明 `"@vue/compiler-sfc": "^3.5.13"`（与 `vue` 的 `^3.5.13` 同范围，实测装入 3.5.40），并更新 `package-lock.json`（`lockfileVersion: 3`，**仅 +1 行**，无其他连带变更）。

### 19.7 验证

| 项 | 结果 |
|---|---|
| 路由链全量回归（7 跳：module→module→route→route→module→module→返回） | 全部正常，`appLen` 均 >34000、`nav 8`、**无 PAGEERROR** ✓ |
| 智能返回（有历史） | `history.state.back = /?module=vault&dir=41` → 点「返回」落回该页 ✓ |
| 智能返回（新标签直开 `/overview`） | `back = null` → 落主壳（非死按钮）✓ |
| 侧栏副标题 | 收藏夹/便签/概览/回收站/云盘/密码库 六页均正确 ✓ |
| console 错误 | 归零（改前每次 2 条 manifest + 1 条 meta 警告）✓ |
| 生产 manifest（`vite preview`） | `200` + `application/manifest+json` + 合法 JSON ✓ |
| `npm run check:template-root` | 通过；反向用例 `EXIT=1` ✓ |
| `npm run build` | `EXIT=0`，`✓ built in 6.38s`，PWA precache 181 entries（3639.44 KiB）✓ |

### 19.8 本批遗留

1. **音乐页需在生产环境验证**：dev 下 iframe 必然 404，本地无法完整验证跨应用嵌入。上线后需复核 `/music/` 与 iframe 内主题联动。
2. **紧凑模式仍未给宽度令牌分档**（承 17.8-5）：`--layout-sider-width` / `--layout-folder-width` 已就位，紧凑模式下仍为 240/220。若产品要求紧凑时收窄侧栏，只需在 `main.css` 的 `.app-density-compact` 块覆盖这两个变量。
3. **`check-template-root` 只覆盖 `.vue` 的 `<template>` 根层级**：不覆盖 JSX/render 函数手写的多根，也不覆盖「根元素内部」的注释（那不会造成 Fragment，无需覆盖）。
4. **承前批未决**：网格模式 `itemHeight` 虚高（17.8-1）、P1-8 数值层级（17.8-2）、`.dash-group-title` 的 `uppercase`（17.8-3）、虚拟滚动仅 mock 验证（17.8-6）。

