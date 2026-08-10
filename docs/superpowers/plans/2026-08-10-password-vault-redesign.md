# 密码库列表修复 + 玻璃拟态编辑弹窗 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 JNClub 密码库列表"眼睛图标无功能 / 复制不可用"两个 bug，并将编辑弹窗重构为固定紫蓝暗色玻璃拟态（双列表单 + 密码长度滑块 + 绿色随机生成按钮）。

**Architecture:** 拆出 3 个独立单元：共享 `copyText` 工具（clipboard API + execCommand 降级，解决 HTTP 生产环境复制失败）、`PasswordRevealPopover`（列表密码气泡显示组件，解决眼睛无渲染问题）、`PasswordEditorModal`（自绘玻璃弹窗）。`VaultView.vue` 只保留列表与编排逻辑。后端零改动。

**Tech Stack:** Vue 3 `<script setup lang="ts">`、Naive UI（NPopover/NModal/NInput/NSlider/NButton/NIcon）、lucide-vue-next、Pinia（现有 vault store）。

## Global Constraints

- 后端**零改动**；API 契约不变（`/api/vault` CRUD、detail 返回明文、编辑留空=保持不变）
- 不新增 npm 依赖
- 类型检查必须通过：`npx vue-tsc --noEmit` 零错误（项目 build 脚本 = `vue-tsc && vite build`，无单测框架，以类型检查 + 构建 + 浏览器实测为验证门禁）
- 列表行视觉沿用现有 Apple Pink token（`--bg-card`/`--border`/`--brand` 等）；**仅弹窗**为固定紫蓝暗色玻璃，不随主题
- UI 文案保持中文，与现有一致（"密码已复制"、"复制失败"、"获取密码失败"等）
- 文件路径：`jnclub-web/src/` 下

---

### Task 1: 共享复制工具 clipboard.ts

**Files:**
- Create: `jnclub-web/src/shared/utils/clipboard.ts`

**Interfaces:**
- Consumes: 无（纯工具，零依赖）
- Produces: `copyText(text: string): Promise<boolean>` — 返回是否复制成功；Task 2 / Task 3 的 VaultView 使用

- [ ] **Step 1: 创建文件**

```ts
// 剪贴板工具：优先 navigator.clipboard（安全上下文），
// 否则降级 document.execCommand('copy')（明文 HTTP 生产环境可用）
export async function copyText(text: string): Promise<boolean> {
  if (!text) return false
  // 方式一：Clipboard API（仅 HTTPS / localhost 安全上下文存在）
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    /* 落到降级路径 */
  }
  // 方式二：隐藏 textarea + execCommand（HTTP 环境兜底）
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}
```

- [ ] **Step 2: 类型检查**

Run: `cd jnclub-web && npx vue-tsc --noEmit`
Expected: 通过（零错误）

- [ ] **Step 3: 提交**

```bash
git add src/shared/utils/clipboard.ts
git commit -m "feat(web): 新增共享剪贴板工具（clipboard API + execCommand 降级）"
```

---

### Task 2: 密码列表气泡组件 PasswordRevealPopover.vue

**Files:**
- Create: `jnclub-web/src/modules/bookmark/components/PasswordRevealPopover.vue`

**Interfaces:**
- Consumes: `VaultItem`（`../stores/vault`）、`copyText`（`../../../shared/utils/clipboard`）、vault store `fetchDetail(id)`
- Produces: 组件 `PasswordRevealPopover`，Props `{ item: VaultItem }`；Task 3 的 VaultView 用它替换列表眼睛按钮

- [ ] **Step 1: 创建文件**

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { NPopover, NButton, NIcon, useMessage } from 'naive-ui'
import { Eye, EyeOff, Copy } from 'lucide-vue-next'
import { useVaultStore, type VaultItem } from '../stores/vault'
import { copyText } from '../../../shared/utils/clipboard'

const props = defineProps<{ item: VaultItem }>()

const vaultStore = useVaultStore()
const message = useMessage()

const revealed = ref(false)
const pwdText = ref('')

// 打开时按需拉取明文（缓存已取过则不重复请求）
watch(revealed, async (v) => {
  if (!v || pwdText.value) return
  try {
    const detail = await vaultStore.fetchDetail(props.item.id)
    pwdText.value = detail.password || ''
  } catch (e: any) {
    message.error(e.message || '获取密码失败')
    revealed.value = false
  }
})

const onShowChange = (v: boolean) => {
  revealed.value = v
}

const onCopy = async () => {
  if (await copyText(pwdText.value)) message.success('密码已复制')
  else message.error('复制失败')
}
</script>

<template>
  <NPopover
    :show="revealed"
    trigger="click"
    placement="right"
    :show-arrow="true"
    @update:show="onShowChange"
  >
    <template #trigger>
      <NButton quaternary circle size="small" :title="revealed ? '隐藏密码' : '显示密码'">
        <template #icon><NIcon :component="revealed ? EyeOff : Eye" size="16" /></template>
      </NButton>
    </template>
    <div class="pwd-pop">
      <code class="pwd-text">{{ pwdText || '…' }}</code>
      <NButton size="tiny" type="primary" @click="onCopy">
        <template #icon><NIcon :component="Copy" size="13" /></template>
        复制
      </NButton>
    </div>
  </NPopover>
</template>

<style scoped>
.pwd-pop {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 300px;
}
.pwd-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: #fff;
  word-break: break-all;
  user-select: all;
}
</style>
```

- [ ] **Step 2: 类型检查**

Run: `cd jnclub-web && npx vue-tsc --noEmit`
Expected: 通过（零错误）

- [ ] **Step 3: 提交**

```bash
git add src/modules/bookmark/components/PasswordRevealPopover.vue
git commit -m "feat(web): 密码库列表密码气泡展示组件（点击眼睛拉取并浮层显示）"
```

---

### Task 3: 玻璃拟态编辑弹窗 PasswordEditorModal.vue

**Files:**
- Create: `jnclub-web/src/modules/bookmark/components/PasswordEditorModal.vue`

**Interfaces:**
- Consumes: vault store `createItem/updateItem`、`useMessage`
- Produces: 组件 `PasswordEditorModal`，Props `{ show: boolean, editingId: number | null, directoryId: number | null, initial: {name, username, url, notes} | null }`，Emits `update:show(v: boolean)` / `saved`；Task 4 的 VaultView 接入

- [ ] **Step 1: 创建文件**

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { NModal, NButton, NIcon, NInput, NSlider, useMessage } from 'naive-ui'
import { Lock, Eye, EyeOff, RefreshCw, X } from 'lucide-vue-next'
import { useVaultStore } from '../stores/vault'

const props = defineProps<{
  show: boolean
  editingId: number | null
  directoryId: number | null
  initial?: { name: string; username: string; url: string; notes: string } | null
}>()

const emit = defineEmits<{
  'update:show': [v: boolean]
  saved: []
}>()

const vaultStore = useVaultStore()
const message = useMessage()

const form = ref({ name: '', username: '', password: '', url: '', notes: '' })
const showPwd = ref(false)
const pwdLength = ref(16)
const saving = ref(false)

// 每次打开时重置表单（编辑回填非密码字段，密码留空=保持不变）
watch(() => props.show, (v) => {
  if (!v) return
  const init = props.initial || {}
  form.value = {
    name: init.name || '',
    username: init.username || '',
    password: '',
    url: init.url || '',
    notes: init.notes || '',
  }
  showPwd.value = false
  pwdLength.value = 16
})

const generatePwd = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*()_+-='
  const arr = new Uint32Array(pwdLength.value)
  crypto.getRandomValues(arr)
  let pwd = ''
  for (let i = 0; i < pwdLength.value; i++) pwd += chars[arr[i] % chars.length]
  form.value.password = pwd
  showPwd.value = true
}

const submit = async () => {
  if (!form.value.name.trim()) { message.warning('请输入条目名称'); return }
  if (!props.directoryId) { message.warning('请先选择目录'); return }
  saving.value = true
  try {
    const payload = {
      directoryId: props.directoryId,
      name: form.value.name.trim(),
      username: form.value.username.trim(),
      password: form.value.password,
      url: form.value.url.trim(),
      notes: form.value.notes,
    }
    if (props.editingId === null) {
      await vaultStore.createItem(payload)
      message.success('已保存')
    } else {
      await vaultStore.updateItem(props.editingId, payload)
      message.success('已更新')
    }
    emit('saved')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const onShowChange = (v: boolean) => emit('update:show', v)
</script>

<template>
  <NModal
    :show="props.show"
    :mask-closable="false"
    @update:show="onShowChange"
  >
    <div class="pwd-modal">
      <div class="pwd-modal-head">
        <div class="pwd-modal-title">
          <span class="lock-chip"><NIcon :component="Lock" size="15" /></span>
          <span>{{ editingId === null ? '新建密码条目' : '编辑密码条目' }}</span>
        </div>
        <NButton quaternary circle size="small" @click="onShowChange(false)">
          <template #icon><NIcon :component="X" size="16" /></template>
        </NButton>
      </div>

      <div class="pwd-modal-body">
        <div class="form-grid">
          <div class="field">
            <label class="field-label">名称</label>
            <NInput v-model:value="form.name" placeholder="如：GitHub / 邮箱 / 银行卡" clearable />
          </div>
          <div class="field">
            <label class="field-label">密码</label>
            <div class="pwd-input-wrap">
              <NInput
                v-model:value="form.password"
                :type="showPwd ? 'text' : 'password'"
                placeholder="留空保持不变（编辑时）"
                clearable
              />
              <NButton quaternary circle size="small" @click="showPwd = !showPwd">
                <template #icon><NIcon :component="showPwd ? EyeOff : Eye" size="16" /></template>
              </NButton>
            </div>
          </div>
          <div class="field">
            <label class="field-label">账号</label>
            <NInput v-model:value="form.username" placeholder="用户名 / 邮箱 / 卡号" clearable />
          </div>
          <div class="field">
            <label class="field-label">密码长度</label>
            <div class="slider-row">
              <NSlider v-model:value="pwdLength" :min="6" :max="64" :step="1" class="pwd-slider" />
              <span class="length-badge">{{ pwdLength }}</span>
            </div>
          </div>
          <div class="field">
            <label class="field-label">站点地址</label>
            <NInput v-model:value="form.url" placeholder="https://…（可选）" clearable />
          </div>
          <div class="field">
            <label class="field-label">&nbsp;</label>
            <NButton class="gen-btn" @click="generatePwd">
              <template #icon><NIcon :component="RefreshCw" size="14" /></template>
              随机生成
            </NButton>
          </div>
        </div>
        <div class="field">
          <label class="field-label">备注</label>
          <NInput v-model:value="form.notes" type="textarea" :rows="3" placeholder="备注（可选）" clearable />
        </div>
      </div>

      <div class="pwd-modal-foot">
        <NButton class="ghost-btn" @click="onShowChange(false)">取消</NButton>
        <NButton class="confirm-btn" :loading="saving" @click="submit">确认</NButton>
      </div>
    </div>
  </NModal>
</template>

<style scoped>
.pwd-modal {
  width: 640px;
  max-width: calc(100vw - 32px);
  border-radius: 16px;
  padding: 24px;
  color: #fff;
  background:
    radial-gradient(1200px 500px at 10% -10%, rgba(124, 58, 237, 0.35), transparent 60%),
    radial-gradient(900px 400px at 110% 120%, rgba(37, 99, 235, 0.3), transparent 60%),
    linear-gradient(160deg, #1e1b4b 0%, #172554 55%, #0f172a 100%);
  border: 1px solid rgba(255, 255, 255, 0.14);
  box-shadow:
    0 24px 64px -12px rgba(2, 6, 23, 0.7),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}
.pwd-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.pwd-modal-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}
.lock-chip {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c4b5fd;
  background: rgba(139, 92, 246, 0.22);
  border: 1px solid rgba(167, 139, 250, 0.35);
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 16px;
}
.field { min-width: 0; }
.field-label {
  display: block;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 6px;
}
.pwd-input-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}
.slider-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 6px;
}
.pwd-slider { flex: 1; }
.length-badge {
  min-width: 34px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #c4b5fd;
  background: rgba(139, 92, 246, 0.18);
  border: 1px solid rgba(167, 139, 250, 0.3);
  border-radius: 999px;
  padding: 2px 8px;
}
.pwd-modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}
/* 玻璃输入框：8px 圆角 + 内阴影 + 轻边框 + 聚焦辉光 */
.pwd-modal :deep(.n-input) {
  border-radius: 8px;
  --n-color: rgba(255, 255, 255, 0.06) !important;
  --n-color-focus: rgba(255, 255, 255, 0.09) !important;
  --n-color-hover: rgba(255, 255, 255, 0.08) !important;
  --n-border: rgba(255, 255, 255, 0.14) !important;
  --n-border-hover: rgba(167, 139, 250, 0.6) !important;
  --n-border-focus: #8b5cf6 !important;
  --n-box-shadow-focus: 0 0 0 3px rgba(139, 92, 246, 0.25) !important;
  --n-text-color: #fff !important;
  --n-placeholder-color: rgba(255, 255, 255, 0.35) !important;
  --n-caret-color: #a78bfa !important;
}
/* 滑块轨道/圆点适配深色 */
.pwd-modal :deep(.n-slider-rail) {
  background: rgba(255, 255, 255, 0.14) !important;
}
/* 绿色 pill 生成按钮 */
.gen-btn {
  border-radius: 999px !important;
  background: linear-gradient(135deg, #10b981, #059669) !important;
  color: #fff !important;
  border: none !important;
  font-weight: 600;
  box-shadow: 0 4px 14px -4px rgba(16, 185, 129, 0.5);
}
.gen-btn:hover { filter: brightness(1.1); }
/* 渐变主按钮 */
.confirm-btn {
  border-radius: 10px;
  background: linear-gradient(135deg, #8b5cf6, #3b82f6) !important;
  color: #fff !important;
  border: none !important;
  font-weight: 600;
  box-shadow: 0 4px 14px -4px rgba(99, 102, 241, 0.5);
}
.confirm-btn:hover { filter: brightness(1.12); }
/* ghost 取消按钮 */
.ghost-btn {
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: rgba(255, 255, 255, 0.85);
}
.ghost-btn:hover { background: rgba(255, 255, 255, 0.14); }
</style>
```

- [ ] **Step 2: 类型检查**

Run: `cd jnclub-web && npx vue-tsc --noEmit`
Expected: 通过（零错误）

- [ ] **Step 3: 提交**

```bash
git add src/modules/bookmark/components/PasswordEditorModal.vue
git commit -m "feat(web): 密码库玻璃拟态编辑弹窗（双列布局/长度滑块/绿色随机生成）"
```

---

### Task 4: VaultView.vue 重构接线

**Files:**
- Modify: `jnclub-web/src/modules/bookmark/components/VaultView.vue`

**Interfaces:**
- Consumes: `PasswordEditorModal`、`PasswordRevealPopover`、`copyText`（`../../../shared/utils/clipboard`）
- Produces: 无（最终交付）

- [ ] **Step 1: 修改 script setup**（替换 39-122 行区间：弹窗状态 + 随机生成 + 复制逻辑）

将 39-122 行（`showModal` 到 `copyPwd` 结束）替换为：

```ts
// ========== 新建 / 编辑（弹窗已拆分为独立组件） ==========
const modalShow = ref(false)
const editingId = ref<number | null>(null)
const modalInitial = ref<{ name: string; username: string; url: string; notes: string } | null>(null)

const openCreate = () => {
  editingId.value = null
  modalInitial.value = null
  modalShow.value = true
}

const openEdit = (item: VaultItem) => {
  editingId.value = item.id
  modalInitial.value = {
    name: item.name,
    username: item.username || '',
    url: item.url || '',
    notes: item.notes || '',
  }
  modalShow.value = true
}

const onSaved = () => {
  modalShow.value = false
  load()
}

// ========== 复制密码（先拉明文，再走共享剪贴板工具，HTTP 环境可用） ==========
const copyPwd = async (item: VaultItem) => {
  let pwd = item.password
  if (pwd == null) {
    try {
      const detail = await vaultStore.fetchDetail(item.id)
      pwd = detail.password
    } catch (e: any) {
      message.error(e.message || '获取密码失败')
      return
    }
  }
  if (!pwd) { message.warning('内容为空'); return }
  if (await copyText(pwd)) message.success('密码已复制')
  else message.error('复制失败')
}
```

- [ ] **Step 2: 修改 import 语句**（6-16 行）

将第 6-16 行 import 块替换为：

```ts
import { ref, watch, onMounted } from 'vue'
import {
  NButton, NIcon, NSpin, NEmpty, NTag, useMessage, useDialog,
} from 'naive-ui'
import {
  KeyRound, Plus, Pencil, Trash2, Copy, User,
} from 'lucide-vue-next'
import { useVaultStore, type VaultItem } from '../stores/vault'
import { useDraggableSort } from '../composables/useDraggableSort'
import PasswordEditorModal from './PasswordEditorModal.vue'
import PasswordRevealPopover from './PasswordRevealPopover.vue'
import { copyText } from '../../../shared/utils/clipboard'
```

- [ ] **Step 3: 修改列表行眼睛/复制按钮**（181-186 行）

将 181-186 行的眼睛按钮 + 复制按钮替换为：

```html
<PasswordRevealPopover :item="item" />
<NButton quaternary circle size="small" title="复制密码" @click="copyPwd(item)">
  <template #icon><NIcon :component="Copy" size="16" /></template>
</NButton>
```

- [ ] **Step 4: 替换弹窗模板**（199-245 行 `NModal` 整块）

将 199-245 行的 `<NModal ...>...</NModal>` 整块替换为：

```html
    <!-- 新建/编辑弹窗（玻璃拟态） -->
    <PasswordEditorModal
      v-model:show="modalShow"
      :editing-id="editingId"
      :directory-id="props.directoryId"
      :initial="modalInitial"
      @saved="onSaved"
    />
```

- [ ] **Step 5: 清理无用 CSS**（331-339 行 `.pwd-field` / `.pwd-generate`）

将 331-339 行的 `.pwd-field` 与 `.pwd-generate` 两个规则删除（弹窗已拆分，列表不再使用）。

- [ ] **Step 6: 类型检查**

Run: `cd jnclub-web && npx vue-tsc --noEmit`
Expected: 通过（零错误）

- [ ] **Step 7: 构建**

Run: `cd jnclub-web && npm run build`
Expected: `vue-tsc` 通过 + `vite build` 成功产出 `dist/`

- [ ] **Step 8: 浏览器手动验证（本地 dev 或构建后 preview）**

Run: `npm run dev`（本地 5173，dev 代理 /api → 19005，需本地后端或连服务器；若本地后端不可用，走 Step 9 上线后线上验证）
验证清单：
1. 密码库列表：点击眼睛 → 行右侧气泡显示明文密码，等宽字体；点击气泡外区域 → 气泡收起；再次点眼睛 → 重新拉取/显示
2. 气泡内"复制" → 提示"密码已复制"（HTTP 环境走 execCommand 降级）；用 Cmd+V 粘贴确认内容一致
3. 列表"复制密码"按钮 → 同样成功
4. 新建条目弹窗：紫蓝渐变玻璃卡片、双列布局、滑块拖动 + 数字徽章实时变化、绿色"随机生成"pill 生成对应长度密码、密码输入框聚焦有紫辉光
5. 编辑已有条目：密码留空 → 提交 → 刷新后密码不变；填新密码 → 提交生效
6. 弹窗"取消"与右上角 X 均可关闭，内容不丢失校验提示正常

- [ ] **Step 9: 提交**

```bash
git add src/modules/bookmark/components/VaultView.vue
git commit -m "fix(web): 密码库列表眼睛气泡显示密码 + 复制降级修复 + 接入玻璃弹窗"
```

- [ ] **Step 10: 推送上线（自动部署）**

```bash
git push origin master
```

Expected: 服务器 auto-deploy（cron */5 轮询）自动拉取构建部署 JNClub；约 5-8 分钟后线上生效。验证：
`curl -s -o /dev/null -w '%{http_code}' http://localhost/jnclub/` → 200，且线上打开密码库执行 Step 8 清单复核。

---

## Self-Review 记录

- **Spec 覆盖**：眼睛气泡（Task 2 ✓）、复制降级（Task 1+4 ✓）、玻璃弹窗布局（Task 3 ✓ 双列/滑块/绿色 pill/备注/右下角 CTA/聚焦辉光/8px 圆角内阴影）、后端零改动（约束 ✓）、固定暗色不随主题（CSS 硬编码紫蓝 ✓）、编辑留空=不变（watch 重置密码为空 + 后端契约 ✓）
- **占位符扫描**：无 TBD/TODO；每步含完整代码
- **类型一致性**：`copyText(text): Promise<boolean>` 在 Task 1 定义，Task 2/4 以 `if (await copyText(...))` 消费一致；`PasswordEditorModal` Props/Emits 在 Task 3 定义、Task 4 使用一致（`v-model:show`、`:editing-id`、`:directory-id`、`:initial`、`@saved`）；`PasswordRevealPopover` Props `{ item }` Task 2 定义、Task 4 传 `:item="item"` 一致；`modalInitial` 类型与 `initial` prop 类型一致
