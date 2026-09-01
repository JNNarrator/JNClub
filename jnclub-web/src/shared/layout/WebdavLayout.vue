<script setup lang="ts">
/**
 * WebdavLayout.vue — WebDAV 站点管理主壳
 * 套用与主界面一致的壳（MainLayout 左侧导航/移动端 TabBar）+ 统一 JPageShell 页面壳。
 */
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import MainLayout from './MainLayout.vue'
import JPageShell from './JPageShell.vue'
import WebdavView from '../../modules/bookmark/views/WebdavView.vue'
import HelpDrawer from '../components/HelpDrawer.vue'
import JPageHeader from '../components/ui/JPageHeader.vue'
import { useAppShortcuts } from '../composables/useAppShortcuts'

const { isDark } = defineProps<{ isDark: boolean }>()
const emit = defineEmits<{ 'toggle-theme': [] }>()
const router = useRouter()

const refreshTick = ref(0)
const handleRefresh = () => { refreshTick.value++ }

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
    <JPageShell>
      <template #header>
        <JPageHeader
          title="WebDAV"
          subtitle="站点管理 · 简单文件管理"
          refresh
          theme
          back
          :is-dark="isDark"
          @refresh="handleRefresh"
          @toggle-theme="emit('toggle-theme')"
          @back="router.push('/')"
        />
      </template>

      <WebdavView :key="refreshTick" />
    </JPageShell>

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>