<script setup lang="ts">
/**
 * HelpDrawer.vue — 快捷键帮助面板（⌘/ 唤起，数据来自 shared/shortcuts）
 */
import { NDrawer, NDrawerContent, NIcon } from 'naive-ui'
import { Keyboard } from 'lucide-vue-next'
import { GLOBAL_SHORTCUTS } from '../shortcuts'

defineProps<{ show: boolean }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <NDrawer :show="show" :width="360" placement="right" @update:show="(v: boolean) => { if (!v) emit('close') }">
    <NDrawerContent title="键盘快捷键" closable @close="emit('close')">
      <div class="help-list">
        <div v-for="s in GLOBAL_SHORTCUTS" :key="s.action" class="help-item">
          <span class="help-label">
            <NIcon :component="Keyboard" size="13" class="help-icon" />
            {{ s.label }}
          </span>
          <kbd class="help-kbd">{{ s.display }}</kbd>
        </div>
      </div>
      <p class="help-tip">输入框聚焦时，仅搜索 / 主题 / 帮助 / 锁定密码库快捷键生效，其余自动让位给编辑。</p>
    </NDrawerContent>
  </NDrawer>
</template>

<style scoped>
.help-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.help-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  transition: background var(--dur) var(--ease);
}
.help-item:hover {
  background: var(--hover-bg);
}
.help-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-sm);
  color: var(--text-2);
}
.help-icon {
  color: var(--brand);
}
.help-kbd {
  font-family: var(--font-mono);
  font-size: var(--fs-sm);
  color: var(--text-1);
  background: var(--hover-bg);
  border: 1px solid var(--border);
  border-bottom-width: 2px;
  border-radius: 5px;
  padding: 2px 8px;
  white-space: nowrap;
}
.help-tip {
  margin-top: 16px;
  font-size: var(--fs-sm);
  color: var(--text-3);
  line-height: 1.6;
}
</style>
