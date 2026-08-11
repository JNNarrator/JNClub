<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { NLayoutSider, NIcon } from 'naive-ui'
import { Bookmark, StickyNote, Cloud, Trash2, KeyRound, Heart, Music } from 'lucide-vue-next'
import { useUserPreferences } from '../composables/useUserPreferences'
import NavItem from '../../modules/bookmark/components/NavItem.vue'
import { useDraggableSort } from '../../modules/bookmark/composables/useDraggableSort'
import { useRouter, useRoute } from 'vue-router'

const props = defineProps<{
  activeModule: 'bookmarks' | 'notes' | 'files' | 'vault'
  /** 折叠状态（可外部控制，用于移动端自动折叠） */
  collapsed?: boolean
}>()

const emit = defineEmits<{
  'module-change': [module: 'bookmarks' | 'notes' | 'files' | 'vault']
  'update:collapsed': [value: boolean]
}>()

const router = useRouter()
const route = useRoute()

const prefs = useUserPreferences()

/** 导航项（支持拖拽排序，顺序持久化到后端偏好 nav.order，带用户记忆） */
type NavKey = 'bookmarks' | 'notes' | 'files' | 'vault'
interface NavDef { key: NavKey; icon: any; label: string }
const NAV_META: Record<NavKey, Omit<NavDef, 'key'>> = {
  bookmarks: { icon: Bookmark, label: '收藏夹' },
  notes: { icon: StickyNote, label: '便签' },
  files: { icon: Cloud, label: '云盘' },
  vault: { icon: KeyRound, label: '密码库' },
}
const DEFAULT_ORDER: NavKey[] = ['bookmarks', 'notes', 'files', 'vault']

const navItems = ref<NavDef[]>(DEFAULT_ORDER.map(k => ({ key: k, ...NAV_META[k] })))

/** 从偏好恢复拖拽排序（localStorage 即时兜底 + 后端记忆） */
const loadNavOrder = () => {
  try {
    const order = prefs.get<NavKey[]>('nav.order', DEFAULT_ORDER)
    if (Array.isArray(order) && order.length) {
      const valid = order.filter(k => NAV_META[k])
      // 补齐偏好中缺失的默认导航项（如旧数据没有 vault/密码库），避免导航项意外消失
      const present = new Set<NavKey>(valid)
      DEFAULT_ORDER.forEach(k => { if (!present.has(k)) valid.push(k) })
      if (valid.length) {
        navItems.value = valid.map(k => ({ key: k, ...NAV_META[k] }))
      }
    }
  } catch { /* 保持默认 */ }
}
loadNavOrder()

/** 偏好加载完成后以后端记忆为准（用户记忆） */
watch(() => prefs.ready, (r) => {
  if (r) loadNavOrder()
})

/** 拖拽排序提交：更新顺序 + 持久化 */
const navListRef = ref<HTMLElement | null>(null)
let navSortTimer: ReturnType<typeof setTimeout> | null = null
const { init: initNavSort } = useDraggableSort(navListRef, (orderedKeys) => {
  // 防抖提交，避免频繁写后端
  const valid = orderedKeys.filter((k): k is NavKey => typeof k === 'string' && !!NAV_META[k as NavKey])
  if (!valid.length) return
  navItems.value = valid.map(k => ({ key: k, ...NAV_META[k] }))
  if (navSortTimer) clearTimeout(navSortTimer)
  navSortTimer = setTimeout(() => {
    prefs.set('nav.order', valid)
  }, 300)
})
onMounted(() => { initNavSort() })
</script>

<template>
  <NLayoutSider
    bordered collapse-mode="width" :collapsed-width="64" :width="240"
    :collapsed="props.collapsed ?? false" show-trigger="bar"
    :on-update:collapsed="(v: boolean) => emit('update:collapsed', v)"
    class="side-nav sidebar-glow"
  >
    <!-- Logo 区：渐变粉底 + heart -->
    <div :class="['logo-bar', { collapsed: props.collapsed }]">
      <div :class="['logo-icon-wrap', { collapsed: props.collapsed }]">
        <NIcon :component="Heart" :size="props.collapsed ? 20 : 18" color="#fff" />
      </div>
      <template v-if="!props.collapsed">
        <span class="logo-text">JNClub</span>
        <span class="logo-sub">{{ activeModule === 'bookmarks' ? '收藏夹' : activeModule === 'notes' ? '便签' : '云盘' }}</span>
      </template>
    </div>

    <nav ref="navListRef" class="nav-list">
      <div
        v-for="item in navItems"
        :key="item.key"
        :data-id="item.key"
        class="nav-item-wrap"
      >
        <NavItem
          :icon="item.icon" :label="item.label"
          :active="activeModule === item.key" :collapsed="props.collapsed ?? false"
          @click="emit('module-change', item.key)"
        />
      </div>
      <!-- 音乐入口（固定，不参与拖拽）：iframe 内嵌播放器 -->
      <div class="nav-item-wrap">
        <NavItem
          :icon="Music" label="音乐"
          :active="route.name === 'music'" :collapsed="props.collapsed ?? false"
          @click="router.push('/music')"
        />
      </div>
      <!-- 回收站入口（固定，不参与拖拽） -->
      <div class="nav-item-wrap">
        <NavItem
          :icon="Trash2" label="回收站"
          :active="false" :collapsed="props.collapsed ?? false"
          @click="router.push('/recycle')"
        />
      </div>
    </nav>
  </NLayoutSider>
</template>

<style scoped>
.side-nav {
  display: flex;
  flex-direction: column;
  transition: width var(--dur) var(--ease);
  overflow: hidden;
  position: relative;
  background: var(--glass-bg-trans) !important;
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
}

/* === Logo 区 === */
.logo-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 20px;
  background: transparent;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
  transition: padding var(--dur) var(--ease), justify-content var(--dur) var(--ease);
}
.logo-bar.collapsed {
  padding: 20px 0;
  justify-content: center;
}
.logo-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-1);
  flex-shrink: 0;
}
.logo-icon-wrap.collapsed {
  width: 40px;
  height: 40px;
}
.logo-text {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-1);
  letter-spacing: 1px;
  line-height: 1.2;
}
.logo-sub {
  font-size: var(--fs-xs);
  color: var(--text-3);
  margin-left: auto;
}

/* === 导航 === */
.nav-list {
  padding: 12px 12px;
  flex-shrink: 0;
}
.nav-item-wrap { position: relative; }

/* 侧栏拖拽视觉（导航项为 pill 圆角，保留本地覆写；chosen 全局统一） */
.nav-list :deep(.sortable-ghost) {
  opacity: 0.5;
  background: var(--brand-soft) !important;
  border-radius: var(--radius-pill);
  outline: 2px dashed var(--brand);
  outline-offset: -2px;
}
.nav-list :deep(.sortable-chosen .nav-item) { background: var(--hover-bg); }
</style>
