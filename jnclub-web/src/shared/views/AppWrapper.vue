<script setup lang="ts">
/**
 * AppWrapper.vue — 应用外壳
 * 嵌入 MainLayout（含 SideNav），管理 activeModule（后端偏好记忆 + localStorage 首屏兜底）
 * 将 isDark 透传至 Home
 */
import { ref, onMounted } from 'vue'
import MainLayout from '../layout/MainLayout.vue'
import Home from '../../modules/bookmark/views/Home.vue'
import { useUserPreferences } from '../composables/useUserPreferences'

defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const prefs = useUserPreferences()
/** 初始化：localStorage 兜底避免首屏闪烁，load 完成后以后端偏好为准 */
const activeModule = ref<'bookmarks' | 'notes' | 'files' | 'vault'>(prefs.get('module.activeModule', 'bookmarks'))

onMounted(() => {
  prefs.load().then(() => {
    activeModule.value = prefs.get('module.activeModule', 'bookmarks')
  })
})

const handleModuleChange = (module: 'bookmarks' | 'notes' | 'files' | 'vault') => {
  activeModule.value = module
  prefs.set('module.activeModule', module)
}
</script>

<template>
  <MainLayout
    :is-dark="isDark"
    @toggle-theme="emit('toggle-theme')"
    @module-change="handleModuleChange"
  >
    <Home :active-module="activeModule" :is-dark="isDark" @module-change="handleModuleChange" />
  </MainLayout>
</template>
