<script setup lang="ts">
/**
 * MobileTabBar.vue — 移动端底部导航（<768px）
 * 跟随用户导航配置（nav.hidden）：隐藏的项不显示；空时回退默认 5 项
 * 桌面端不渲染，由 MainLayout 按视口切换
 */
import { ref, computed, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { useRouter, useRoute } from 'vue-router'
import { useUserPreferences } from '../composables/useUserPreferences'
import { NAV_META, MOBILE_KEYS, normalizeNavKeys, type NavDef } from './navConfig'

const props = defineProps<{
  activeModule: 'bookmarks' | 'notes' | 'files' | 'vault'
}>()

const emit = defineEmits<{
  'module-change': [module: 'bookmarks' | 'notes' | 'files' | 'vault']
}>()

const router = useRouter()
const route = useRoute()
const prefs = useUserPreferences()

/** 用户隐藏的导航项（nav.hidden） */
const hiddenKeys = ref<string[]>([])

const loadHidden = () => {
  const hidden = prefs.get<any[]>('nav.hidden', [])
  hiddenKeys.value = Array.isArray(hidden) ? normalizeNavKeys(hidden) : []
}
loadHidden()

watch(() => prefs.ready, (r) => {
  if (r) loadHidden()
})

/** 参与移动端显示的 tab：默认 5 项按 nav.hidden 过滤；全隐藏时回退默认（防空 TabBar） */
const TABS = computed<NavDef[]>(() => {
  const shown = MOBILE_KEYS.filter(k => !hiddenKeys.value.includes(k))
  const keys = shown.length ? shown : MOBILE_KEYS
  return keys.map(k => ({ key: k, ...NAV_META[k] }))
})

const handleTab = (tab: NavDef) => {
  if (tab.kind === 'route') {
    router.push(tab.target)
    return
  }
  emit('module-change', tab.key as 'bookmarks' | 'notes' | 'files' | 'vault')
}

/** 激活态：模块按 activeModule（仅主应用页 / 生效，避免与概览/回收站等路由页同时高亮），路由按 route.name（与 key 同名） */
const isActive = (tab: NavDef) => {
  if (tab.kind === 'module') return route.name === 'app' && props.activeModule === tab.key
  return route.name === tab.key
}
</script>

<template>
  <nav class="mobile-tabbar" aria-label="主导航">
    <button
      v-for="tab in TABS"
      :key="tab.key"
      type="button"
      class="tab-item jnclub-bouncy"
      :class="{ 'tab-active': isActive(tab) }"
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
  justify-content: flex-start;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border-top: 1px solid var(--glass-border);
  /* iPhone 安全区：底部留出 Home Indicator 空间 */
  padding-bottom: env(safe-area-inset-bottom);
  height: calc(56px + env(safe-area-inset-bottom));
}
.mobile-tabbar::-webkit-scrollbar {
  display: none;
}

.tab-item {
  flex: 0 0 64px;
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
  position: relative;
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

/* 激活指示条 */
.tab-item.tab-active::after {
  content: '';
  position: absolute;
  bottom: 6px;
  left: 50%;
  transform: translateX(-50%);
  width: 16px;
  height: 3px;
  border-radius: 2px;
  background: var(--brand);
  transition: width var(--dur) var(--ease);
}
.tab-item.tab-active:hover::after {
  width: 22px;
}
</style>
