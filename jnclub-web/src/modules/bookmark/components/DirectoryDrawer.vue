<script setup lang="ts">
/**
 * DirectoryDrawer.vue — 移动端目录抽屉（<768px）
 * 复用 FolderPanel；选中目录后自动收起并通知父级。
 */
import { NDrawer, NDrawerContent, NIcon } from 'naive-ui'
import { FolderOpen } from 'lucide-vue-next'
import FolderPanel from './FolderPanel.vue'

withDefaults(defineProps<{
  show: boolean
  directories: any[]
  selectedId: number | null
  type: number
}>(), {
  directories: () => [],
  selectedId: null,
  type: 1,
})

const emit = defineEmits<{
  'update:show': [v: boolean]
  select: [id: number]
  refresh: []
}>()

const handleSelect = async (id: number) => {
  emit('update:show', false)
  emit('select', id)
}
</script>

<template>
  <NDrawer
    :show="show"
    :width="280"
    placement="left"
    :mask-closable="true"
    class="mobile-dir-drawer"
    @update:show="(v: boolean) => emit('update:show', v)"
  >
    <NDrawerContent :native-scrollbar="false">
      <template #header>
        <span class="drawer-title">
          <NIcon :component="FolderOpen" size="16" style="margin-right: 6px; vertical-align: -2px;" />
          目录
        </span>
      </template>
      <FolderPanel
        :directories="directories"
        :selected-id="selectedId"
        :type="type"
        @select="handleSelect"
        @refresh="emit('refresh')"
      />
    </NDrawerContent>
  </NDrawer>
</template>

<style scoped>
.mobile-dir-drawer :deep(.n-drawer-body-content-wrapper) {
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
}
.drawer-title {
  display: inline-flex;
  align-items: center;
  font-weight: 600;
  color: var(--text-1);
}
</style>
