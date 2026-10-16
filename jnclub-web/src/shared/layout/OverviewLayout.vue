<script setup lang="ts">
/**
 * OverviewLayout.vue — 概览看板主壳
 * 套用与主界面一致的壳（MainLayout 左侧导航/移动端 TabBar）+ 统一 JPageShell 页面壳。
 */
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import MainLayout from './MainLayout.vue'
import JPageShell from './JPageShell.vue'
import DashboardView from '../views/DashboardView.vue'
import HelpDrawer from '../components/HelpDrawer.vue'
import JPageHeader from '../components/ui/JPageHeader.vue'
import { useAppShortcuts } from '../composables/useAppShortcuts'
import { useSmartBack } from '../composables/useSmartBack'
import type { ModuleKey } from '../types/nav'

const { isDark } = defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const router = useRouter()

/** 顶栏「返回」：回到真实来路；直接打开概览 URL 时退回收藏夹（报告 IA-5） */
const { goBack } = useSmartBack()

/**
 * 注：模板里 <MainLayout :active-module="'bookmarks'"> 的 active-module 只是占位 ——
 * 概览是路由页，SideNav/MobileTabBar 的激活态与侧栏副标题一律按 route.name 判定
 * （见 SideNav.vue 的 moduleLabel），此值不参与显示；类型上只能是 ModuleKey
 * （不含 'overview'），故传默认模块。
 *
 * ⚠ 这类说明不要写成 <template> 根层级的 HTML 注释：那会让本组件从「单根」变成
 * 「多根 Fragment」（编译产物出现 DEV_ROOT_FRAGMENT），而 App.vue 的
 * <Transition name="page-fade" mode="out-in"> 只能作用于单根 —— 一旦多根，
 * out-in 的「先离场再进场」状态机卡死：旧组件已卸载、新组件永不挂载，
 * 表现为离开本页后整页白屏。
 */

/** 概览内点左侧模块：跳回主界面并切换到对应模块 */
const handleModuleChange = (module: ModuleKey) => {
  router.push({ path: '/', query: { module } })
}

/** 快捷键帮助面板 */
const showHelp = ref(false)

/** 全局快捷键（概览壳同样生效） */
useAppShortcuts({
  onToggleTheme: () => emit('toggle-theme'),
  onModuleChange: handleModuleChange,
  onOpenHelp: () => { showHelp.value = true },
})
</script>

<template>
  <MainLayout :is-dark="isDark" :active-module="'bookmarks'" @module-change="handleModuleChange">
    <JPageShell>
      <template #header>
        <JPageHeader
          title="概览"
          subtitle="数据看板与快捷入口"
          theme
          back
          :is-dark="isDark"
          @toggle-theme="emit('toggle-theme')"
          @back="goBack"
        />
      </template>

      <DashboardView />
    </JPageShell>

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>
