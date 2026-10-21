<script setup lang="ts">
/**
 * DevtoolsShortcuts.vue — 开发者工具快捷键自定义（页内标签页）
 *
 * 迁移自 jnx `src/views/ToolShortcuts.vue`。改动：
 * - 表数据源换成 `DEVTOOLS_SHORTCUT_GROUPS`（只含工具内动作与页内导航）
 * - 平台判定走 `usePlatform()`（与 JNClub 全局一致），不再有独立的 platform 状态
 * - 录制逻辑保留 jnx 的行为：capture + once 监听、Esc 取消、必须带至少一个修饰键、
 *   冲突仍写入但提示；提示文案改由 `bindings.warn()` 统一给出
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { NButton, NInput, NTag, useMessage } from 'naive-ui'
import { RotateCcw, Search } from 'lucide-vue-next'
import { usePlatform } from '@/shared/composables/usePlatform'
import type { Chord, Mod } from '@/shared/composables/useKeyboardShortcut'
import { useShortcutBindingsStore } from '../stores/shortcutBindings'
import { DEVTOOLS_SHORTCUT_GROUPS, type DevtoolsShortcutAction } from '../shortcuts'
import Kbd from '../components/Kbd.vue'

defineOptions({ name: 'DevtoolsShortcuts' })

const bindings = useShortcutBindingsStore()
const { isMac } = usePlatform()
const message = useMessage()

const search = ref('')
const recording = ref<DevtoolsShortcutAction | null>(null)
const warnText = ref('')

const groups = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return DEVTOOLS_SHORTCUT_GROUPS
  return DEVTOOLS_SHORTCUT_GROUPS.map((g) => ({
    ...g,
    items: g.items.filter((i) => i.label.toLowerCase().includes(q) || i.action.toLowerCase().includes(q)),
  })).filter((g) => g.items.length)
})

/* ─── 录制 ─── */
function stopRecord(): void {
  recording.value = null
  warnText.value = ''
  window.removeEventListener('keydown', onRecordKey, { capture: true })
}

function startRecord(action: DevtoolsShortcutAction): void {
  stopRecord()
  recording.value = action
  window.addEventListener('keydown', onRecordKey, { capture: true, once: true })
}

function onRecordKey(e: KeyboardEvent): void {
  e.preventDefault()
  e.stopPropagation()
  const action = recording.value
  if (!action) return

  if (e.key === 'Escape') {
    stopRecord()
    return
  }

  // 只按下修饰键本身：不算一个完整组合，继续等主键
  if (['Meta', 'Control', 'Alt', 'Shift'].includes(e.key)) {
    window.addEventListener('keydown', onRecordKey, { capture: true, once: true })
    return
  }

  const mods: Mod[] = []
  if (isMac.value ? e.metaKey : e.ctrlKey) mods.push('mod')
  if (e.altKey) mods.push(isMac.value ? 'opt' : 'alt')
  if (e.shiftKey) mods.push('shift')

  if (!mods.length) {
    warnText.value = '需要至少一个修饰键（⌘/Ctrl、Option/Alt 或 Shift）'
    recording.value = null
    return
  }

  const key = e.key.length === 1 ? e.key.toLowerCase() : e.key
  const chord: Chord = { mods, key }

  const warn = bindings.warn(action, chord)
  bindings.setBinding(action, chord)
  recording.value = null
  if (warn) {
    warnText.value = warn
    message.warning(warn)
  } else {
    warnText.value = ''
    message.success(`已把「${bindings.labels[action] ?? action}」设为 ${bindings.accelerator(action)}`)
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onRecordKey, { capture: true })
})

/* ─── 重置 ─── */
function resetOne(action: DevtoolsShortcutAction): void {
  bindings.reset(action)
  message.success('已恢复默认键位')
}

function resetAll(): void {
  bindings.resetAll()
  warnText.value = ''
  message.success('已恢复全部默认键位')
}
</script>

<template>
  <div class="ks-page">
    <header class="ks-head">
      <div>
        <h2 class="ks-title">快捷键</h2>
        <p class="ks-sub">
          这些键位只在开发者工具页内生效。自定义 {{ bindings.customizedCount }} 项
        </p>
      </div>
      <NButton size="small" secondary :disabled="!bindings.customizedCount" @click="resetAll">
        <template #icon><RotateCcw :size="14" /></template>
        全部恢复默认
      </NButton>
    </header>

    <NInput v-model:value="search" placeholder="搜索动作或键名…" clearable size="small" class="ks-search">
      <template #prefix><Search :size="14" /></template>
    </NInput>

    <p v-if="warnText" class="ks-warn">{{ warnText }}</p>

    <section v-for="group in groups" :key="group.name" class="ks-group">
      <h3 class="ks-group-title">{{ group.name }}</h3>
      <div class="ks-list">
        <div v-for="item in group.items" :key="item.action" class="ks-row">
          <span class="ks-label">{{ item.label }}</span>

          <div class="ks-right">
            <NTag v-if="!bindings.isDefault(item.action)" size="small" type="info" :bordered="false">
              自定义
            </NTag>

            <button
              type="button"
              class="ks-kbd-btn"
              :class="{ recording: recording === item.action }"
              :title="recording === item.action ? '按下新的组合键，Esc 取消' : '点击后按下新的组合键'"
              @click="recording === item.action ? stopRecord() : startRecord(item.action)"
            >
              <span v-if="recording === item.action" class="ks-recording">按下组合键…</span>
              <Kbd v-else :action="item.action" />
            </button>

            <button
              type="button"
              class="ks-reset"
              :disabled="bindings.isDefault(item.action)"
              title="恢复默认"
              @click="resetOne(item.action)"
            >
              <RotateCcw :size="13" />
            </button>
          </div>
        </div>
      </div>
    </section>

    <p v-if="!groups.length" class="ks-empty">没有匹配的快捷键</p>
  </div>
</template>

<style scoped>
.ks-page {
  height: 100%;
  overflow-y: auto;
  padding: 20px 20px 40px;
  max-width: 820px;
  margin: 0 auto;
  color: var(--text-1);
}

.ks-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.ks-title {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--text-1);
}
.ks-sub {
  margin: 4px 0 0;
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.ks-search {
  margin-bottom: 12px;
}

.ks-warn {
  margin: 0 0 12px;
  padding: 8px 12px;
  font-size: var(--fs-xs);
  color: var(--warning-text);
  background: var(--state-warning-soft);
  border-radius: var(--radius-xs);
}

.ks-group {
  margin-bottom: 16px;
}
.ks-group-title {
  margin: 0 0 6px;
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-3);
  text-transform: uppercase;
}

.ks-list {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.ks-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 14px;
  border-top: 1px solid var(--border);
}
.ks-row:first-child {
  border-top: none;
}

.ks-label {
  font-size: var(--fs-sm);
  color: var(--text-1);
}

.ks-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.ks-kbd-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 76px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
  background: var(--bg-card);
  color: var(--text-2);
  font-family: inherit;
  cursor: pointer;
  transition: border-color var(--dur-fast, 0.15s) ease, color var(--dur-fast, 0.15s) ease;
}
.ks-kbd-btn:hover {
  border-color: var(--brand);
}
.ks-kbd-btn.recording {
  border-color: var(--brand);
  background: var(--brand-soft);
  color: var(--brand);
}
.ks-recording {
  font-size: var(--fs-2xs);
  white-space: nowrap;
}

.ks-reset {
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
.ks-reset:hover:not(:disabled) {
  background: var(--hover-bg);
  color: var(--text-1);
}
.ks-reset:disabled {
  opacity: 0.3;
  cursor: default;
}

.ks-empty {
  padding: 24px 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--text-3);
}

@media (max-width: 767px) {
  .ks-page {
    padding: 12px 12px 32px;
  }
  .ks-head {
    flex-direction: column;
    gap: 8px;
  }
  .ks-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .ks-right {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
