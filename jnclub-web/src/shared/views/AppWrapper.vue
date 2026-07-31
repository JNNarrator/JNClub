<script setup lang="ts">
/**
 * AppWrapper.vue — 应用外壳
 * 嵌入 MainLayout（含 SideNav），管理 activeModule
 * 将 isDark 透传至 Home
 * 刷新后记住上次访问的功能页
 */
import { ref } from 'vue'
import MainLayout from '../layout/MainLayout.vue'
import Home from '../../modules/bookmark/views/Home.vue'

defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const STORAGE_KEY = 'jnclub-active-module'

function getStoredModule(): 'bookmarks' | 'notes' {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'bookmarks' || stored === 'notes') return stored
  return 'bookmarks'
}

const activeModule = ref<'bookmarks' | 'notes'>(getStoredModule())

const handleModuleChange = (module: 'bookmarks' | 'notes') => {
  activeModule.value = module
  localStorage.setItem(STORAGE_KEY, module)
}
</script>

<template>
  <MainLayout
    :is-dark="isDark"
    @toggle-theme="emit('toggle-theme')"
    @module-change="handleModuleChange"
  >
    <Home :active-module="activeModule" :is-dark="isDark" />
  </MainLayout>
</template>
