<script setup lang="ts">
/**
 * MainLayout.vue — 应用主布局
 * 嵌入 SideNav（三段式）+ 内容区
 * 移动端（<768px）：侧栏自动折叠为窄条，顶栏隐藏 FAB 标签文字由 Home 处理
 */
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { NLayout, NLayoutContent } from 'naive-ui'
import SideNav from './SideNav.vue'

defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
  'module-change': [module: 'bookmarks' | 'notes' | 'files' | 'vault']
}>()

const activeModule = ref<'bookmarks' | 'notes' | 'files' | 'vault'>('bookmarks')

const handleModuleChange = (module: 'bookmarks' | 'notes' | 'files' | 'vault') => {
  activeModule.value = module
  emit('module-change', module)
}

// ========== 移动端响应式：侧栏自动折叠 ==========
const collapsed = ref(false)
const isMobile = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) collapsed.value = true
  else collapsed.value = false
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})
onBeforeUnmount(() => window.removeEventListener('resize', checkMobile))

watch(isMobile, (m) => {
  // 移动端强制折叠；切回桌面展开
  collapsed.value = m
})
</script>

<template>
  <NLayout has-sider class="app-layout">
    <SideNav
      :is-dark="isDark"
      :active-module="activeModule"
      :collapsed="collapsed"
      @toggle-theme="emit('toggle-theme')"
      @module-change="handleModuleChange"
      @update:collapsed="(v: boolean) => collapsed = v"
    />

    <NLayout>
      <NLayoutContent class="app-content">
        <slot />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}

.app-content {
  height: 100vh;
  overflow: auto;
  background: var(--bg-page);
}
</style>
