<script setup lang="ts">
import FolderTree from './FolderTree.vue'

interface Directory {
  id: number
  parentId: number | null
  name: string
  type?: number
  sortOrder: number
  children?: Directory[]
}

defineProps<{
  directories: Directory[]
  selectedId: number | null
  type?: number
}>()

const emit = defineEmits<{
  select: [id: number]
  refresh: []
}>()
</script>

<template>
  <div class="folder-panel-card">
    <FolderTree
      :directories="directories"
      :selected-id="selectedId"
      :type="type"
      @select="(id: number) => emit('select', id)"
      @refresh="emit('refresh')"
    />
  </div>
</template>

<style scoped>
.folder-panel-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}
</style>
