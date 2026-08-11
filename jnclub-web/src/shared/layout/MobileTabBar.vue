<script setup lang="ts">
/**
 * MobileTabBar.vue — 移动端底部导航（<768px）
 * 收藏/便签/云盘/密码库 4 Tab + 回收站入口
 * 桌面端不渲染，由 MainLayout 按视口切换
 */
import { NIcon } from 'naive-ui'
import { Bookmark, StickyNote, Cloud, KeyRound, Trash2 } from 'lucide-vue-next'
import { useRouter } from 'vue-router'

const props = defineProps<{
  activeModule: 'bookmarks' | 'notes' | 'files' | 'vault'
}>()

const emit = defineEmits<{
  'module-change': [module: 'bookmarks' | 'notes' | 'files' | 'vault']
}>()

const router = useRouter()

type ModuleKey = 'bookmarks' | 'notes' | 'files' | 'vault'
interface TabDef { key: ModuleKey | 'recycle'; icon: any; label: string }
const TABS: TabDef[] = [
  { key: 'bookmarks', icon: Bookmark, label: '收藏' },
  { key: 'notes', icon: StickyNote, label: '便签' },
  { key: 'files', icon: Cloud, label: '云盘' },
  { key: 'vault', icon: KeyRound, label: '密码' },
  { key: 'recycle', icon: Trash2, label: '回收站' },
]

const handleTab = (tab: TabDef) => {
  if (tab.key === 'recycle') {
    router.push('/recycle')
    return
  }
  emit('module-change', tab.key as ModuleKey)
}
</script>

<template>
  <nav class="mobile-tabbar" aria-label="主导航">
    <button
      v-for="tab in TABS"
      :key="tab.key"
      type="button"
      class="tab-item jnclub-bouncy"
      :class="{ 'tab-active': props.activeModule === tab.key }"
      @click="handleTab(tab)"
    >
      <NIcon :component="tab.icon" :size="22" />
      <span class="tab-label">{{ tab.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.mobile-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border-top: 1px solid var(--glass-border);
  /* iPhone 安全区：底部留出 Home Indicator 空间 */
  padding-bottom: env(safe-area-inset-bottom);
  height: calc(56px + env(safe-area-inset-bottom));
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  height: 56px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--text-3);
  -webkit-tap-highlight-color: transparent;
}

.tab-label {
  font-size: 10px;
  line-height: 1.2;
}

.tab-active {
  color: var(--brand);
  font-weight: 600;
}
.tab-active :deep(.n-icon) {
  filter: drop-shadow(0 0 6px var(--focus-ring));
}
</style>
