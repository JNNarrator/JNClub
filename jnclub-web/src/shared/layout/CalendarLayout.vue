<script setup lang="ts">
/**
 * CalendarLayout.vue — 日历主壳
 * 套用与主界面一致的壳：MainLayout（左侧导航/移动端 TabBar）+ 统一页面头（刷新/主题/返回）。
 */
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import MainLayout from './MainLayout.vue'
import CalendarView from '../../modules/bookmark/views/CalendarView.vue'
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

/** 顶栏刷新计数：递增触发 CalendarView 重新拉取 */
const refreshTick = ref(0)
const handleRefresh = () => { refreshTick.value++ }

/** 日历内点左侧模块：跳回主界面并切换到对应模块 */
const handleModuleChange = (module: 'bookmarks' | 'notes' | 'files' | 'vault') => {
  router.push({ path: '/', query: { module } })
}

const showHelp = ref(false)

useAppShortcuts({
  onToggleTheme: () => emit('toggle-theme'),
  onModuleChange: handleModuleChange,
  onOpenHelp: () => { showHelp.value = true },
})
</script>

<template>
  <MainLayout
    :is-dark="isDark"
    :active-module="'bookmarks'"
    @module-change="handleModuleChange"
  >
    <div class="calendar-page">
      <JPageHeader
        title="日历"
        subtitle="待办与便签的月视图"
        refresh
        theme
        back
        :is-dark="isDark"
        @refresh="handleRefresh"
        @toggle-theme="emit('toggle-theme')"
        @back="router.push('/')"
      />

      <div class="calendar-body">
        <CalendarView :refresh="refreshTick" />
      </div>
    </div>

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>

<style scoped>
.calendar-page {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  z-index: 1;
  padding: 12px 24px 0;
}

.calendar-body {
  flex: 1;
  min-height: 0;
  width: 100%;
  max-width: 1560px;
  margin: 0 auto;
  padding: 20px 24px;
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
  .calendar-page {
    padding: 8px 8px 0;
  }
  .calendar-body {
    margin: 0;
    padding: 12px;
  }
}
</style>
