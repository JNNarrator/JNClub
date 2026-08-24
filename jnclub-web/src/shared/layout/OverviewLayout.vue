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

const { isDark } = defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const router = useRouter()

/** 概览内点左侧模块：跳回主界面并切换到对应模块 */
const handleModuleChange = (module: 'bookmarks' | 'notes' | 'files' | 'vault') => {
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
          @back="router.push('/')"
        />
      </template>

      <DashboardView />
    </JPageShell>

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>
