<script setup lang="ts">
/**
 * Kbd.vue — 开发者工具键位展示
 *
 * 迁移自 jnx `src/components/Kbd.vue`，差异：
 * - 不再依赖 jnx 的全局 SHORTCUTS 表，改为读开发者工具自己的绑定 store
 *   （这样设置页改了键位，速查表会同步更新）
 * - 平台 glyph 走 JNClub `usePlatform()`
 * - 配色换成 JNClub token（--bg-card / --border / --text-3）
 */
import { computed } from 'vue'
import { usePlatform } from '@/shared/composables/usePlatform'
import { useShortcutBindingsStore } from '../stores/shortcutBindings'
import type { DevtoolsShortcutAction } from '../shortcuts'

const props = defineProps<{
  /** 按动作取键位（会应用用户自定义绑定） */
  action?: DevtoolsShortcutAction
  /** 直接给键位数组，如 ['mod', 'Enter'] */
  keys?: string[]
}>()

const { isMac } = usePlatform()
const bindings = useShortcutBindingsStore()

const GLYPH: Record<string, { mac: string; win: string }> = {
  mod: { mac: '⌘', win: 'Ctrl' },
  shift: { mac: '⇧', win: 'Shift' },
  alt: { mac: '⌥', win: 'Alt' },
  opt: { mac: '⌥', win: 'Alt' },
}

function formatKey(k: string): string {
  const map: Record<string, string> = {
    ArrowLeft: '←',
    ArrowRight: '→',
    ArrowUp: '↑',
    ArrowDown: '↓',
    Backspace: '⌫',
    Enter: '↵',
    Escape: 'Esc',
    Delete: '⌦',
    Tab: 'Tab',
    ' ': 'Space',
  }
  return map[k] || (k.length === 1 ? k.toUpperCase() : k)
}

function translateKey(k: string): string {
  if (k in GLYPH) return GLYPH[k][isMac.value ? 'mac' : 'win']
  return formatKey(k)
}

const parts = computed<string[]>(() => {
  if (props.keys) return props.keys.map(translateKey)
  if (props.action) return bindings.keysFor(props.action).map(translateKey)
  return []
})
</script>

<template>
  <span class="kbd-stack">
    <kbd v-for="(p, i) in parts" :key="i" class="kbd-part">{{ p }}</kbd>
  </span>
</template>

<style scoped>
.kbd-stack {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  vertical-align: middle;
  font-size: 0;
}
.kbd-part {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  font-size: 11px;
  font-family: var(--font-mono);
  font-weight: 500;
  border: 1px solid var(--border);
  border-radius: var(--radius-2xs);
  background: var(--bg-card);
  color: var(--text-3);
  line-height: 1;
}
</style>
