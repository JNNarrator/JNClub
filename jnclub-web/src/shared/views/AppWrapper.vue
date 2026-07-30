<script setup lang="ts">
/**
 * AppWrapper.vue — 应用外壳
 * 嵌入 MainLayout（含 SideNav），管理 activeModule
 * 将 isDark 透传至 Home
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

const activeModule = ref<'bookmarks' | 'notes'>('bookmarks')

const handleModuleChange = (module: 'bookmarks' | 'notes') => {
  activeModule.value = module
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
