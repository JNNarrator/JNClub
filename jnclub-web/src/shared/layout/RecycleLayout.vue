<script setup lang="ts">
/**
 * RecycleLayout.vue — 回收站主壳
 * 套用与主界面一致的壳：MainLayout（左侧导航/移动端 TabBar）+ 统一页面头（刷新/主题/返回）。
 */
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import MainLayout from './MainLayout.vue'
import RecycleView from '../../modules/bookmark/views/RecycleView.vue'
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

/** 顶栏刷新计数：递增触发 RecycleView 重新拉取 */
const refreshTick = ref(0)
const handleRefresh = () => { refreshTick.value++ }

/** 回收站内点左侧模块：跳回主界面并切换到对应模块（AppWrapper 从 URL query.module 定位） */
const handleModuleChange = (module: 'bookmarks' | 'notes' | 'files' | 'vault') => {
  router.push({ path: '/', query: { module } })
}

/** 快捷键帮助面板 */
const showHelp = ref(false)

/** 全局快捷键（回收站壳同样生效：模块切换/主题/锁定密码库/帮助） */
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
    <div class="recycle-page">
      <JPageHeader
        title="回收站"
        subtitle="软删除条目统一管理"
        refresh
        theme
        back
        :is-dark="isDark"
        @refresh="handleRefresh"
        @toggle-theme="emit('toggle-theme')"
        @back="router.push('/')"
      />

      <!-- 主体：毛玻璃面板（与 Home 的 collection-column 同风格） -->
      <div class="recycle-body">
        <RecycleView :refresh="refreshTick" />
      </div>
    </div>

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>

<style scoped>
.recycle-page {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  z-index: 1;
  padding: 12px 24px 0;
}

.recycle-body {
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
  .recycle-page {
    padding: 8px 8px 0;
  }
  .recycle-body {
    margin: 0;
    padding: 12px;
    padding-bottom: 12px;
  }
}
</style>
