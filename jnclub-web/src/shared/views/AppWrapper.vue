<script setup lang="ts">
/**
 * AppWrapper.vue — 应用外壳
 * 嵌入 MainLayout（含 SideNav），管理 activeModule（后端偏好记忆 + localStorage 首屏兜底）
 * 将 isDark 透传至 Home
 */
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import Home from '../../modules/bookmark/views/Home.vue'
import { useUserPreferences } from '../composables/useUserPreferences'

defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

type ModuleKey = 'bookmarks' | 'notes' | 'files' | 'vault'
const MODULES: ModuleKey[] = ['bookmarks', 'notes', 'files', 'vault']

const route = useRoute()
const router = useRouter()
const prefs = useUserPreferences()

/** 从 URL query.module 读取合法模块；无则回退偏好/默认 */
const moduleFromUrl = (): ModuleKey => {
  const m = route.query.module
  if (typeof m === 'string' && (MODULES as string[]).includes(m)) return m as ModuleKey
  return prefs.get<ModuleKey>('module.activeModule', 'bookmarks')
}

/** 初始化：URL 优先（刷新定位），无则偏好兜底 */
const activeModule = ref<ModuleKey>(moduleFromUrl())

/** 模块切换 → 写入 URL query + 偏好持久化（同源双向同步） */
const handleModuleChange = (module: ModuleKey) => {
  activeModule.value = module
  prefs.set('module.activeModule', module)
  router.replace({ query: { ...route.query, module } })
}

/** 监听 URL 变化（浏览器前进/后退/手动改 URL）→ 同步模块 */
watch(() => route.query.module, (m) => {
  if (typeof m === 'string' && (MODULES as string[]).includes(m)) {
    activeModule.value = m as ModuleKey
  }
})
</script>

<template>
  <MainLayout
    :is-dark="isDark"
    :active-module="activeModule"
    @toggle-theme="emit('toggle-theme')"
    @module-change="handleModuleChange"
  >
    <Home :active-module="activeModule" :is-dark="isDark" @module-change="handleModuleChange" />
  </MainLayout>
</template>
