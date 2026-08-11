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
  <div class="folder-panel">
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
.folder-panel {
  padding: 0 4px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
</style>
