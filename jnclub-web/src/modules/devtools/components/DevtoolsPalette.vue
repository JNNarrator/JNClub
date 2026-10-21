<script setup lang="ts">
/**
 * DevtoolsPalette.vue — 开发者工具页内命令面板（⌘⇧P / Ctrl+Shift+P）
 *
 * 迁移自 jnx `src/components/CommandPalette.vue` + `src/stores/commandPalette.ts`。
 * 与 jnx 的差异：
 * - jnx 用 pinia store 全局持有 open/query/actions；这里收敛成一个受控组件
 *   （`show` + `update:show`），动作列表由 DevtoolsLayout 注入 —— 因为动作要访问
 *   store、router 与主题切换 emit，放在壳层组装比塞进 store 更直接。
 * - 视觉全部走 JNClub 设计 token，不再硬编码 jnx 的粉色调。
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { CornerDownLeft, Search } from 'lucide-vue-next'
import type { PaletteAction } from '../types'

const props = defineProps<{
  show: boolean
  actions: PaletteAction[]
}>()

const emit = defineEmits<{ 'update:show': [value: boolean] }>()

const query = ref('')
const selectedIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)

const filtered = computed<PaletteAction[]>(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return props.actions
  return props.actions.filter((a) => {
    if (a.label.toLowerCase().includes(q)) return true
    if (a.id.toLowerCase().includes(q)) return true
    if (a.category?.toLowerCase().includes(q)) return true
    return a.keywords?.some((k) => k.toLowerCase().includes(q)) ?? false
  })
})

watch(
  () => props.show,
  (open) => {
    if (!open) {
      query.value = ''
      selectedIndex.value = 0
      return
    }
    selectedIndex.value = 0
    void nextTick(() => inputRef.value?.focus())
  },
)

watch(filtered, () => {
  if (selectedIndex.value > filtered.value.length - 1) {
    selectedIndex.value = Math.max(0, filtered.value.length - 1)
  }
})

function close(): void {
  emit('update:show', false)
}

/**
 * Esc 兜底：输入框失焦后（例如点了结果区的空白）仍能关闭面板。
 * 用捕获阶段是为了抢在页内快捷键之前处理。
 */
function onWindowKeydown(e: KeyboardEvent): void {
  if (!props.show) return
  if (e.key !== 'Escape') return
  e.preventDefault()
  e.stopPropagation()
  close()
}

watch(
  () => props.show,
  (open) => {
    if (open) window.addEventListener('keydown', onWindowKeydown, true)
    else window.removeEventListener('keydown', onWindowKeydown, true)
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onWindowKeydown, true)
})

function move(delta: number): void {
  const len = filtered.value.length
  if (!len) return
  selectedIndex.value = (selectedIndex.value + delta + len) % len
}

function exec(index: number): void {
  const action = filtered.value[index]
  if (!action) return
  close()
  action.run()
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    move(1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    move(-1)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    exec(selectedIndex.value)
  } else if (e.key === 'Escape') {
    e.preventDefault()
    close()
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="cp-fade">
      <div v-if="props.show" class="cp-overlay" @click.self="close">
        <div class="cp-panel" role="dialog" aria-label="命令面板">
          <div class="cp-search">
            <Search :size="17" class="cp-search-icon" />
            <input
              ref="inputRef"
              v-model="query"
              class="cp-input"
              type="text"
              placeholder="搜索工具或动作…"
              @keydown="onKeydown"
            />
            <kbd class="cp-kbd">Esc</kbd>
          </div>

          <div class="cp-results">
            <div v-if="!filtered.length" class="cp-empty">没有匹配的结果</div>
            <button
              v-for="(action, i) in filtered"
              :key="action.id"
              type="button"
              class="cp-item"
              :class="{ active: i === selectedIndex }"
              @click="exec(i)"
              @mouseenter="selectedIndex = i"
            >
              <span class="cp-label">{{ action.label }}</span>
              <span v-if="action.category" class="cp-cat">{{ action.category }}</span>
              <CornerDownLeft v-if="i === selectedIndex" :size="13" class="cp-enter" />
            </button>
          </div>

          <div class="cp-foot">
            <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
            <span><kbd>↵</kbd> 执行</span>
            <span><kbd>Esc</kbd> 关闭</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.cp-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 12vh 16px 16px;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.cp-panel {
  width: 540px;
  max-width: 100%;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-popover);
  overflow: hidden;
}

.cp-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.cp-search-icon {
  flex-shrink: 0;
  color: var(--text-3);
}
.cp-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--fs-base);
  font-family: inherit;
  color: var(--text-1);
}
.cp-input::placeholder {
  color: var(--text-4);
}

.cp-kbd,
.cp-foot kbd {
  font-family: var(--font-mono);
  font-size: var(--fs-2xs);
  line-height: 1;
  padding: 3px 5px;
  border: 1px solid var(--border);
  border-radius: var(--radius-2xs);
  color: var(--text-3);
  background: var(--hover-bg);
}

.cp-results {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px;
}

.cp-empty {
  padding: 28px 16px;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.cp-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border: none;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-1);
  font-size: var(--fs-sm);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease, ease);
}
.cp-item:hover,
.cp-item.active {
  background: var(--brand-soft);
  color: var(--brand);
}
.cp-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cp-cat {
  flex-shrink: 0;
  font-size: var(--fs-2xs);
  color: var(--text-3);
}
.cp-item.active .cp-cat {
  color: var(--brand);
}
.cp-enter {
  flex-shrink: 0;
  opacity: 0.7;
}

.cp-foot {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 14px;
  border-top: 1px solid var(--border);
  font-size: var(--fs-2xs);
  color: var(--text-3);
  flex-shrink: 0;
}
.cp-foot span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.cp-fade-enter-active,
.cp-fade-leave-active {
  transition: opacity var(--dur-fast) var(--ease, ease);
}
.cp-fade-enter-from,
.cp-fade-leave-to {
  opacity: 0;
}

@media (max-width: 767px) {
  .cp-overlay {
    padding: 8vh 10px 10px;
  }
  .cp-panel {
    max-height: 76vh;
  }
  .cp-foot {
    display: none;
  }
}
</style>
