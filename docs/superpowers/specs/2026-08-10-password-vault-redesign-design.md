# JNClub 密码库：列表修复 + 玻璃拟态编辑弹窗设计

日期：2026-08-10
状态：已获用户确认（"可以试一下"）

## 1. 背景与目标

JNClub 密码库（Vault）存在两个用户可见问题，同时需要按规格重构编辑弹窗视觉：

1. **眼睛图标无功能**：列表行点击眼睛后 `revealPwd` 已成功拉取明文并写入 `item.password`，但行模板从不渲染密码文本（只有一个写死的"密码已加密"标签），界面无任何变化。
2. **复制按钮不可用**：`copyText` 只调用 `navigator.clipboard.writeText`，该 API 仅存在于安全上下文（HTTPS / localhost）。生产环境走明文 HTTP（`http://jiangnan.88933.vip`），`navigator.clipboard` 为 `undefined`，且无降级方案，复制必然失败。
3. **编辑弹窗重构**：现为单列朴素表单（长度用 NSelect），需改为"固定紫蓝暗色玻璃拟态 + 双列表单 + 密码长度滑块 + 绿色随机生成按钮"的现代密码管理器风格。

## 2. 范围

- 前端仅限 `jnclub-web/src`：VaultView.vue 拆分重构、新增共享工具与弹窗组件。
- 后端**无改动**（API 契约不变：`/api/vault` CRUD + detail 返回明文密码、编辑留空=保持不变）。
- 弹窗视觉固定为暗色紫蓝玻璃，不随应用亮/暗主题变化（已确认）。
- 列表密码展示方式：眼睛点击后在行上**浮层气泡**显示密码（已确认）。

## 3. 设计决策

| 项 | 决策 | 理由 |
|---|---|---|
| 弹窗主题 | 固定紫蓝暗色玻璃拟态 | 与 1Password 等密码管理器一致；应用主题是 Apple Pink，混用会冲突 |
| 列表密码展示 | NPopover 浮层气泡 | 防偷窥、不撑大行高；点外部自动收起 |
| 弹窗外壳 | `NModal`（无 preset）自绘玻璃卡片 | 白拿遮罩/ESC/过渡，同时完全掌控视觉 |
| 复制 | 共享 `clipboard` 工具 + execCommand 降级 | 解决 HTTP 生产环境复制失败 |
| 随机密码 | 沿用 `crypto.getRandomValues`（安全随机） | 已有实现，仅改交互（滑块 + 绿色 pill） |

## 4. 架构与文件

```
jnclub-web/src/
├── shared/utils/clipboard.ts              # [新增] copyText()：clipboard API + execCommand 降级
└── modules/bookmark/
    ├── components/VaultView.vue           # [修改] 移除弹窗与生成逻辑，改接弹窗组件；列表加气泡
    ├── components/PasswordEditorModal.vue # [新增] 玻璃拟态编辑弹窗（新建/编辑共用）
    └── components/PasswordRevealPopover.vue # [新增] 密码展示气泡（含复制按钮）
```

### 4.1 clipboard.ts（共享工具）

```ts
export async function copyText(text: string): Promise<boolean>
```

- 先试 `navigator.clipboard?.writeText`（安全上下文）
- 失败/不存在时降级：临时 `<textarea>` + `document.execCommand('copy')` + 移除元素
- 返回成功与否；调用方负责 message 提示

### 4.2 PasswordEditorModal.vue（弹窗组件）

Props: `show: boolean`、`editingId: number | null`、`directoryId: number`（必填）、`initial?: {name, username, url, notes}`
Emits: `update:show`、`saved`
内部状态：`form {name, username, password, url, notes}`、`showPwd`、`pwdLength`（默认 16，范围 6–64）

布局（严格按规格）：
```
┌─────────────────────────────────────────────┐
│  🔒 编辑密码条目                       [×]  │  ← 标题行（锁图标 + 标题 + 关闭）
├─────────────────────────────────────────────┤
│  名称 [    ]     密码 [•••••] 👁            │
│  账号 [    ]     长度 ──●────  16           │  ← 双列 grid（左 名称/账号/站点，右 密码/长度/生成）
│  站点地址 [    ] [ 随机生成 ](绿色 pill)     │
│  备注 [ textarea（通栏）                    ]│
├─────────────────────────────────────────────┤
│                          [取消][确认(渐变)]  │  ← 右下角
└─────────────────────────────────────────────┘
```

视觉规格：
- 背景：深底紫蓝渐变（径向/线性），`backdrop-filter: blur(20px)`，1px 半透明白边，柔和投影
- 输入框：8px 圆角、内阴影、轻边框；聚焦品牌紫辉光（focus glow）
- 滑块：`NSlider` + 右侧数字徽章（显示当前长度）
- 随机生成：绿色 pill（`border-radius: 999px`），点击生成并自动显示密码
- 按钮：取消 = 半透明 ghost；确认 = 紫蓝渐变，hover 微亮
- 微交互：hover 上浮/边框提亮、按下反馈、渐变过渡

行为：
- 新建：表单清空；编辑：回填 name/username/url/notes，password 留空（placeholder"留空保持不变"）
- 提交校验：name 必填、directoryId 存在；payload 同现有 `{directoryId, name, username, password, url, notes}`
- 成功后 `emit('saved')`，父组件关闭并刷新列表
- 生成逻辑沿用：字符集 `A-Za-z0-9!@#$%^&*()_+-=`（去掉易混字符），`crypto.getRandomValues`，长度 = pwdLength

### 4.3 PasswordRevealPopover.vue（列表气泡）

- 包在行内眼睛按钮外层；仅在激活（已拉取明文）时展示
- 内容：等宽字体显示密码 + 绿色复制按钮（调 `copyText`，成功提示"密码已复制"）
- 点击气泡外区域自动关闭（NPopover 默认行为）
- 眼睛按钮图标随状态 Eye/EyeOff 切换

### 4.4 VaultView.vue（修改）

- 删除内置弹窗模板（199-245 行）、`showModal/showPwd/pwdLength/generatePwd/submit` 相关逻辑
- 替换为 `<PasswordEditorModal>`，`openCreate`/`openEdit` 传入 initial，`@saved` 触发 `load()`
- 列表眼睛按钮：改为 `PasswordRevealPopover` 包裹；点击时 `fetchDetail` 拉明文（复用 `revealPwd` 逻辑，改存到独立 `revealedPwd` state 而非污染 item）
- 复制按钮：改用 `copyText`（若密码未拉取则先拉取再复制）
- 保持行卡片样式、drag-sort、删除确认等其余行为不变

## 5. 数据流

```
VaultView ──openCreate/openEdit──▶ PasswordEditorModal
        ◀──saved（触发 load() 刷新）──
VaultView ──revealPwd──▶ stores/vault.fetchDetail(id) ──▶ 明文
        ──copyPwd──▶ utils/clipboard.copyText(明文)（无则先 fetchDetail）
```

## 6. 错误处理

- fetchDetail 失败：message.error（气泡不显示密码）
- 复制失败：message.error（降级也失败时提示手动复制）
- 表单提交失败：message.error(e.message)，弹窗保持打开
- 非安全上下文复制：由降级路径兜底，无需用户感知

## 7. 验证

1. `cd jnclub-web && npx vue-tsc --noEmit` 零错误；`npm run build` 通过
2. 浏览器实测（本地 dev，HTTP 环境）：
   - 列表点眼睛 → 气泡显示明文密码，外部点击/再次点眼睛收起
   - 气泡内复制 → 成功提示（验证 execCommand 降级生效）
   - 弹窗：双列布局、滑块调长度、绿色生成按钮产出对应长度密码、焦点辉光
   - 编辑已有条目：密码留空提交后密码不变（后端保持）
3. 上线：`git push` 后走已配置的 auto-deploy（服务器自构建自部署），验证线上列表/弹窗功能

## 8. 不做（YAGNI）

- 不引入密码强度检测/历史记录
- 不重构后端加密存储
- 不改成暗色跟随主题（已确认固定玻璃风格）
- 不新增独立 API 模块（现有 store 直连 axios 模式保持）
