<script setup lang="ts">
/**
 * AppWrapper.vue — 主界面壳（M4-1：由 shared/views/ 移入模块内）
 * 嵌入 MainLayout（含 SideNav），管理 activeModule（后端偏好记忆 + localStorage 首屏兜底）
 * 将 isDark 透传至 Home。
 * 它装配的 Home/RecycleView 与四个 activeModule 取值全部属于本模块，
 * 属「模块自己的装配壳」而非跨模块复用的布局原语，故归入本模块。
 */
import { ref, watch, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '../../../shared/layout/MainLayout.vue'
import Home from './Home.vue'
import RecycleView from './RecycleView.vue'
import HelpDrawer from '../../../shared/components/HelpDrawer.vue'
import { useAppShortcuts } from '../../../shared/composables/useAppShortcuts'
import { useUserPreferences } from '../../../shared/composables/useUserPreferences'
import { useUiDensity } from '../../../shared/composables/useUiDensity'
import { useVaultStore } from '../stores/vault'
import { MODULE_KEYS, type ModuleKey } from '../../../shared/types/nav'

defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const MODULES: ModuleKey[] = MODULE_KEYS

const route = useRoute()
const router = useRouter()
const prefs = useUserPreferences()
const { density } = useUiDensity()

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
  onLockVault: () => {
    const vault = useVaultStore()
    if (vault.masterStatus?.unlocked) vault.lock()
  },
})
</script>

<template>
  <MainLayout
    :is-dark="isDark"
    :active-module="activeModule"
    :class="density === 'compact' ? 'app-density-compact' : ''"
    @toggle-theme="emit('toggle-theme')"
    @module-change="handleModuleChange"
  >
    <RecycleView v-if="isRecycle" />
    <Home v-else :active-module="activeModule" :is-dark="isDark" @module-change="handleModuleChange" @toggle-theme="emit('toggle-theme')" />

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>
