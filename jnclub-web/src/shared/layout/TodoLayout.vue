<script setup lang="ts">
/**
 * TodoLayout.vue — 待办清单主壳
 * 套用与主界面一致的壳：MainLayout（左侧导航/移动端 TabBar）+ 毛玻璃顶栏（面包屑「JNClub/待办」+ 刷新/主题/返回）
 */
import { NIcon, NButton, NBreadcrumb, NBreadcrumbItem } from 'naive-ui'
import { RefreshCw, Sun, Moon, ArrowLeft } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import MainLayout from './MainLayout.vue'
import TodoView from '../../modules/bookmark/views/TodoView.vue'
import HelpDrawer from '../components/HelpDrawer.vue'
import { useAppShortcuts } from '../composables/useAppShortcuts'

const { isDark } = defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const router = useRouter()

/** 顶栏刷新计数：递增触发 TodoView 重新拉取 */
const refreshTick = ref(0)
const handleRefresh = () => { refreshTick.value++ }

/** 待办内点左侧模块：跳回主界面并切换到对应模块（AppWrapper 从 URL query.module 定位） */
const handleModuleChange = (module: 'bookmarks' | 'notes' | 'files' | 'vault') => {
  router.push({ path: '/', query: { module } })
}

/** 快捷键帮助面板 */
const showHelp = ref(false)

/** 全局快捷键（待办壳同样生效：模块切换/主题/锁定密码库/帮助） */
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
    <div class="todo-page">
      <!-- 毛玻璃顶栏（与 Home 顶栏同款） -->
      <header class="home-header glass-header">
        <div class="header-left">
          <NBreadcrumb class="jnclub-breadcrumb">
            <NBreadcrumbItem @click="router.push('/')">JNClub</NBreadcrumbItem>
            <NBreadcrumbItem class="breadcrumb-current">待办</NBreadcrumbItem>
          </NBreadcrumb>
        </div>
        <div class="header-right">
          <NButton quaternary circle size="small" class="refresh-btn jnclub-bouncy" title="刷新" @click="handleRefresh">
            <template #icon><NIcon :component="RefreshCw" size="16" /></template>
          </NButton>
          <button type="button" class="theme-toggle-btn jnclub-bouncy" title="切换暗色模式" @click="emit('toggle-theme')">
            <NIcon :component="isDark ? Sun : Moon" size="16" />
          </button>
          <NButton quaternary circle size="small" class="refresh-btn jnclub-bouncy" title="返回" @click="router.push('/')">
            <template #icon><NIcon :component="ArrowLeft" size="16" /></template>
          </NButton>
        </div>
      </header>

      <!-- 主体：毛玻璃面板（与 Home 的 collection-column / recycle-body 同风格） -->
      <div class="todo-body">
        <TodoView :refresh="refreshTick" />
      </div>
    </div>

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>

<style scoped>
.todo-page {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  z-index: 1;
}

/* === 顶栏：与 Home 一致 === */
.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
  gap: 16px;
}
.header-left {
  flex: 1;
  min-width: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.jnclub-breadcrumb :deep(.n-breadcrumb-item__link) {
  cursor: pointer;
  font-size: var(--fs-md);
}
.breadcrumb-current :deep(.n-breadcrumb-item__link) {
  font-weight: 600;
  color: var(--text-1);
}
.refresh-btn {
  color: var(--text-2);
}
.refresh-btn:hover {
  color: var(--text-1);
  background: var(--hover-bg);
}
.theme-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  background: transparent;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  color: var(--text-2);
}
.theme-toggle-btn:hover {
  background: var(--hover-bg);
  color: var(--text-1);
}

/* === 主体：与 Home 的 collection-column 玻璃面板同风格 === */
.todo-body {
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

/* === 移动端适配（<768px），与 Home 一致 === */
@media (max-width: 767px) {
  .home-header {
    padding: 0 12px;
    height: 52px;
    gap: 8px;
  }
  .todo-body {
    margin: 0;
    padding: 12px;
  }
  .header-right {
    gap: 6px;
  }
  .header-right :deep(.n-button) {
    min-width: 40px;
    height: 40px;
  }
  .header-right :deep(.n-button) span {
    display: none;
  }
  .jnclub-breadcrumb :deep(.n-breadcrumb-item__link) {
    font-size: var(--fs-sm);
    max-width: 90px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .todo-body {
    padding-bottom: 12px;
  }
}
</style>
