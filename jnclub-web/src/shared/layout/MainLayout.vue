<script setup lang="ts">
/**
 * MainLayout.vue — 应用主布局
 * 嵌入 SideNav（三段式）+ 内容区
 */
import { ref } from 'vue'
import { NLayout, NLayoutContent } from 'naive-ui'
import SideNav from './SideNav.vue'

defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
  'module-change': [module: 'bookmarks' | 'notes' | 'files']
}>()

const activeModule = ref<'bookmarks' | 'notes' | 'files'>('bookmarks')

const handleModuleChange = (module: 'bookmarks' | 'notes' | 'files') => {
  activeModule.value = module
  emit('module-change', module)
}
</script>

<template>
  <NLayout has-sider class="app-layout">
    <SideNav
      :is-dark="isDark"
      :active-module="activeModule"
      @toggle-theme="emit('toggle-theme')"
      @module-change="handleModuleChange"
    />

    <NLayout>
      <NLayoutContent class="app-content">
        <slot />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}

.app-content {
  height: 100vh;
  overflow: auto;
  background: var(--bg-page);
}
</style>
