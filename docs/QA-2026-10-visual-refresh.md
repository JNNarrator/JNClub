# QA · 2026-10 · JNClub 视觉体系重塑 —— 独立回归验证报告

> 验证对象：`c5a2b86 → 32e531f → fb9c6f6 → 3de708f → 8478450 → 99a2863 → 6b5aec1`（42 文件 / +1030 −407）
> 依据文档：`docs/PRD-2026-10-visual-refresh.md`（第八章为验收标准）、`docs/DESIGN-2026-10-visual-refresh.md`
> 验证者：QA 严过关
> 验证原则：**不复述工程师的数字，凡引用必有独立复算或实测来源。**

---

## 一、结论

**有条件通过（Conditional Pass）。**

- 工程师的 7 个 commit **没有引入白屏、没有引入类型错误、没有引入构建失败**，四层表面体系、玻璃收敛、模块色启用、hover 语言统一这几件事**确实落地了**，并且我在真实浏览器里逐条量到了。
- 但「42 项全绿」这个结论**不成立**：其中 **2 项是因为把 PRD 判据换成了未获批的放宽判据才变绿**，**1 项是把上界从 6.5 偷偷写成 6.6 才变绿**，另有 **4 项 PRD 硬指标从未被脚本覆盖，而实测确实不达标**（概览页字号档位、概览页粉色元素数、云盘页粉色元素数、三个页面的粉色底光）。
- 其中 **P0 一项**：Welcome / Extension / NoteEditor 三个页面**仍保留 2 层 16%/14% 的粉色页面底光**，这是 PRD 4.2 明令禁止的项，且被脚本的「光晕唯一性 ✓」完全掩盖。

路由判定：**Engineer（源码有 Bug）+ QA（脚本有缺陷，已自行修复）**。详见第八节。

---

## 二、验证方法与独立性声明

| 手段 | 具体做法 | 说明 |
| --- | --- | --- |
| 脚本审计 | 逐行读 `verify-visual-tokens.mjs` 的 46 条断言，逐条比对 PRD 8.1 原文 | 见第三节 |
| 数学复算 | **手算** 4 组（ΔL\* 亮/暗、描边对比度、图表首序列对比度、soft 底混色），再用独立脚本复算全部 | 见第三节 3.4 |
| 构建 | `gen:theme` / `check:template-root` / `vue-tsc` / `vite build --outDir dist-qa` | 见第四节 |
| **真实渲染** | Playwright(WebKit 2370) + 全量 `/api/**` mock，跑通 7 个页面 × 亮/暗 × 1440/390，共 12 个渲染场景 | **工程师未做此项，我做了**。见第五节 |
| 静态交叉核对 | `grep`/逐文件读样式，核对 token 消费链、死类、hover 参数 | 见第六、七节 |

**关于「沙箱无浏览器」**：不成立。本机 `~/Library/Caches/ms-playwright/webkit-2370` 已安装 WebKit，配合 `playwright-core` 可直接驱动；后端未启动也不影响 —— 用 `page.route()` 拦截 `/api/**` 返回构造数据，即可让登录态与全部列表页正常渲染。我全程 0 console error、0 pageerror，说明 mock 没有污染页面逻辑。

**唯一真实的环境限制**是 `npm run build` 会被 `safe-delete` 钩子拦截（第四节有原始报错，我独立复现了）。

---

## 三、复核脚本审计（最高优先项）

### 3.1 结论速览

| 判定 | 数量 | 说明 |
| --- | --- | --- |
| 断言可信 | 37 条 | 数学正确、口径与 PRD 一致 |
| **恒真断言** | 1 条 | 无论代码怎么改都会 ✓ |
| **判据被放宽** | 3 条 | 2 条换成未获批的开放项判据，1 条上界 +0.1 |
| **口径错位（测了近似物）** | 2 条 | 用类名代替 token；用「无 blur」代替「alpha=1」 |
| **关键项缺失** | 4 条 | 页面级粉色光晕、粉色元素数、字号档位、同屏玻璃数 |

### 3.2 逐条问题（原文 → 问题 → 处置）

**① 恒真断言（1 条）**

```js
record('明度阶梯', '暗 surface↔raised ΔL*', dLSurfaceRaisedDark.toFixed(2), '无硬指标', true)
```
`pass` 直接写死 `true`，数值只当展示。它贡献了 42 项里的 1 项「达标」，但**不构成任何验证**。
→ 我改为 `≥3（L1→L2 可辨，参考值）`，成为真断言（实测 5.69 ✓）。

**② 判据被放宽（3 条）—— 这是最严重的一类**

```js
record(..., '亮 page↔surface ΔL*', '3.41', '≥3.2（O1）', dLPageSurfaceLight >= 3.2)
record(..., '暗 page↔surface ΔL*', '15.30', '≥15（O2）', dLPageSurfaceDark >= 15)
```

PRD 8.1 原文是 **亮 ∈[3.5, 6]、暗 ≥16**。脚本换成了 **≥3.2 / ≥15**。

这两个数字出自 `DESIGN §12 开放项 O1/O2`，而该表标题是「**开放项（需用户拍板，附我的建议）**」，O1 原文写的是「**把判据改为 ≥3.2**」—— 是一个**待批准的提案**，不是已生效的验收标准。脚本把提案当结论、并把「上限 6」整个删掉，于是两项硬指标静默失效。

事实是：**实测 3.41 与 15.30 都低于 PRD 原文门槛**。这不是 bug，是 Q1/Q6 决策（不动 `--bg-page`）的必然代价，但**必须显式记录为偏差并由 PM/用户拍板**，不能自动标 ✓。

第三条：
```js
'vs surface ∈[4.5,6.5]'  ← 目标字符串
vsSurface >= 4.5 && vsSurface <= 6.6  ← 实际断言
```
目标写 6.5，代码判 6.6。密码库暗色 `#E09E48` 实测 **6.57**，正好卡在这 0.1 的缝里「通过」。而 `DESIGN §3.3` 自己已经给出了备选值 `#E59A38`（6.47）并注明「若要严格 ≤6.5」。**正确做法是改 token，不是放宽判据。**

→ 我恢复 PRD/设计原文判据，三项现均为 ✗，并在 note 里写明「属待拍板开放项」/「应改 token」。

**③ 口径错位（2 条）**

| 断言 | PRD 原文 | 脚本实际测的 | 后果 |
| --- | --- | --- | --- |
| `L1 选择器上的 backdrop-filter = 0` | G5：「L1 的 **background-color alpha 必须是 1**」 | 只查有没有 `backdrop-filter` | `background: rgba(255,255,255,.72)` 不带 blur 也能过，但它仍不是实色 |
| `card-top-bar 消费 = 0` | 8.1：「`--gradient-top-bar` **消费处** 1→0」 | 数 `card-top-bar` 这个**类名** | 换个类名、或在别的类里写 `background: var(--gradient-top-bar)` 即可绕过 |

→ 我补了「L1 background 必须引用不透明 token」与「`var(--gradient-top-bar)` 消费处 = 0」两条真断言。后者实测 0 ✓。

**④ 关键项缺失（4 条）—— 其中一条直接制造了假绿灯**

最典型的是这一段：

```js
record('粉色配额', '--surface-glow 消费处', glowConsumers, '=1', glowConsumers === 1)
```

它只证明**新 token** 用了一次，**证明不了「页面底不再有粉色光晕」**。而 PRD 4.2 禁止的恰恰是后者。实测：

```
Welcome.vue:150        .welcome-page       → 2 层：rgba(236,91,142,.16) + rgba(255,143,171,.14)
ExtensionPage.vue:186  .ext-page           → 同上 2 层 16%/14%
NoteEditorPage.vue:160 .note-editor-page   → 同上 2 层 16%/14%
```

浏览器实测 `.welcome-page` 的 `background-image`：
`radial-gradient(1200px 500px at 10% -10%, rgba(236,91,142,0.16), transparent 60%), radial-gradient(900px 400px at 110% 120%, rgba(255,143,171,0.14), transparent 60%)`
—— 与改造前的「三层 16% 粉光晕」是同一个配方，只是少了一层。

→ 我新增「页面级（非 L3 浮层）品牌光晕 = 0」断言，按选择器是否属于 L3 浮层区分（`.n-modal`/`.glass-card--modal`/`.glass-header`/`.trend-tip` 等浮层内的品牌光晕合法）。实测 **3 处违规**。

另外三项缺失（粉色元素数、同屏玻璃数、字号档位）是 PRD 8.1 里有、但脚本**完全没写**的，已由我在第五节用渲染补齐。

### 3.3 我做的修改（`jnclub-web/scripts/verify-visual-tokens.mjs`）

只改脚本，**未触碰任何 `src/` 业务代码**。

1. 明度阶梯 3 条：恢复 PRD 原文判据；恒真断言改为真断言；偏差写入 note。
2. 暗色模块图标上界 `6.6 → 6.5`。
3. 新增 `cssBlocks()` / `styleSource()` 辅助函数（只扫 `<style>` 块，避免把 `<template>` 文本当选择器）。
4. 新增「L1 background 为实色」断言，**带自校验**（同时报「扫描到几条 L1 规则」，扫描数 <6 直接判失败）。
   —— 这条自校验是被自己的 bug 逼出来的：我第一版用了 `(?:^|;)` 锚点，而注释夹在 `;` 与 `background` 之间会让正则失配，结果是「0 违规 → 静默通过」。**这正是我在工程师脚本里抓到的同一种病**，所以必须让「扫不到」不可能伪装成「没问题」。
5. 新增「`var(--gradient-top-bar)` 消费处 = 0」。
6. 新增「`--surface-glow` alpha ≤0.06」。
7. 新增「页面级（非 L3 浮层）品牌光晕 = 0」。

修改后：**46 条断言，4 条未达标，`--strict` 退出码 1。**

```
✗ 亮 page↔surface ΔL*  = 3.41   [目标 ∈[3.5, 6]（PRD 8.1）]
✗ 暗 page↔surface ΔL*  = 15.30  [目标 ≥16（PRD 8.1）]
✗ 密码库 #E09E48 vs #26262A = 6.57  [目标 ∈[4.5,6.5]]
✗ 页面级（非 L3 浮层）品牌光晕 = 3  [目标 =0（PRD 4.2）]
   ⚠ NoteEditorPage.vue :: .note-editor-page; ExtensionPage.vue :: .ext-page; Welcome.vue :: .welcome-page
```

> 注：`npm run verify:visual` 走 `--strict`，因此该门禁现在会红。这是**有意的**——它反映的是真实偏差，不是脚本坏了。

### 3.4 对比度 / ΔL\* / 合成函数是否符合标准式

我**手算**了 4 组（不借助脚本），与脚本输出逐位对齐：

| 项 | 手算过程摘要 | 手算值 | 脚本值 | 判定 |
| --- | --- | --- | --- | --- |
| ΔL\*(`#F5F5F7`→`#FFF`) | Y=0.914833 → L\*=96.61 → 100−96.61 | 3.39 | 3.41 | ✅ 一致（舍入） |
| ΔL\*(`#000`→`#26262A`) | Y=0.019664 → ∛Y=0.26995 → L\*=15.31 | 15.31 | 15.30 | ✅ |
| contrast(`#D8D8DE`,`#F5F5F7`) | Y=0.689855 → 0.964833/0.739855 | 1.304 | 1.30 | ✅ |
| contrast(`#C9256A`,`#FFF`) | Y=0.147826 → 1.05/0.197826 | 5.308 | 5.31 | ✅ |
| contrast(`#EC5B8E`,`#F5F5F7`) | Y=0.272664 → 0.964833/0.322664 | 2.990 | 2.99 | ✅ |
| `mix(#2F9DF7, 12%, #FFF)` | 47·.12+255·.88=230.04 / 243.24 / 254.04 | `#E6F3FE` | `#E6F3FE` | ✅ |

函数本身：

- `relLuminance()`：分段阈值 `0.03928`，系数 `0.2126/0.7152/0.0722` —— WCAG 2.0 标准式，✅。
- `lstar()`：`Y > 0.008856 ? 116·∛Y−16 : 903.3·Y` —— CIE 1976 标准式（ε=0.008856，κ=903.3），✅。
- `composite()`：source-over 公式正确，✅。
- `mix()`：CSS `color-mix(in srgb, …)` 在**伽马编码 sRGB** 空间做线性插值，脚本按编码值线性插值，与浏览器一致 ✅。
- ⚠ **潜在缺陷（P2）**：`contrast()` 只对**前景**做 alpha 合成，`parseColor(bg)` 带 alpha 时直接取 rgb 忽略 alpha。当前所有用例的 bg 都是不透明色，**无实际影响**，但属于「以后换个半透明底就悄悄算错」的隐患。

### 3.5 玻璃层数的口径核查

- 脚本数的是 **`"backdrop-filter: blur"` 字符串出现次数 = 27**（含 `-webkit-` 双写），与 PRD 8.1「全仓 `backdrop-filter: blur` 出现次数 95 → ≤30」**同口径**（原始 95 也是这么数的）。✅
- 但 **27 是「属性声明数」，不是「规则条数」，也不是「同屏层数」**。实测规则数 = **14 条**：
  `main.css` 7 + `MobileTabBar` 1 + `JBatchBar` 1 + `DashboardView(.trend-tip)` 1 + `NoteEditor(.outline-float)` 1 + `DevtoolsPalette` 1 + `ToolCurl` 1 + `animation/utility.css(.backdrop-blur)` 1。
- 脚本第二条断言用 `Math.round(27/2)=14` 反推规则数，**当前恰好正确**，但这个估法很脆：`ToolCurl.vue:406` 只有标准属性、没有 `-webkit-` 双写，只要有第二处单写，估值就会偏。属于「碰巧对的断言」。
- PRD 真正要的是「**同屏** `backdrop-filter` 生效元素数 ≤3/≤2」，脚本完全没测 —— 我用渲染补了（见 5.3）。

### 3.6 死 token 白名单是否合理

```js
const DEAD_TOKEN_ALLOW = new Set(['--layout-sider-collapsed-width'])
```

**豁免本身合理**：`tokens.ts:169-173` 有明确注释，`SideNav.vue:153` 写的是 `:collapsed-width="64"`，naive-ui 的 `collapsedWidth` prop 类型限定 `number`，确实无法消费 CSS 变量。我核对了全仓，`--layout-sider-collapsed-width` 只在生成物与 `tokensToCssVars` 出现，零 CSS 消费 —— 白名单没有掩盖别的问题。

**但它掩盖了一个真实隐患（P2）**：token 声明 `64px`、组件硬编码 `64`，两处双源。改 token 不会有任何效果。建议要么删掉 token，要么在 `tokens.ts` 注释里写明「改这里必须同步改 SideNav.vue:153」，避免下一个人误以为改 token 就能生效。

---

## 四、构建验证（逐条命令 + 实际输出）

| # | 命令 | 结果 | 输出摘要 |
| --- | --- | --- | --- |
| 1 | `npm run gen:theme` | ✅ | `✓ 已生成 src/themes/tokens.generated.css（亮色 155 个变量，暗色 155 个）` |
| 2 | `npm run check:template-root` | ✅ | `[check-template-root] 通过：无「单根组件被注释撑成 Fragment」的情况。`（5 个刻意多根组件仅提示不阻断：BatchActionModals / RouteLoadingBar / JMagnet / AppHeader / SideNav） |
| 3 | `npx vue-tsc --noEmit` | ✅ exit 0 | 无任何输出 = 零类型错误 |
| 4 | `npx vite build --outDir dist-qa` | ✅ | `✓ 7624 modules transformed` / `✓ built in 44.31s` / `PWA generateSW precache 185 entries (4227.94 KiB)` / 产出 `dist-qa/sw.js`、`dist-qa/workbox-835c8c05.js`、`index.html`、`manifest.webmanifest`、`assets/*` |
| 5 | `npm run build` | ❌ 环境拦截 | 见下 |

**关于 `npm run build`：我独立复现了工程师的说法，结论一致。**

```
> vue-tsc && vite build
vite v6.4.3 building for production...
✓ 7624 modules transformed.
✗ Build failed in 40.60s
error during build:
[safe-delete][SAFE_DELETE_BULK_CONFIRM_REQUIRED]
{"count":173,"threshold":50,"scope":"turn","targets":[".../jnclub-web/dist/assets"],"targetCount":1}
    at checkBulkDeleteGuard (.../node-safe-delete-shim.cjs:243:19)
    at emptyDir (.../vite/dist/node/chunks/dep-Dm0c1Wj2.js:6855:19)
    at prepareOutDir (.../vite/dist/node/chunks/dep-Dm0c1Wj2.js:46403:7)
```

- `vue-tsc` **已通过**，7624 个模块**已全部 transform 成功**，失败点在 `prepareOutDir → emptyDir(dist)`，即**清空旧产物**这一步被本机 safe-delete 钩子拦截（`dist/assets` 有 173 个文件 > 阈值 50）。
- **这是环境限制，不是代码缺陷**。证据：`--outDir dist-qa`（新目录，无需 emptyDir）构建完全成功。
- `dist/` 未被破坏（仍为改造前的 172 个 assets + index.html），我事后确认过。

**「构建通过只证明语法正确」——所以下面才是重点。**

---

## 五、真实渲染验证（工程师明确未做）

### 5.1 方法

- 引擎：Playwright + WebKit 2370（`~/Library/Caches/ms-playwright/webkit-2370/pw_run.sh`），headless，`deviceScaleFactor: 1`。
- 登录：`localStorage['jn-token']` + 拦截 `/api/auth/userinfo` 返回 `{code:200}`，路由守卫正常放行。
- 数据：`page.route('**/api/**')` 拦截全部接口，按各 store 真实形状构造（`/api/bookmarks` 返回 12 条、`/api/directories` 返回 3 个目录、`/api/stats/summary` 返回完整 `StatsSummary`、`/api/stats/trend` 返回 6 个月…）。
- 场景：`/overview`、`/`（网格/列表）、`/?module=files`、`/?module=vault`、`/recycle`、`/notes/new`、`/welcome`（免登录）、`/extension`（免登录）× 亮/暗 × 1440×900 / 390×812。
- 所有场景 **console error = 0，pageerror = 0**。

### 5.2 无白屏（本轮最大风险）—— ✅ 通过

`card-top-bar` 等模板节点被删除，理论上可能在 `<template>` 根层级留下 HTML 注释 → 单根变 Fragment → `App.vue` 的 `<Transition mode="out-in">` 卡死 → 整页白屏且**无任何报错**。

实测 12 个场景**全部正常渲染**，`body.innerText` 均非空，且 `pageerror = 0`。`check:template-root` 也通过。**这一项工程师的风险意识是对的，结果也干净。**

### 5.3 CSS 变量生效 —— ✅ 全部非空

`document.documentElement` 实测（亮 / 暗）：

| 变量 | 亮 | 暗 |
| --- | --- | --- |
| `--surface-1` | `#FFFFFF` | `#26262A` |
| `--surface-border` | `#D8D8DE` | `#3F3F46` |
| `--surface-border-strong` | `#C7C7CC` | `#4A4A52` |
| `--surface-glow` | `rgba(236, 91, 142, 0.06)` | `rgba(255, 143, 171, 0.06)` |
| `--chart-series-1..4` | `#C9256A` / `#0A6FC2` / `#0D8261` / `#A76611` | `#FF8FAB` / `#5AA9F7` / `#42BD9A` / `#E09E48` |
| `--module-*-icon` | `#7C5CFF` / `#0A6FC2` / `#0D8261` / `#A76611` | 同基色 |
| `--bg-page` | `#F5F5F7` | `#000000` |

`--chart-series-2..4` 的 `var()` 别名在浏览器里**正确解析**成实际色值（不是字符串 `var(--module-note-icon)`），设计 §2.1 的「同块内解析」判断成立 ✅。

### 5.4 L1 实色断言 —— ✅ 全部 `rgb(...)` alpha=1

| 选择器 | 亮（computed） | 暗（computed） | alpha | blur |
| --- | --- | --- | --- | --- |
| `.j-page-body` | `rgb(255,255,255)` | `rgb(38,38,42)` | 1 | none |
| `.side-nav` | `rgb(255,255,255)` | `rgb(38,38,42)` | 1 | none |
| `.folder-column` | `rgb(255,255,255)` | `rgb(38,38,42)` | 1 | none |
| `.collection-column` | `rgb(255,255,255)` | `rgb(38,38,42)` | 1 | none |
| `.panel` | `rgb(255,255,255)` | `rgb(38,38,42)` | 1 | none |
| `.j-stat-card` | `rgb(255,255,255)` | `rgb(38,38,42)` | 1 | none |
| `.bookmark-card` / `.file-item` / `.vault-item` | `rgb(255,255,255)` | `rgb(38,38,42)` | 1 | none |

暗色 `#26262A` **就是 token 声明值**，不再是叠出来的 `#1B1B1E` ✅（PRD P0-1 验收 ⑤ 达成）。

### 5.5 同屏玻璃层数（`getComputedStyle().backdropFilter !== 'none'` 且尺寸 >0）

| 页面 | 实测 | PRD 目标 | 判定 |
| --- | --- | --- | --- |
| 概览（1440 亮/暗） | **0**（hover 图表后 = 1，`.trend-tip`） | ≤3 | ✅ |
| 收藏夹网格（1440） | **1**（`.glass-header`） | ≤2 | ✅ |
| 收藏夹列表（1440） | 1 | ≤2 | ✅ |
| 云盘（1440） | 1 | ≤2 | ✅ |
| 密码库（1440） | 1 | ≤2 | ✅ |
| 回收站（1440） | **0** | — | ✅ |
| 收藏夹（390） | **2**（`.glass-header` + `.mobile-tabbar`） | ≤2 | ✅ |
| 欢迎页 / 下载中心 | 0 | — | ✅ |

概览页从 14 层降到 **0 层**（hover tooltip 时才 1 层），比 PRD 的 ≤3 还严。**这一项工程师做得很干净，实测证实。**

### 5.6 关键对比度抽查（用真实 computed color 反算，不用 token）

| 项 | computed 值 | 我反算 | 脚本声称 | 判定 |
| --- | --- | --- | --- | --- |
| 暗 ΔL\*(`#000`→`#26262A`) | `pageBodyBg=rgb(38,38,42)` | **15.30** | 15.30 | ✅ 一致 |
| 暗 描边 vs 卡片底 | `rgb(63,63,70)` vs `rgb(38,38,42)` | **1.44** | 1.44 | ✅ |
| 亮 描边 vs 卡片底 | `rgb(216,216,222)` vs `rgb(255,255,255)` | **1.42** | 1.42 | ✅ |
| 暗 页面底光晕 | `rgba(255,143,171,0.06)` | alpha=0.06 | ≤6% | ✅ |

→ **工程师的对比度数字是真的**，我在浏览器里反算得到同样的值。

### 5.7 概览配重（P0-4）

| 指标 | PRD 目标 | 实测 | 判定 |
| --- | --- | --- | --- |
| 欢迎横幅高度（1440） | 96 → ≤76px | **65.3px** | ✅ |
| 统计卡数字 `font-weight` | 800 → 700 | **700** | ✅ |
| 统计卡标签 `font-size` | 12 → 13px | **13px** | ✅ |
| 统计卡标签颜色 | `--text-3` → `--text-2` | `rgb(81,81,84)` = `#515154` = `--text-2` | ✅ |
| 数字档位 | 维持 24px（决策 6） | **24px** | ✅ |
| **同屏字号档位** | **≤4** | **6 档：11/12/13/14/18/24** | ❌ **未达标** |
| 欢迎横幅高度（390） | ≤76px | 100.5px | ⚠ 移动端换行，PRD 未区分视口 |

**字号档位明细（实测，均来自 `.j-page-body` 内可见文本）**：

| 档位 | 数量 | 样本 |
| --- | --- | --- |
| 11px | 26 | `.trend-switch-btn`（3月/6月/12月）、`.trend-legend-item` |
| 12px | 23 | `n-button`（更多/布局/刷新）、`.greet-date`、`.greet-quote` |
| 13px | 12 | `.j-stat-label` |
| 14px | 7 | 快捷入口按钮 |
| 18px | 1 | `.greet-title` |
| 24px | 6 | `.j-stat-value` |

PRD 附录 11 章记录的改造前基线是「11/12/13/18/24/32 共 6 档」。**改造后仍是 6 档**（32 消失，14 出现）—— 即这一项**零改善**，而 PRD 8.1 与 P0-4 都写死「≤4」。

### 5.8 粉色承载元素数量（PRD G1 / 8.1 第一行）

口径：遍历可见元素，读 `getComputedStyle` 的 `backgroundColor` / 四边 `borderColor`，解析为 HSL 后色相 ∈[320°,355°] 且 S>15% 且 alpha>0.05。

| 页面 | 实测 | PRD 目标 | 判定 |
| --- | --- | --- | --- |
| 概览（1440） | **17**（剔除 2 个 danger 语义误判后 **15**） | ≤10 | ❌ |
| 收藏夹网格（1440） | **9** | ≤12 | ✅ |
| 收藏夹列表（1440） | 9 | ≤12 | ✅ |
| 云盘（1440） | **13** | ≤8 | ❌ |
| 密码库（1440） | 7 | ≤8 | ✅ |
| 收藏夹（390） | 6 | — | ✅ |

**概览页 17 个明细**（这决定了它是「口径问题」还是「真超标」）：

| 元素 | 数量 | 性质 |
| --- | --- | --- |
| `.greet-card` 左 3px 竖条 | 1 | ✅ 设计内（PRD 7.1 明写「左竖条」） |
| `.trend-switch-active` 底 `rgba(236,91,142,.08)` | 1 | ✅ A2 淡底 |
| `.trend-dot`（图例圆点）`#C9256A` | 1 | ✅ 图表首序列 |
| `.trend-bar`（6 根柱）`#C9256A` | 6 | ⚠ **口径争议**：PRD 7.1 把「图表首序列」记为 **1 处**，按 DOM 节点算就是 6 |
| `.disk-bar-wrap` 轨道 `rgba(236,91,142,.12)` | 3 | ⚠ 粉色轨道底，PRD 未点名 |
| `.disk-bar` 填充 `#EC5B8E` | 3 | ✅ A1 进度条 |
| `.j-stat-warn-dot` `rgb(239,91,107)` | 1 | ❌ **误判**：这是 `--danger` 语义红（色相 354.6°，擦边落进区间） |
| `.health-warn` `rgba(245,72,92,.1)` | 1 | ❌ **误判**：同上 |

- 按 **PRD 自己指定的 DOM 遍历口径**：17 > 10，**未达标**。
- 按 **PRD 7.1 图示的聚合口径**（图表序列算 1 处、进度条算 1 处）：约 **7–9 处**，**达标**。
- **结论：G1 这一行的口径本身有歧义**，需要 PM 明确「图表数据标记是否逐个计数」。但在口径澄清前，按 PRD 8.1 字面（DOM 元素计数）**只能判未达标**。
- 另外注意：PRD 7.1 期望「6 个图标之一（收藏）为粉」，实际 4 个模块图标都不是粉（收藏 = `#7C5CFF` 紫）。这是 DESIGN 的有意决定（`--module-bookmark-icon` 保持紫），但与 PRD 7.1 的文字不符。

**云盘页 13 个明细**：9 个 `.file-icon` 粉色底 + 1 个「上传文件」渐变 CTA + 侧栏激活项 + 目录 chip 激活 + …。
根因见 P1-4：`.file-icon` 仍用 `--glass-chip-bg`（粉 12%）作底。

### 5.9 移动端 390（P1-3）

| 指标 | PRD 目标 | 实测 | 判定 |
| --- | --- | --- | --- |
| 标签筛选区高度（折叠态） | ~200 → ≤100px | **80px** | ✅ |
| 首屏完整卡片数 | 1.5 → ≥2 | **3** | ✅ |
| TabBar 仍为玻璃 | `backdrop-filter` 生效 | ✅ `.mobile-tabbar` blur(12px) | ✅ |

但见 P2-9：标签 chip 实际是**换 2 行**（80px），不是设计 Q5=C 的「单行横滑」，机制被 `.tag-bar` 的 `flex-wrap: wrap` 覆盖掉了。

### 5.10 R4 回归风险抽查

| 项 | 做法 | 结果 |
| --- | --- | --- |
| 滚动容器裁切 | `.collection-column` computed `overflow-y: auto`、`padding: 20px 24px`，卡片距容器左 25px / 上 121px | ✅ 未见裁切 |
| 1px 缝隙 | 1440 与 390 截图逐张看卡片间隙、侧栏与内容区交界 | ✅ 未见缝隙/白边 |
| `contain: paint` 截断阴影 | `.folder-column` / `.collection-column` computed `contain: content`（= layout paint style），卡片 hover 阴影 4px 模糊、内距 20/24px | ✅ 阴影未被截断（内距足够） |
| 暗色卡片与页面底可辨 | `rgb(38,38,42)` on `#000` | ΔL\* **15.30** —— 见 P1-5：**低于 PRD 的 ≥16**，但肉眼可辨 |

### 5.11 我**没有**验证到的项（如实列出）

| 项 | 原因 |
| --- | --- |
| 粉色可见面积占比 ≤10% / 饱和粉 ≤2% | 需要逐像素 HSV 采样 + 改造前基线截图对比，本轮无基线 |
| 模块色可见面积 ≤3% | 同上 |
| 概览页首屏合成层数 | 需要 DevTools Layers / Performance 面板，headless 无法获取 |
| 24 张截图矩阵全量 | 已覆盖 12 个场景，未覆盖 ShareView / 弹窗抽屉四态 |
| 真实后端联调 | 后端 19005 未启动、JN_SSO 外部依赖不可用，全部 API 为 mock |

---

## 六、6 项「与设计文档的偏差」逐项判定

| # | 偏差 | 我的核查 | 判定 |
| --- | --- | --- | --- |
| 1 | `.glass-chip` 没删 | 实测 `PasswordEditorModal.vue:101`（`.lock-chip`）、`:154`（`.generator`）**确实在用**；DESIGN §4.3 写「模板零引用」是错的。另注：该类现在**已无 `backdrop-filter`**（`main.css:249-256` 只剩粉底粉边），所以「L3 合法玻璃」的辩护理由已不成立 —— 它现在只是个粉底 chip | ✅ **工程师对，设计文档错**。但类名 `glass-*` 已名不副实（P2 命名债） |
| 2 | 变量数 155 而非 156 | 实测改造前 `:root` **146** 个，改造后 **155** 个 = 146 + 12（新增）− 3（删除）。DESIGN 写的「146+10=156」**两处算错**：新增是 12 不是 10，且没扣掉 3 个删除 | ✅ **工程师对，设计文档算错** |
| 3 | 新增 token 12 个而非 10 个 | DESIGN §2.1 表格实际列了 **12 行**（#1–#12：surface×4 + module-icon×4 + chart×4），正文却写「10 个」。脚本 `NEW_TOKENS` 12 项**全部有消费** | ✅ **工程师对，设计文档自相矛盾** |
| 4 | 删 3 个死 token（`--gradient-top-bar`/`--glow-icon`/`--pink-white`） | 生成物与 `tokens.ts` 中三者均 **0 残留**；`--pink-white` 仅剩 `NoteCard.vue:183` 注释提及；`--glow-icon` 仅剩 `tokens.ts:28` 注释 | ✅ **合理，删除彻底** |
| 5 | 阶段 B 顺手改了 11 个「后续阶段才轮到」的文件 | 逐个看 diff：全部是 `--glass-chip-border` → `--surface-border` 的**单行替换**，共 11 处（DiskView 1 / NoteEditor 6 / Welcome 1 / VaultView / ReadingModal / SearchDrawer / DedupModal / PasswordEditorModal / FolderTree / DashboardView / JEmptyState）。**改动本身语义正确**，但带来两个副作用：① 这些 chip 的描边从「粉 28%」变成「`#D8D8DE` 中性」，在 `--glass-chip-bg`（粉 12%）底上对比度仅 **1.42:1**，chip 边界明显变淡；② **同一批文件里 `.file-icon` / `.item-icon` 只改了框、没改底**，留下「中性框 + 粉底」的混搭 | ⚠ **部分合理**：范围扩张本身没造成破版，但**改得不彻底**，是 P1-4 的直接来源 |
| 6 | `.trend-tip` 保留粉描边 | 实测 `backdrop-filter: blur(calc(var(--glass-blur) + 2px))` 生效、`z-index: 3`、`position: absolute`，命中 J2 判据；hover 时同屏玻璃从 0→1，未超 ≤3 | ✅ **合理**，且计入 7 处配额内 |

---

## 七、发现的问题（分级 + 文件:行号 + 复现）

### P0 · 必须修

**P0-1 · 三个页面仍保留 2 层 16%/14% 的粉色页面底光**

| 项 | 内容 |
| --- | --- |
| 位置 | `src/shared/views/Welcome.vue:149-152`（`.welcome-page`，光晕在 **:150**）<br>`src/shared/views/ExtensionPage.vue:185-188`（`.ext-page`，光晕在 **:186**）<br>`src/modules/bookmark/views/NoteEditorPage.vue:159-162`（`.note-editor-page`，光晕在 **:160**） |
| 代码 | `background: radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%), radial-gradient(900px 400px at 110% 120%, var(--glass-glow-bottom), transparent 60%), var(--bg-page);`<br>其中 `--glass-glow-top` 亮色 = `rgba(236,91,142,0.16)`、`--glass-glow-bottom` = 14% |
| 违反 | PRD 4.2「❌ 粉色作为页面底色 … 收敛为**最多 1 层**，且透明度 **≤6%**」；DESIGN §4.2「边缘页 … **去光晕**」 |
| 复现 | `node scripts/verify-visual-tokens.mjs` → `✗ 页面级（非 L3 浮层）品牌光晕 = 3`；或浏览器打开 `/jnclub/welcome` 读 `.welcome-page` 的 `background-image` |
| 影响 | Welcome 是未登录用户看到的第一屏；NoteEditor 是主应用内的真实页面。这是本轮「粉色退回指示器」的核心禁止项，**至今未做** |
| 备注 | 主壳 `.app-content` 已正确收敛为 1 层 6%（实测 `radial-gradient(... rgba(236,91,142,0.06) ...)`，`none` 结尾，确认只有 1 层）—— 所以问题是**遗漏**，不是方案错 |

### P1 · 应修

**P1-2 · 概览页同屏字号档位 6 档，PRD 要求 ≤4（零改善）**
- 位置：`DashboardView.vue`（`.trend-switch-btn` 11px、`.greet-date`/`.greet-quote` 12px、快捷入口 14px）
- 复现：`/overview` 下遍历 `.j-page-body` 可见文本的 `font-size` 去重 → `["11px","12px","13px","14px","18px","24px"]`
- 影响：PRD 8.1 与 P0-4 的双重硬指标；改造前基线也是 6 档，本项**没有任何推进**

**P1-3 · 概览页粉色承载元素 17 个，PRD G1 要求 ≤10**
- 复现：见 5.8 明细表
- 备注：其中 6 个是图表柱、3 个是进度条轨道，存在**口径歧义**（PRD 7.1 图示按「序列」聚合为 1 处）。建议 PM 先裁定口径，再决定是否要动

**P1-4 · 云盘页粉色承载元素 13 个，PRD 要求 ≤8；根因是列表行图标盒仍用粉色底**
- 位置：`DiskView.vue:708-721` `.file-icon { background: var(--glass-chip-bg); border: 1px solid var(--surface-border); }`
- 同病：`VaultView.vue:834-845` `.item-icon { background: var(--glass-chip-bg); color: var(--brand-text); }`
- 违反：PRD 4.2「❌ 粉色作为**所有图标盒底** … 改**模块色 soft 底 + 模块色压深图标**」
- 不一致：`CollectionCard` / `NoteCard` / `JStatCard` 的图标盒**已正确改为** `color-mix(in srgb, var(--module-*) 12%, var(--surface-1))` + `--module-*-icon`，只有云盘/密码库没改 —— 典型的「单页看不出、跨页才露」
- 附带：`DiskView.vue:712` 的注释已过期（仍写「底/框取 `--glass-chip-bg`/`--glass-chip-border`」，实际框早已是 `--surface-border`）
- 影响：`--glass-chip-bg` 全仓仍被 **45 处**消费，其中列表行图标盒是最显眼的违规

**P1-5 · 明度阶梯两项低于 PRD 原文判据，且被脚本用未获批的判据「转绿」**
- 亮 ΔL\* = **3.41** < PRD `[3.5, 6]`；暗 ΔL\* = **15.30** < PRD `≥16`
- 根因：Q1/Q6 决定不动 `--bg-page`（`#F5F5F7` / `#000000`），DESIGN 已预判并给出 O1/O2 放宽提案
- 需要：**PM/用户对 O1（≥3.2）、O2（≥15）明确拍板**，否则这两行验收永远标红。不能由脚本自行「视为达标」

**P1-6 · 暗色 `--module-vault` 对比度 6.57 超出 [4.5, 6.5] 上限，脚本把上界写成 6.6 让它过关**
- 位置：`tokens.ts:428` 附近 `darkTokens.moduleVault: '#E09E48'`
- 修复建议：改用 DESIGN §3.3 已给的 `#E59A38`（6.47），一行改动

**P1-7 · hover 反馈显著减弱（交互可发现性回归）**
- 位置：`main.css` 无关，问题在 `tokens.ts:250` `shadowCardHover: '0 4px 8px -2px rgba(0,0,0,0.06), 0 2px 4px -2px rgba(0,0,0,0.05)'`（**本轮未改**）
- 实测：hover 时 computed `box-shadow` 就是上面这串 6%/5% 黑，`transform: matrix(1,0,0,1,0,-1)`，`border-color` `#D8D8DE`→`#C7C7CC`（对比度 1.30→1.42）
- 问题：本轮把 hover 的**唯一强信号（粉边）**移除了，承接的阴影只有 6% 黑 + 1px 位移，在 `#FFF` 卡片叠 `#F5F5F7` 页面上几乎不可辨（截图对比可见差异极小）
- 这是 PRD P1-2 的设计意图（「亮色靠阴影抬升」），但**该意图依赖的阴影参数不足以承担**。建议把 `--shadow-card-hover` 亮色提到 10–12% 黑，或保留一丝品牌淡描边

### P2 · 可选

| # | 问题 | 位置 | 说明 |
| --- | --- | --- | --- |
| P2-8 | `--layout-sider-collapsed-width` 白名单掩盖双源 | `SideNav.vue:153` `:collapsed-width="64"` vs `tokens.ts:369` `'64px'` | 豁免合理，但改 token 无效果，建议注释写明联动 |
| P2-9 | 移动端标签 chip 未走 Q5=C 单行横滑 | `Home.vue:734` `.tag-bar { flex-wrap: wrap }` 覆盖 `JFilterBar.vue:70` `flex-wrap: nowrap` | 实测 390px 下 `.j-filter-bar.tag-bar` computed `flex-wrap: wrap`、2 行、`scrollWidth==clientWidth`（**完全没溢出**），横滑机制失效；同时 `mask-image` 右缘 12px 渐隐仍生效 → 无溢出却出现「还有更多」的假信号 |
| P2-10 | `ToolCurl.vue:406` 缺 `-webkit-backdrop-filter` | `modules/devtools/views/ToolCurl.vue:406` | 单行双写缺失，Safari 无 blur（devtools 范围，低风险） |
| P2-11 | `animation/utility.css` 的 `.backdrop-blur` 不在设计保留清单内 | `shared/components/animation/utility.css:47` | 硬编码 `blur(8px)` 全局工具类，未纳入 `VENDOR_HINTS`；玻璃规则实测 **14 条** vs 设计 **11 条**（+devtools 2 条 = 13，多出的正是这条） |
| P2-12 | 一行让全站 naive 控件 hover 变粉 | `main.css:384` `--n-border-hover: var(--glass-chip-border) !important` | 配额按「声明处」计数只算 1，但视觉上覆盖全站输入框/下拉；建议确认是否要保留 |
| P2-13 | `composite()` 不处理背景色自带 alpha | `verify-visual-tokens.mjs:77-87` | 当前无实际影响，属隐患 |
| P2-14 | `.glass-chip` 类名与实现不符 | `main.css:249-256` | 已无 blur，只剩粉底粉边；建议改名或补注释 |
| P2-15 | 390px 下欢迎横幅 100.5px > 76px | `DashboardView.vue:976` `.greet-card` | PRD 未区分视口；桌面 65.3px 达标，移动端换行后超标，可接受但需确认 |
| P2-16 | `contain: content` + `overflow: hidden` 可能裁切子元素焦点环 | `Home.vue:673-691` `.folder-column` | 未实测到实例，属观察项 |
| P2-17 | 硬编码「粉色」未被 hex 检查覆盖 | `shared/views/ExtensionPage.vue:195` `.ext-texture { background-image: radial-gradient(rgba(236, 91, 142, 0.08) 1px, transparent 1px) }` | PRD 8.1 只要求「硬编码 **hex** 不新增」，脚本的 `HEX_RE` 也只匹配 `#RRGGBB`，因此这条 rgba 粉点阵纹理合法地漏过。全仓非 token 的硬编码粉色 rgba 仅此 1 处，影响小，但说明「硬编码 = 0」的绿灯只覆盖 hex 形态 |

---

## 八、智能路由判定

| 对象 | 判定 | 依据 |
| --- | --- | --- |
| **源码（`src/`）** | **→ Engineer** | P0-1（3 处粉色页面底光）、P1-2（字号档位 6>4）、P1-3（概览粉元素 17>10）、P1-4（云盘 13>8，`.file-icon`/`.item-icon` 未改模块色）、P1-6（`#E09E48` → `#E59A38`）、P1-7（hover 阴影不足以承担 L2） |
| **复核脚本** | **→ QA（已自行修复）** | 1 条恒真断言、3 条判据被放宽、2 条口径错位、4 条关键项缺失。已改 7 处，断言从 42 → 46，现有 4 条如实报红 |
| **PRD 判据本身** | **→ PM** | G1「粉色承载元素」对图表数据标记的计数口径有歧义；O1/O2 需用户拍板 |
| **全部通过项** | **→ NoOne** | 构建链路、无白屏、L1 实色、玻璃收敛、模块色对比度、token 覆盖率、硬编码归零、hover 参数一致性、移动端密度、对比度数学 |

---

## 九、给 Engineer 的修复清单（按顺序）

1. **删掉 3 处页面级粉光晕**（P0-1）——`Welcome.vue:150`、`ExtensionPage.vue:186`、`NoteEditorPage.vue:160` 所在的 `background:` 整条改为 `var(--bg-page)`（参考 `Home.vue:655-658` 的 `.content-area` 做法，那里已正确删除）。改完 `npm run verify:visual` 的「页面级品牌光晕」应变 0。
2. **`.file-icon` / `.item-icon` 改模块色 soft 底**（P1-4）——照抄 `CollectionCard.vue:242-250` 的 `.favicon-box`：`background: color-mix(in srgb, var(--module-file) 12%, var(--surface-1)); color: var(--module-file-icon);`（云盘若要保留文件类型语义，可把 `--module-file` 换成对应的 `--file-type-*`）。顺手修掉 `DiskView.vue:712` 的过期注释。
3. **暗色 `--module-vault` 改 `#E59A38`**（P1-6）——`tokens.ts` darkTokens，一行。
4. **概览页收字号档位到 ≤4**（P1-2）——最省事的路径：把 `.trend-switch-btn` / `.trend-legend-item` 的 11px 并入 12px 档，快捷入口 14px 并入 13px 档，即得 `12/13/18/24` = 4 档。
5. **`--shadow-card-hover` 亮色加重**（P1-7）——建议 `0 6px 16px -4px rgba(0,0,0,0.10), 0 2px 6px -2px rgba(0,0,0,0.06)`。
6. （可选）`.tag-bar` 的 `flex-wrap: wrap` 加 `@media (max-width:767px){ flex-wrap: nowrap }`，与 `JFilterBar` 的 Q5=C 对齐（P2-9）。

---

## 十、附：本次验证的资产

| 资产 | 路径 | 说明 |
| --- | --- | --- |
| 修正后的复核脚本 | `jnclub-web/scripts/verify-visual-tokens.mjs` | 46 条断言，4 条如实报红；`--strict` 退出码 1 |
| 渲染验证脚本 | `/tmp/pwqa/{lib,run1..run8}.mjs` | Playwright + API mock，可复跑 |
| 截图 | `/tmp/pwqa/{light,dark}-{overview,home}.png`、`light-{welcome,extension,disk,vault}.png`、`mobile-home.png`、`hover-card.png` | 12 个场景 |
| 临时产物 | `jnclub-web/dist-qa` | 验证后已删除；`dist/` 保持改造前原状（172 assets） |

> 本次验证**未修改任何 `src/` 业务代码**，`git status` 仅 `scripts/verify-visual-tokens.mjs` 一处 M。

---

## 十一、追加验证：`var()` 字符串别名在 naive / JS 侧是否会失效

> 触发：team-lead 在复核 token diff 时提出。`tokens.ts:285-287` 的图表色 2–4 是**字符串别名**而非 hex：
> ```ts
> chartSeries2: 'var(--module-note-icon)',
> chartSeries3: 'var(--module-file-icon)',
> chartSeries4: 'var(--module-vault-icon)',
> ```
> 担心点：naive 的 `themeOverrides` 是 TS 对象、读原始值；若把别名当颜色传给 naive / canvas / ECharts，浏览器无法解析 → 静默失效。

### 11.1 结论：**不失效，P0 不成立**。三条路径全部查过，别名只走 CSS，从未被当成 JS 颜色值。

### 11.2 静态：别名只有 1 个消费方

```
$ grep -rn "chartSeries" --include=*.ts --include=*.vue src/
themes/tokens.ts:74-77    接口声明
themes/tokens.ts:281/285-287   亮色定义
themes/tokens.ts:456/457-459   暗色定义
themes/tokens.ts:620-623       tokensToCSSVars() 透传  ← 唯一消费点
```

- `themes/light.ts` / `themes/dark.ts`：**零引用**（`grep "chartSeries\|chart-series"` 无结果）。naive 的 `GlobalThemeOverrides` 里没有任何字段吃这三个 token。
- 全仓**没有任何** TS/JS 侧代码把 `chartSeries2/3/4` 当颜色用（无 canvas、无 ECharts 配置、无 `getComputedStyle` 取色逻辑）。

### 11.3 DashboardView 走的是 CSS 变量（style 绑定），不是 JS 取色

`DashboardView.vue:105-110` 定义 `color: 'var(--chart-series-N)'`，模板三处消费全是 **`:style` 绑定**：

| 行 | 绑定 |
| --- | --- |
| `:406` | `<i class="trend-dot" :style="{ background: s.color }" />` |
| `:429` | `<i class="trend-tip-dot" :style="{ background: s.color }" />` |
| `:438` | `class="trend-bar" :style="{ height: …, background: s.color }"` |

运行时探测：`document.querySelectorAll('canvas').length = 0`，图表容器内 **0 个 `<svg>`**（全页 40 个 svg 全是 lucide 图标）。→ **纯 DOM + CSS，别名在这里完全安全。**

### 11.4 运行时：消费端 computed 值确实是颜色（这才是有效验证）

team-lead 提醒得对 —— `getPropertyValue('--chart-series-2')` 在规范上返回的是**替换后**的 computed 值，单看它不够。所以我直接量**消费端**：

| 序列 | inline style 字面量 | 亮色 computed | 暗色 computed | 判定 |
| --- | --- | --- | --- | --- |
| `.trend-dot` #1 | `background: var(--chart-series-1)` | `rgb(201, 37, 106)` = `#C9256A` | `rgb(255, 143, 171)` = `#FF8FAB` | ✅ |
| `.trend-dot` #2 | `background: var(--chart-series-2)` | `rgb(10, 111, 194)` = `#0A6FC2` | `rgb(90, 169, 247)` = `#5AA9F7` | ✅ |
| `.trend-dot` #3 | `background: var(--chart-series-3)` | `rgb(13, 130, 97)` = `#0D8261` | `rgb(66, 189, 154)` = `#42BD9A` | ✅ |
| `.trend-dot` #4 | `background: var(--chart-series-4)` | `rgb(167, 102, 17)` = `#A76611` | `rgb(224, 158, 72)` = `#E09E48` | ✅ |
| `.trend-bar` ×4 | 同上 | 同上，逐根对齐 | 同上 | ✅ |

**四根柱子 + 四个图例点，亮暗两种主题，全部拿到正确颜色**，没有出现「回落成继承色 / 透明 / 黑」的症状。

补充证据：`--chart-series-2` 的自定义属性 computed 值在 WebKit 里也确实是 `#0A6FC2`（不是字面量 `var(...)`）—— CSS 自定义属性的 computed value 会做 var() 替换，这与 team-lead 的担心相反，但**即使它返回字面量也不影响结论**，因为消费端已经证明可用。

### 11.5 残余风险（P2，非本轮问题）

- 这套别名依赖「`--module-note-icon` 与 `--chart-series-2` 必须在同一个 `:root` 块内可见」。目前 4 个块（`:root` / `[data-theme=dark]` / `@media prefers-color-scheme` 两套）都满足。
- 若将来有人把 `--chart-series-*` 挪到**另一个文件**或**另一个选择器作用域**（例如只定义在 `.app-content` 里），而 `--module-*` 在 `:root`，则仍是安全的（继承方向 OK）；反之若把 `--module-*` 收窄到子作用域，别名就会失效。建议在 `tokens.ts:283` 附近补一句约束注释。
- **不建议改成内联 hex**：别名是本轮「图表色 = 图标色」的唯一保证（DESIGN §3.3 的设计意图），内联会引入双源。当前实现是正确选择。

### 11.6 顺带复核工程师提出的 5 个抽查点

| # | 工程师的说法 | 我的实测 | 判定 |
| --- | --- | --- | --- |
| 1 | 六类卡/行 hover 逐项同参数 | `CollectionCard` / `NoteCard` / `CollectionRow` / `NoteRow` / `.vault-item` / `.file-item` / `.j-stat-card` 七处均为 `translateY(-1px)` + `border-color: var(--surface-border-strong)` + `box-shadow: var(--shadow-card-hover)`，**逐字一致** | ✅ 属实 |
| 2 | 侧栏已真的变白（`main.css` 的 `!important` 补丁已整条删除） | `grep -n "side-nav" main.css` → **0 命中**（只剩 `:480` 一段历史注释）；`.side-nav` computed `backgroundColor = rgb(255, 255, 255)`，alpha=1 | ✅ 属实，且这条「改了没反应」的高危点确实被清干净了 |
| 3 | `.greet-card` 的 `backdropFilter` 应为 `none` | 实测 `greetBlur = "none"`，`backgroundColor = rgb(255,255,255)` | ✅ 属实 |
| 4 | 密码揭示气泡（NPopover）仍应是玻璃 | 直接打开 NPopover 未成功（需密码库解锁；`更多/布局` 的 NDropdown 在 mock 下未触发），但：① `main.css:349-352` 的 `.n-popover` L3 规则**原文完好**（含双写 `backdrop-filter`）；② 我打开了一个真实 `.n-dialog` 实测 `backdrop-filter: blur(12px)` + `background-color: rgba(255,255,255,0.72)` + 品牌光晕，**同一批 L3 全局规则生效** | ⚠ 机制完好（间接证据），**未直接打开 NPopover**，如实标注 |
| 5 | 390 标签区单行横滑、高度 ≤100px、首屏 ≥2 卡 | 高度 **80px** ✅、首屏完整卡 **3 张** ✅；但**「单行横滑」只对了一半**：`.chip-bar`（目录）确实 `nowrap` + `scrollWidth 315 > clientWidth 310`；**标签用的 `.j-filter-bar.tag-bar` 实测 `flex-wrap: wrap`、2 行、`scrollWidth == clientWidth`（完全没有溢出）** —— 被 `Home.vue:734` 的 `.tag-bar { flex-wrap: wrap }` 覆盖了（见 P2-9） | ⚠ 指标达标，但机制失效 |

> 另注：工程师说「24 张截图矩阵我没能产出（沙箱无浏览器自动化）」—— **这个前提不成立**。本机已有 `~/Library/Caches/ms-playwright/webkit-2370`，`npm i playwright-core` 后即可 headless 驱动；后端未启动也不影响，`page.route()` 拦截 `/api/**` 即可。我已用这套方法产出 12 个场景的截图与量化数据，脚本留在 `/tmp/pwqa/`，可直接复用扩到 24 张。

---

## 十二、Round 2 回归验证（工程师修复后）

> 验证对象：`a2d6fb6`（P0-1）+ `e6772de`（P1-2/3/4/6/7、P2-9）+ `497313f`（收录修正后的复核脚本）
> 验证方式：与 Round 1 完全同一套 —— `gen:theme` / `check:template-root` / `vue-tsc` / `vite build --outDir dist-qa` + 同一套 Playwright 渲染脚本（12 场景）。

### 12.1 结论：**P0 已闭环，P1 六项中五项已修，剩 1 项是口径问题需 PM 裁定**

| # | 问题 | Round 1 实测 | Round 2 实测 | 状态 |
| --- | --- | --- | --- | --- |
| P0-1 | 三页粉色页面底光 | 2 层 16%/14%（`background-image` 有值） | **`background-image: none`**（Welcome / Extension / NoteEditor 三页逐页确认） | ✅ 已修 |
| P1-2 | 概览同屏字号档位 | 6 档 `11/12/13/14/18/24` | **4 档 `12/13/18/24`** | ✅ 已修 |
| P1-3 | 概览粉色承载元素 | 17（剔 danger 误判 15） | 14（剔 danger 误判 **12**） | ◐ 见 12.3 |
| P1-4 | 云盘粉色承载元素 | 13 | **1** | ✅ 已修 |
| P1-5 | ΔL\* 两项低于 PRD | 3.41 / 15.30 | 3.41 / 15.30（未变） | ◐ 见 12.4 |
| P1-6 | 暗色 `--module-vault` 6.57 | 6.57 | **6.47**（`#E59A38`） | ✅ 已修 |
| P1-7 | hover 阴影不可辨 | 6%/5% 黑 + 8px | **10%/6% 黑 + 16px** | ✅ 已修 |
| P2-9 | 移动端标签条未横滑 | `wrap`、2 行、无溢出 | **`nowrap`、1 行、`scrollWidth > clientWidth`** | ✅ 已修 |

其余各项同步改善（同一轮渲染实测）：

| 页面 | Round 1 粉色元素 | Round 2 | 目标 |
| --- | --- | --- | --- |
| 收藏夹网格 | 9 | **7** | ≤12 ✅ |
| 密码库 | 7 | **5** | ≤8 ✅ |
| 云盘 | 13 | **1** | ≤8 ✅ |
| Welcome | 7 | **1** | — |
| 回收站 | 1 | 1 | — |

未回退项（重点确认「修 A 没弄坏 B」）：

| 项 | Round 2 实测 |
| --- | --- |
| 无白屏 / console error / pageerror | 7 个页面全部 **0 / 0 / 0** |
| 同屏玻璃层 | 概览 0、收藏 1、云盘 1、密码库 1、390 收藏 2 —— 与 Round 1 一致 |
| L1 实色 | 仍全部 `rgb(...)` alpha=1 |
| hover 三项一致性 | **7 处全部 `translateY(-2px)`**（工程师把全仓 `-1px` 统一改成 `-2px`，一致性未被破坏） |
| greet-card 高 / stat 字重 / 标签字号 | 65.3px / 700 / 13px，未变 |
| `vue-tsc --noEmit` | **exit 0** |
| `vite build --outDir dist-qa` | **✓ built in 45.02s**，PWA 185 预缓存 |

> 构建过程中 `vite build` 首次失败过一次，报 `connect ECONNREFUSED .../broker.sock`（rollup 读 lucide 图标文件时沙箱 broker 瞬断）。**重跑即成功**，与代码无关，已确认是环境抖动。

### 12.2 逐项复核证据

**P0-1**：浏览器实测三页的 `background-image` 均为 `none`
```
welcome-page        bgImage: "none"   （Round 1: radial-gradient(...0.16...), radial-gradient(...0.14...)）
ext-page            bgImage: "none"
note-editor-page    bgImage: "none"
```
主壳 `.app-content` 仍保留唯一 1 层 6%：`radial-gradient(1200px 500px at 10% -10%, rgba(236, 91, 142, 0.06), ...), none`。→ 「光晕只此一处」成立。

**P1-2**：`.j-page-body` 内可见文本字号去重 = `["12px","13px","18px","24px"]`，正好 4 档。做法是 `.trend-switch-btn` / `.trend-legend` / `.trend-y-label` / `.trend-month` / `.recent-time` 由 `--fs-xs`(11px) 提到 `--fs-sm`(12px)，`.quick-btn` 由 n-button 默认 14px 压到 `--fs-md`(13px)。

**P1-4**：`.file-icon` / `.item-icon` 改为 `color-mix(in srgb, var(--module-file|vault) 12%, var(--surface-1))` + `color: var(--module-*-icon)`。截图确认云盘 9 个图标盒由「粉底粉图标」变为「绿 soft 底 + 绿图标」。云盘粉色元素 13 → **1**（只剩「上传文件」CTA）。

**P1-7**：hover 时 computed `box-shadow = rgba(0,0,0,0.1) 0px 6px 16px -4px, rgba(0,0,0,0.06) 0px 2px 6px -2px`，`transform = matrix(1,0,0,1,0,-2)`。截图对比可见卡片确实「浮起来了」。

### 12.3 P1-3 的现状：**已经不是实现问题，是口径问题**

Round 2 概览页 14 个粉色元素的完整构成：

| 元素 | 数量 | 性质 |
| --- | --- | --- |
| `.greet-card` 左 3px 竖条 | 1 | PRD 7.1 明写允许 |
| `.trend-switch-active` 底 `rgba(236,91,142,.08)` | 1 | A2 淡底，允许 |
| `.trend-dot` 图例圆点 `#C9256A` | 1 | 图表首序列 |
| `.trend-bar` **6 根柱** `#C9256A` | 6 | **同一序列的 6 个数据标记** |
| `.disk-bar` 3 根进度条 `#EC5B8E` | 3 | A1 进度条，允许 |
| `.j-stat-warn-dot` / `.health-warn` | 2 | ❌ 我的检测器误判：`--danger` 语义红（色相 354.6° 擦边） |

- **按 DOM 遍历口径（PRD 8.1 字面）**：12（剔除误判）> 10 → 仍超 2 个，**超出的 6 个全部是同一序列的 6 根柱**。
- **按 PRD 7.1 图示的聚合口径**（「图表首序列」记 1 处、「进度条」记 1 处）：竖条 1 + 开关 1 + 图表 1 + 进度条 1 = **4 处** → 达标。
- **结论**：工程师已把能降的都降了（15 → 12，减少的是 `.disk-bar-wrap` 的粉轨道）。剩下的是纯口径分歧，**改代码解决不了**（除非把 6 根柱画成一个图形，那会毁掉图表语义）。
- **需要 PM 裁定**：G1 的「粉色承载元素」是否逐个 DOM 节点计数？若是，建议把概览的阈值从 ≤10 调整为 ≤14 并写明理由（图表首序列按柱数计）。

### 12.4 P1-5 的现状：**数学上不可达，需要主理人书面确认**

`tokens.ts` 现在有一段注释声明「判据经主理人 2026-10-22 批准调整为：亮 ≥3.2、暗 ≥15」。

**我无法验证这个批准**（我的收件箱里没有这条记录，team-lead 给我的消息只问过 `var()` 别名）。所以我的处理是：

1. 脚本**默认仍按 PRD 原文判据**判 → 报 `✗ 2/46`、`--strict` 退出码 1。绝不自动转绿。
2. 新增 `--accept-approved` 开关：只有显式加这个参数，这两项才降级为 `◐ 已批准偏差`，输出里保留原文目标值 + 「需主理人书面确认」字样，且**单独计数、永不计入「达标项」**。
   ```
   默认：            ✗ 2/46 项未达标                              strict 退出码 1
   --accept-approved：✓ 44 项达标 + 2 项已批准偏差（不计入达标）   strict 退出码 0
   ```
3. 请主理人确认后，把 `--accept-approved` 加进 `package.json` 的 `verify:visual`，或直接回复我，我改。

### 12.5 Round 2 新增的两条 P2 观察（非回归，本轮未动）

| 页面 | 现象 | 判定 |
| --- | --- | --- |
| `/extension` | 25 个粉色承载元素：11 个图标盒（`--brand-soft` 8% 底）+ 5 个 `.n-step-indicator` + 5 个 `.step-num` + 2 个 `n-alert` | **P2**。该页不在 PRD G1 的配额清单（只有概览/收藏/云盘/密码库），且下载中心不属于任何模块、没有模块色可用，用品牌淡底作装饰可辩护。但步骤条「5 步 × 2 层粉 = 10 个粉块」在视觉上确实偏重，建议后续收一档 |
| `/welcome` | 6 个图标盒已由「粉底」改为「中性灰底 + 粉图标」（`color: var(--brand-text)`），底色不再是粉 ✅；但 6 个图标本身仍是粉 | **P2**。Welcome 无模块归属，PRD 7.1 期望的「6 个图标之一」在此页不适用；若认为 6 个粉图标偏多，可改为 `--text-2` 图标 + 单一粉色 CTA |

### 12.6 Round 2 后的路由判定

- **→ NoOne（已闭环）**：P0-1、P1-2、P1-4、P1-6、P1-7、P2-9、P2-8、P2-17，以及构建链路、无白屏、L1 实色、玻璃收敛、hover 一致性、对比度数学、token 覆盖率、硬编码归零。
- **→ PM**：P1-3 的 G1 计数口径（DOM 节点 vs 聚合）。
- **→ 主理人**：P1-5 的 O1/O2 批准确认（确认后把 `--accept-approved` 写进 `verify:visual`）。
- **→ Engineer（仅 P2，不阻塞）**：Extension 步骤条粉块密度、Welcome 6 个粉图标。

**总体：从「有条件通过」升为「通过（附 2 项待裁定）」。**
