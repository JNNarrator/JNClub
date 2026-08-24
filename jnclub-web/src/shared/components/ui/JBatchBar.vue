<script setup lang="ts">
/**
 * JBatchBar.vue — 通用批量操作条
 * 供收藏/便签等多选批量操作使用；按需展示打标签/移动到/删除。
 */
import { NButton, NIcon } from 'naive-ui'
import { FolderInput, Tags, Trash2, X } from 'lucide-vue-next'

withDefaults(defineProps<{
  selectedCount: number
  allSelected: boolean
  showTag?: boolean
  showMove?: boolean
  showDelete?: boolean
}>(), {
  showTag: false,
  showMove: true,
  showDelete: true,
})

const emit = defineEmits<{
  'toggle-all': []
  tag: []
  move: []
  delete: []
  cancel: []
}>()
</script>

<template>
  <Transition name="batch-up">
    <div class="j-batch-bar">
      <span class="j-batch-info">已选 {{ selectedCount }} 项</span>
      <NButton size="small" quaternary @click="emit('toggle-all')">
        {{ allSelected ? '取消全选' : '全选' }}
      </NButton>
      <NButton v-if="showTag" size="small" type="primary" secondary @click="emit('tag')">
        <template #icon><NIcon :component="Tags" size="14" /></template>
        打标签
      </NButton>
      <NButton v-if="showMove" size="small" type="primary" secondary @click="emit('move')">
        <template #icon><NIcon :component="FolderInput" size="14" /></template>
        移动到
      </NButton>
      <NButton v-if="showDelete" size="small" type="error" secondary @click="emit('delete')">
        <template #icon><NIcon :component="Trash2" size="14" /></template>
        删除
      </NButton>
      <NButton size="small" quaternary @click="emit('cancel')">
        <template #icon><NIcon :component="X" size="14" /></template>
        取消
      </NButton>
    </div>
  </Transition>
</template>

<style scoped>
.j-batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  box-shadow: var(--shadow-2);
  flex-wrap: wrap;
}
.j-batch-info {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-2);
  margin-right: 4px;
}
.batch-up-enter-active,
.batch-up-leave-active {
  transition: opacity 180ms var(--ease), transform 180ms var(--ease);
}
.batch-up-enter-from,
.batch-up-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>
