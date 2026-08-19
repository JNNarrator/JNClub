<script setup lang="ts">
/**
 * AppWrapper.vue — 应用外壳
 * 嵌入 MainLayout（含 SideNav），管理 activeModule（后端偏好记忆 + localStorage 首屏兜底）
 * 将 isDark 透传至 Home
 */
import { ref, watch, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import Home from '../../modules/bookmark/views/Home.vue'
import RecycleView from '../../modules/bookmark/views/RecycleView.vue'
import HelpDrawer from '../components/HelpDrawer.vue'
import { useAppShortcuts } from '../composables/useAppShortcuts'
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

/** 回收站独立路由：同样进入主布局壳（侧边栏/移动端 TabBar 保持），仅内容区不同 */
const isRecycle = computed(() => route.name === 'recycle')

/** 快捷键帮助面板 */
const showHelp = ref(false)

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

/** 启动时加载后端偏好（nav 排序/视图模式/模块记忆等水合） */
onMounted(() => {
  prefs.load()
})

/** 全局快捷键：模块切换 / 主题 / 锁定密码库 / 帮助（放在 handleModuleChange 定义之后） */
useAppShortcuts({
  onToggleTheme: () => emit('toggle-theme'),
  onModuleChange: handleModuleChange,
  onOpenHelp: () => { showHelp.value = true },
})
</script>

<template>
  <MainLayout
    :is-dark="isDark"
    :active-module="activeModule"
    @toggle-theme="emit('toggle-theme')"
    @module-change="handleModuleChange"
  >
    <RecycleView v-if="isRecycle" />
    <Home v-else :active-module="activeModule" :is-dark="isDark" @module-change="handleModuleChange" @toggle-theme="emit('toggle-theme')" />

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>
