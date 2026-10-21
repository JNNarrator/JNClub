<script setup lang="ts">
/**
 * DevtoolsSettings.vue — 开发者工具偏好设置（页内标签页）
 *
 * 迁移自 jnx `src/views/ToolSettings.vue`，按 Web 端能力收敛：
 * - 主题（light/dark）由 JNClub 全局负责（App.vue + 侧栏个性化），这里不再重复
 * - 标签栏行数 / 每行标签数：Web 端标签栏是横向滚动，不按行折行，因此移除
 * - 剪贴板轮询间隔：浏览器拿不到系统剪贴板事件，只能靠轮询 + 焦点态，间隔固定为
 *   `stores/clipboard.ts` 里的 DEFAULT_INTERVAL，这里只展示说明
 * - 工具记忆 / 恢复上次标签页：保留（对应 useToolDraft 与 store.hydrate 行为）
 */
import { computed, ref } from 'vue'
import { NButton, NInputNumber, NSpin, NSwitch, useDialog, useMessage } from 'naive-ui'
import { Eraser, RotateCcw, Trash2 } from 'lucide-vue-next'
import { useDevtoolsStore } from '../stores/tools'
import {
  CLIPBOARD_INTERVAL_MAX,
  CLIPBOARD_INTERVAL_MIN,
  useDevtoolsSettingsStore,
} from '../stores/settings'
import { useRecentTools } from '../composables/useRecentTools'
import { clearAllDrafts } from '../composables/useToolDraft'

defineOptions({ name: 'DevtoolsSettings' })

const store = useDevtoolsStore()
const settings = useDevtoolsSettingsStore()
const { recentTools, clearRecent } = useRecentTools()
const dialog = useDialog()
const message = useMessage()

const busy = ref(false)

const draftCount = computed(() => {
  let n = 0
  try {
    for (let i = 0; i < localStorage.length; i++) {
      if (localStorage.key(i)?.startsWith('jnclub-devtools:draft:')) n++
    }
  } catch {
    /* 隐私模式禁用 localStorage：按 0 处理 */
  }
  return n
})

async function setToolMemory(value: boolean): Promise<void> {
  await settings.update('toolMemory', value)
  message.success(value ? '已开启工具记忆' : '已关闭工具记忆')
}

async function setReopenLastTab(value: boolean): Promise<void> {
  await settings.update('reopenLastTab', value)
  message.success(value ? '下次进入会恢复上次的标签页' : '下次进入将回到启动台')
}

async function setClipboardInterval(value: number | null): Promise<void> {
  if (value === null) return
  await settings.update('clipboardInterval', value)
}

function confirmClearDrafts(): void {
  dialog.warning({
    title: '清除全部工具记忆',
    content: '所有工具里未提交的输入内容都会被删除，且无法恢复。已打开的标签页不受影响。',
    positiveText: '清除',
    negativeText: '取消',
    onPositiveClick: () => {
      busy.value = true
      try {
        clearAllDrafts()
        message.success('已清除全部工具记忆')
      } finally {
        busy.value = false
      }
    },
  })
}

function confirmResetTabs(): void {
  dialog.warning({
    title: '重置标签页',
    content: '关闭当前所有标签页并回到启动台，同时清空已保存的标签页状态。',
    positiveText: '重置',
    negativeText: '取消',
    onPositiveClick: () => {
      store.resetPersisted()
      message.success('标签页已重置')
    },
  })
}

function confirmClearRecent(): void {
  clearRecent()
  message.success('已清空最近使用记录')
}
</script>

<template>
  <div class="ds-page">
    <header class="ds-head">
      <h2 class="ds-title">工具设置</h2>
      <p class="ds-sub">这些偏好会同步到你的账号，换设备也能保持一致</p>
    </header>

    <NSpin :show="busy" size="small">
      <!-- ─── 标签页 ─── -->
      <section class="ds-card">
        <h3 class="ds-card-title">标签页</h3>

        <div class="ds-row">
          <div class="ds-row-main">
            <span class="ds-label">恢复上次的标签页</span>
            <span class="ds-hint">重新打开开发者工具时，恢复上次打开的工具与激活项</span>
          </div>
          <NSwitch
            :value="settings.values.reopenLastTab"
            @update:value="setReopenLastTab"
          />
        </div>

        <div class="ds-row">
          <div class="ds-row-main">
            <span class="ds-label">当前已打开 {{ store.openTabIds.length }} 个标签页</span>
            <span class="ds-hint">上限 12 个，超出后自动关闭最早打开的那个</span>
          </div>
          <NButton size="small" secondary :disabled="!store.openTabIds.length" @click="confirmResetTabs">
            <template #icon><RotateCcw :size="14" /></template>
            重置
          </NButton>
        </div>
      </section>

      <!-- ─── 工具记忆 ─── -->
      <section class="ds-card">
        <h3 class="ds-card-title">工具记忆</h3>

        <div class="ds-row">
          <div class="ds-row-main">
            <span class="ds-label">记住输入内容</span>
            <span class="ds-hint">
              自动保存各工具里正在编辑的内容，切走再回来或刷新页面都不会丢
            </span>
          </div>
          <NSwitch :value="settings.values.toolMemory" @update:value="setToolMemory" />
        </div>

        <div class="ds-row">
          <div class="ds-row-main">
            <span class="ds-label">已保存 {{ draftCount }} 份草稿</span>
            <span class="ds-hint">草稿 30 天未使用会自动清理</span>
          </div>
          <NButton size="small" secondary :disabled="!draftCount" @click="confirmClearDrafts">
            <template #icon><Eraser :size="14" /></template>
            清除
          </NButton>
        </div>

        <div class="ds-row">
          <div class="ds-row-main">
            <span class="ds-label">最近使用 {{ recentTools.length }} 个工具</span>
            <span class="ds-hint">启动台「继续上次」区域的数据来源</span>
          </div>
          <NButton size="small" secondary :disabled="!recentTools.length" @click="confirmClearRecent">
            <template #icon><Trash2 :size="14" /></template>
            清空
          </NButton>
        </div>
      </section>

      <!-- ─── 剪贴板 ─── -->
      <section class="ds-card">
        <h3 class="ds-card-title">剪贴板</h3>
        <div class="ds-row">
          <div class="ds-row-main">
            <span class="ds-label">监听轮询间隔</span>
            <span class="ds-hint">
              开启「监听」后每隔多久读取一次当前剪贴板；页面不可见时自动跳过读取
            </span>
          </div>
          <div class="ds-interval">
            <NInputNumber
              :value="settings.values.clipboardInterval"
              :min="CLIPBOARD_INTERVAL_MIN"
              :max="CLIPBOARD_INTERVAL_MAX"
              :step="500"
              size="small"
              style="width: 130px"
              @update:value="setClipboardInterval"
            />
            <span class="ds-unit">ms</span>
          </div>
        </div>

        <div class="ds-row">
          <div class="ds-row-main">
            <span class="ds-label">浏览器环境说明</span>
            <span class="ds-hint">
              网页无法读取系统剪贴板的历史。剪贴板工具只能在你点击「记录当前」时读取一次，
              或在该工具页处于前台时轮询当前剪贴板内容；历史记录保存在本机浏览器中。
            </span>
          </div>
        </div>
      </section>

      <!-- ─── 关于 ─── -->
      <section class="ds-card">
        <h3 class="ds-card-title">关于</h3>
        <div class="ds-row">
          <div class="ds-row-main">
            <span class="ds-label">开发者工具</span>
            <span class="ds-hint">
              迁移自 jnx 桌面版：JSON / 格式互转 / JSON⇄JavaBean / DDL⇄Java / HTTP 请求 /
              剪贴板 / Cron 表达式。快捷键可在「快捷键」标签页自定义。
            </span>
          </div>
        </div>
      </section>
    </NSpin>
  </div>
</template>

<style scoped>
.ds-page {
  height: 100%;
  overflow-y: auto;
  padding: 20px 20px 40px;
  max-width: 820px;
  margin: 0 auto;
  color: var(--text-1);
}

.ds-head {
  margin-bottom: 16px;
}
.ds-title {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--text-1);
}
.ds-sub {
  margin: 4px 0 0;
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.ds-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 16px 18px;
  margin-bottom: 14px;
}
.ds-card-title {
  margin: 0 0 8px;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-2);
  letter-spacing: 0.02em;
}

.ds-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-top: 1px solid var(--border);
}
.ds-row:first-of-type {
  border-top: none;
}
.ds-row-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.ds-label {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-1);
}
.ds-hint {
  font-size: var(--fs-xs);
  line-height: 1.55;
  color: var(--text-3);
}

.ds-interval {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.ds-unit {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

@media (max-width: 767px) {
  .ds-page {
    padding: 12px 12px 32px;
  }
  .ds-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
