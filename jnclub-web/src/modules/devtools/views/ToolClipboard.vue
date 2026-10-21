<script setup lang="ts">
/**
 * ToolClipboard.vue — 剪贴板历史（浏览器版）
 *
 * 迁移自 jnx `src/views/ToolClipboard.vue`。桌面端靠 Tauri 插件静默轮询系统剪贴板，
 * 浏览器只能拿到「当前剪贴板内容」且需要权限，因此交互上多给两个入口：
 * - 「记录当前」：用户手势触发的主动抓取（唯一 100% 可靠的方式）
 * - 「监听」：页面可见时按设置的间隔轮询（默认 2s），权限被拒会自动停并给出原因
 * 历史存在本机 localStorage（上限 200 条），不随账号同步。
 */
import { defineOptions, onMounted, onUnmounted, ref } from 'vue'
import { NButton, NEmpty, NSpin, NTooltip, useMessage } from 'naive-ui'
import { AlertCircle, Copy, Play, Plus, Square, Trash2 } from 'lucide-vue-next'
import { useClipboardStore } from '../stores/clipboard'
import { useDevtoolsSettingsStore } from '../stores/settings'

defineOptions({ name: 'ToolClipboard' })
// 工具记忆（useToolDraft）：跳过 — 剪贴板历史本身已持久化，不重复记忆

const clip = useClipboardStore()
const settings = useDevtoolsSettingsStore()
const msg = useMessage()
const capturing = ref(false)

onMounted(() => {
  clip.loadHistory()
  // 监听需要权限，首次进入不自动开启，交给用户显式点击
})
onUnmounted(() => {
  clip.stopWatching()
})

async function copy(content: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(content)
    msg.success('已复制')
  } catch {
    msg.error('复制失败，请手动选择文本')
  }
}

async function captureNow(): Promise<void> {
  if (capturing.value) return
  capturing.value = true
  try {
    const entry = await clip.capture('手动')
    if (entry) msg.success('已记录当前剪贴板内容')
    else if (clip.lastError) msg.warning(clip.lastError)
  } finally {
    capturing.value = false
  }
}

function toggleWatching(): void {
  if (clip.watching) {
    clip.stopWatching()
    msg.info('已暂停监听')
  } else {
    clip.startWatching(settings.values.clipboardInterval)
    if (clip.lastError) msg.warning(clip.lastError)
    else msg.success('已开始监听剪贴板变化')
  }
}

function formatTime(ts: string): string {
  // 存储为 UTC 无时区串，补 Z 后再按本地时区展示
  const d = new Date(`${ts.replace(' ', 'T')}Z`)
  if (Number.isNaN(d.getTime())) return ts
  return d.toLocaleString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function getPreview(text: string): string {
  const t = text.trim()
  return t.length <= 160 ? t : `${t.slice(0, 160)}…`
}
</script>

<template>
  <div class="cb-panel">
    <div class="cb-toolbar">
      <div class="cb-toolbar-left">
        <h2 class="cb-title">剪贴板</h2>
        <span class="cb-badge">{{ clip.items.length }} 条</span>
      </div>
      <div class="cb-toolbar-right">
        <NButton size="small" type="primary" :loading="capturing" @click="captureNow">
          <template #icon><Plus :size="14" /></template>
          记录当前
        </NButton>
        <NButton size="small" secondary :disabled="!clip.available" @click="toggleWatching">
          <template #icon>
            <Square v-if="clip.watching" :size="13" />
            <Play v-else :size="13" />
          </template>
          {{ clip.watching ? '暂停' : '监听' }}
        </NButton>
        <NButton size="small" quaternary :disabled="!clip.items.length" @click="clip.clearAll()">
          清空
        </NButton>
      </div>
    </div>

    <p v-if="clip.lastError" class="cb-alert">
      <AlertCircle :size="14" />
      <span>{{ clip.lastError }}</span>
    </p>

    <p v-else-if="!clip.available" class="cb-alert">
      <AlertCircle :size="14" />
      <span>当前浏览器不支持读取剪贴板（需要 HTTPS 或 localhost），可手动粘贴到各工具中。</span>
    </p>

    <NSpin :show="clip.loading" class="cb-spin">
      <div v-if="!clip.items.length" class="cb-empty">
        <NEmpty description="暂无记录。点击「记录当前」抓取一次剪贴板内容。" />
      </div>
      <div v-else class="cb-list">
        <div v-for="item in clip.items" :key="item.id" class="cb-item">
          <div class="cb-item-main" @click="copy(item.content)">
            <div class="cb-item-content">{{ getPreview(item.content) }}</div>
            <div class="cb-item-meta">
              <span class="cb-item-time">{{ formatTime(item.created_at) }}</span>
              <span class="cb-item-source">{{ item.source || '手动' }}</span>
            </div>
          </div>
          <div class="cb-item-actions">
            <NTooltip trigger="hover">
              <template #trigger>
                <button type="button" class="cb-act" aria-label="复制" @click="copy(item.content)">
                  <Copy :size="14" />
                </button>
              </template>
              复制
            </NTooltip>
            <NTooltip trigger="hover">
              <template #trigger>
                <button
                  type="button"
                  class="cb-act cb-act-danger"
                  aria-label="删除这条记录"
                  @click="clip.removeItem(item.id)"
                >
                  <Trash2 :size="14" />
                </button>
              </template>
              删除
            </NTooltip>
          </div>
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.cb-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px 20px 20px;
  overflow: hidden;
}

.cb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
  margin-bottom: 10px;
}
.cb-toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.cb-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.cb-title {
  margin: 0;
  font-size: var(--fs-base);
  font-weight: 700;
  color: var(--text-1);
}
.cb-badge {
  font-size: var(--fs-xs);
  color: var(--text-2);
  padding: 2px 10px;
  background: var(--hover-bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
}

.cb-alert {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px;
  padding: 8px 12px;
  font-size: var(--fs-xs);
  line-height: 1.5;
  color: var(--warning-text);
  background: var(--state-warning-soft);
  border-radius: var(--radius-xs);
  flex-shrink: 0;
}

.cb-spin {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.cb-spin :deep(.n-spin-content) {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.cb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 200px;
}

.cb-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
  padding-right: 2px;
}

.cb-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
  transition: border-color var(--dur-fast, 0.15s) ease, background var(--dur-fast, 0.15s) ease;
}
.cb-item:hover {
  border-color: var(--brand);
  background: var(--hover-bg);
}

.cb-item-main {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  cursor: pointer;
}

.cb-item-content {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  line-height: 1.55;
  word-break: break-all;
  white-space: pre-wrap;
  color: var(--text-1);
}

.cb-item-meta {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  font-size: var(--fs-2xs);
  color: var(--text-3);
}
.cb-item-time {
  font-family: var(--font-mono);
}

.cb-item-actions {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 8px 8px 0;
  flex-shrink: 0;
}
.cb-act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: var(--radius-2xs);
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
}
.cb-act:hover {
  background: var(--bg-card);
  color: var(--text-1);
}
.cb-act-danger:hover {
  color: var(--danger);
}

@media (max-width: 767px) {
  .cb-panel {
    padding: 12px;
  }
  .cb-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
  .cb-toolbar-right {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
