<script setup lang="ts">
/**
 * MobileTabBar.vue — 移动端底部导航（<768px）
 * 跟随用户导航配置（nav.hidden）：隐藏的项不显示；空时回退默认 5 项。
 * 末尾固定「更多」：进入未展示在底部 Tab 的导航项（音乐/下载中心/被隐藏项），并支持重新显示。
 * 桌面端不渲染，由 MainLayout 按视口切换。
 */
import { ref, computed, watch } from 'vue'
import { NIcon, NDrawer, NDrawerContent, NButton } from 'naive-ui'
import { MoreHorizontal, Eye } from 'lucide-vue-next'
import { useRouter, useRoute } from 'vue-router'
import { useUserPreferences } from '../composables/useUserPreferences'
import { NAV_META, MOBILE_KEYS, DEFAULT_ORDER, normalizeNavKeys, type NavDef } from './navConfig'

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

/** 「更多」抽屉：未展示在底部 Tab 的项 = 未进入 MOBILE_KEYS 的项 + 被用户隐藏的项 */
const MORE_ITEMS = computed<NavDef[]>(() => {
  const visibleKeys = new Set(TABS.value.map(t => t.key))
  return DEFAULT_ORDER
    .filter(k => !visibleKeys.has(k))
    .map(k => ({ key: k, ...NAV_META[k] }))
})

const showMore = ref(false)

const handleTab = (tab: NavDef) => {
  if (tab.kind === 'route') {
    router.push(tab.target)
    return
  }
  emit('module-change', tab.key as 'bookmarks' | 'notes' | 'files' | 'vault')
}

const handleMore = (tab: NavDef) => {
  showMore.value = false
  handleTab(tab)
}

/** 从「更多」重新显示被隐藏的导航项 */
const unhide = (key: string) => {
  hiddenKeys.value = hiddenKeys.value.filter(k => k !== key)
  prefs.set('nav.hidden', hiddenKeys.value)
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
    <button
      type="button"
      class="tab-item jnclub-bouncy"
      :class="{ 'tab-active': showMore }"
      @click="showMore = true"
    >
      <NIcon :component="MoreHorizontal" :size="22" />
      <span class="tab-label">更多</span>
    </button>
  </nav>

  <NDrawer
    :show="showMore"
    placement="bottom"
    :height="'auto'"
    @update:show="(v: boolean) => showMore = v"
  >
    <NDrawerContent title="更多功能" closable>
      <div class="more-list">
        <div v-for="item in MORE_ITEMS" :key="item.key" class="more-row">
          <button
            type="button"
            class="more-item jnclub-bouncy"
            :class="{ 'more-active': isActive(item) }"
            @click="handleMore(item)"
          >
            <NIcon :component="item.icon" :size="18" />
            <span class="more-label">{{ item.label }}</span>
            <span v-if="hiddenKeys.includes(item.key)" class="hidden-badge">已隐藏</span>
          </button>
          <NButton
            v-if="hiddenKeys.includes(item.key)"
            size="tiny"
            quaternary
            class="more-unhide"
            @click="unhide(item.key)"
          >
            <template #icon><NIcon :component="Eye" :size="14" /></template>
            显示
          </NButton>
        </div>
      </div>
      <p v-if="!MORE_ITEMS.length" class="more-empty">当前没有更多功能</p>
    </NDrawerContent>
  </NDrawer>
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
  height: calc(var(--tabbar-height, 56px) + env(safe-area-inset-bottom));
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
  height: var(--tabbar-height, 56px);
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

/* 更多抽屉 */
.more-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.more-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.more-item {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  text-align: left;
}
.more-item:hover {
  background: var(--hover-bg);
  color: var(--text-1);
}
.more-item.more-active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
}
.more-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hidden-badge {
  font-size: var(--fs-xs);
  color: var(--text-3);
  background: var(--hover-bg);
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  flex-shrink: 0;
}
.more-unhide {
  flex-shrink: 0;
}
.more-empty {
  padding: 20px 0;
  text-align: center;
  color: var(--text-3);
  font-size: var(--fs-sm);
}
</style>
