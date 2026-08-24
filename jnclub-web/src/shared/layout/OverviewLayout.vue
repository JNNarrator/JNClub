<script setup lang="ts">
/**
 * OverviewLayout.vue — 概览看板主壳
 * 套用与主界面一致的壳：MainLayout（左侧导航/移动端 TabBar）+ 统一页面头（主题/返回）。
 */
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import MainLayout from './MainLayout.vue'
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
    <div class="overview-page">
      <JPageHeader
        title="概览"
        subtitle="数据看板与快捷入口"
        theme
        back
        :is-dark="isDark"
        @toggle-theme="emit('toggle-theme')"
        @back="router.push('/')"
      />

      <div class="overview-body">
        <DashboardView />
      </div>
    </div>

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>

<style scoped>
.overview-page {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  z-index: 1;
  padding: 12px 24px 0;
}

.overview-body {
  flex: 1;
  min-height: 0;
  width: 100%;
  max-width: 1560px;
  margin: 0 auto;
  padding: 24px 28px;
  overflow-y: auto;
  background:
    radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%),
    var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  box-shadow: var(--glass-shadow);
}

@media (max-width: 767px) {
  .overview-page {
    padding: 8px 8px 0;
  }
  .overview-body {
    margin: 0;
    padding: 12px;
  }
}
</style>
