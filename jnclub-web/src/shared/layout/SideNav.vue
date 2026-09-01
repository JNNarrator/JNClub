<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { NLayoutSider, NIcon, NDropdown } from 'naive-ui'
import { Settings2 } from 'lucide-vue-next'
import { useUserPreferences } from '../composables/useUserPreferences'
import BrandLogo from '../components/BrandLogo.vue'
import NavItem from '../../modules/bookmark/components/NavItem.vue'
import NavEditorDrawer from './NavEditorDrawer.vue'
import CursorSettingsDrawer from './CursorSettingsDrawer.vue'
import ParticlesSettingsDrawer from './ParticlesSettingsDrawer.vue'
import { useUiDensity } from '../composables/useUiDensity'
import { useDraggableSort } from '../../modules/bookmark/composables/useDraggableSort'
import { useRouter, useRoute } from 'vue-router'
import { NAV_META, DEFAULT_ORDER, normalizeNavKeys, completeOrder, type NavDef, type NavKey } from './navConfig'

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

/** 侧栏副标题：模块/路由 → 中文名（补齐 vault/回收站，修复「密码库」误显示「云盘」） */
const moduleLabel = computed(() => {
  const n = route.name
  if (n === 'recycle') return '回收站'
  if (n === 'music') return '音乐'
  if (n === 'webdav') return 'WebDAV'
  if (n === 'extension') return '浏览器插件'
  const m = props.activeModule
  return m === 'bookmarks' ? '收藏夹'
    : m === 'notes' ? '便签'
    : m === 'files' ? '云盘'
    : m === 'vault' ? '密码库'
    : 'JNClub'
})

const prefs = useUserPreferences()
const { density, toggle } = useUiDensity()

/** 全量导航项（顺序 = 可见在前 + 隐藏在后，供拖拽/偏好维护） */
const allItems = ref<NavDef[]>(DEFAULT_ORDER.map(k => ({ key: k, ...NAV_META[k] })))
/** 用户隐藏的导航项（nav.hidden） */
const hiddenKeys = ref<NavKey[]>([])

/** 侧栏实际渲染项：全量顺序过滤隐藏 */
const visibleItems = computed(() => allItems.value.filter(i => !hiddenKeys.value.includes(i.key)))

const loadNavOrder = () => {
  const order = prefs.get<NavKey[]>('nav.order', DEFAULT_ORDER)
  if (Array.isArray(order) && order.length) {
    allItems.value = completeOrder(normalizeNavKeys(order)).map(k => ({ key: k, ...NAV_META[k] }))
  }
}

const loadNavHidden = () => {
  const hidden = prefs.get<NavKey[]>('nav.hidden', [])
  hiddenKeys.value = Array.isArray(hidden) ? normalizeNavKeys(hidden) : []
}

const refreshNav = () => { loadNavOrder(); loadNavHidden() }
refreshNav()

/** 偏好加载完成后以后端记忆为准（用户记忆） */
watch(() => prefs.ready, (r) => {
  if (r) refreshNav()
})

/** 拖拽排序提交：更新顺序 + 持久化（隐藏项保持相对序置于可见项之后） */
const navListRef = ref<HTMLElement | null>(null)
let navSortTimer: ReturnType<typeof setTimeout> | null = null
const { init: initNavSort } = useDraggableSort(navListRef, (orderedKeys) => {
  const valid = normalizeNavKeys(orderedKeys)
  if (!valid.length) return
  const hiddenRest = allItems.value.filter(i => !valid.includes(i.key))
  allItems.value = [...valid.map(k => ({ key: k, ...NAV_META[k] })), ...hiddenRest]
  if (navSortTimer) clearTimeout(navSortTimer)
  navSortTimer = setTimeout(() => {
    prefs.set('nav.order', allItems.value.map(i => i.key))
  }, 300)
})
onMounted(() => { initNavSort() })

/** 点击导航项：模块走 module-change，路由走 router.push */
const handleNavClick = (item: NavDef) => {
  if (item.kind === 'module') {
    emit('module-change', item.key as 'bookmarks' | 'notes' | 'files' | 'vault')
    return
  }
  router.push(item.target)
}

/** 激活态：模块按 activeModule（仅主应用页 / 生效，避免与概览/回收站等路由页同时高亮），路由按 route.name（与 key 同名） */
const isActive = (item: NavDef) => {
  if (item.kind === 'module') return route.name === 'app' && props.activeModule === item.key
  return route.name === item.key
}

/** 编辑导航抽屉 */
const showNavEditor = ref(false)
/** 抽屉每次持久化后刷新侧栏（以 prefs 为准） */
const handleNavEditorSaved = () => { refreshNav() }

/** 光标样式设置抽屉 */
const showCursorSettings = ref(false)

/** 背景特效设置抽屉 */
const showParticlesSettings = ref(false)

/** 个性化设置入口（收纳光标/背景/导航编辑/密度） */
const settingsOptions = computed(() => [
  { label: '光标样式', key: 'cursor' },
  { label: '背景特效', key: 'particles' },
  { label: '编辑导航', key: 'nav' },
  { label: density.value === 'compact' ? '切换舒适模式' : '切换紧凑模式', key: 'density' },
])

const handleSettingsSelect = async (key: string) => {
  if (key === 'cursor') showCursorSettings.value = true
  else if (key === 'particles') showParticlesSettings.value = true
  else if (key === 'nav') showNavEditor.value = true
  else if (key === 'density') await toggle()
}
</script>

<template>
  <NLayoutSider
    bordered collapse-mode="width" :collapsed-width="64" :width="240"
    :collapsed="props.collapsed ?? false" show-trigger="bar"
    :on-update:collapsed="(v: boolean) => emit('update:collapsed', v)"
    class="side-nav sidebar-glow"
  >
    <!-- Logo 区：统一品牌标识（粉渐变 heart） -->
    <div :class="['logo-bar', { collapsed: props.collapsed }]">
      <BrandLogo
        :size="props.collapsed ? 40 : 36"
        :show-text="!props.collapsed"
        :collapsed="props.collapsed"
      />
      <span v-if="!props.collapsed" class="logo-sub">{{ moduleLabel }}</span>
    </div>

    <nav ref="navListRef" class="nav-list">
      <div
        v-for="item in visibleItems"
        :key="item.key"
        :data-id="item.key"
        class="nav-item-wrap"
      >
        <NavItem
          :icon="item.icon" :label="item.label"
          :active="isActive(item)" :collapsed="props.collapsed ?? false"
          @click="handleNavClick(item)"
        />
      </div>
    </nav>

    <!-- 侧栏底部入口：个性化（光标/背景/导航编辑，不参与拖拽） -->
    <div :class="['nav-editor-bar', { collapsed: props.collapsed }]">
      <NDropdown :options="settingsOptions" placement="right-start" trigger="click" @select="handleSettingsSelect">
        <button type="button" class="nav-editor-btn jnclub-bouncy" title="个性化">
          <NIcon :component="Settings2" :size="18" />
          <span v-if="!props.collapsed" class="nav-editor-text">个性化</span>
        </button>
      </NDropdown>
    </div>
  </NLayoutSider>

  <!-- 导航编辑抽屉 -->
  <NavEditorDrawer :show="showNavEditor" @close="showNavEditor = false" @saved="handleNavEditorSaved" />

  <!-- 光标样式设置抽屉 -->
  <CursorSettingsDrawer :show="showCursorSettings" @close="showCursorSettings = false" />

  <!-- 背景特效设置抽屉 -->
  <ParticlesSettingsDrawer :show="showParticlesSettings" @close="showParticlesSettings = false" />
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
.logo-sub {
  font-size: 11px;
  color: var(--text-3);
  margin-left: auto;
}

/* === 导航 === */
.nav-list {
  flex: 1;
  padding: 12px 12px;
  overflow-y: auto;
  min-height: 0;
}
.nav-item-wrap { position: relative; }

/* === 编辑导航入口（左下角小图标） === */
/* NLayoutSider 内容实际在内部 scroll-container 中：需让其成为 flex 列布局，
   nav-list(flex:1) 撑满、编辑按钮才能真正贴到左下角 */
.side-nav :deep(.n-layout-sider-scroll-container) {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.nav-editor-bar {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-start;
  gap: 6px;
  padding: 6px 10px 10px;
  border-top: 1px solid var(--glass-border);
}
.nav-editor-bar.collapsed {
  justify-content: center;
  flex-direction: column;
  align-items: center;
  padding: 8px 0 10px;
}
.nav-editor-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 36px;
  height: 36px;
  padding: 0 14px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
  transition: all .18s ease;
}
.nav-editor-bar.collapsed .nav-editor-btn {
  width: 36px;
  padding: 0;
}
.nav-editor-text {
  font-size: var(--fs-md);
  font-weight: 500;
  white-space: nowrap;
}
.nav-editor-btn:hover {
  color: var(--brand);
  background: var(--brand-soft);
}

/* 侧栏拖拽视觉 */
.nav-list :deep(.sortable-ghost) {
  opacity: 0.5;
  background: var(--brand-soft) !important;
  border-radius: var(--radius-pill);
  outline: 2px dashed var(--brand);
  outline-offset: -2px;
}
.nav-list :deep(.sortable-chosen) { cursor: grabbing; }
.nav-list :deep(.sortable-chosen .nav-item) { background: var(--hover-bg); }
</style>
