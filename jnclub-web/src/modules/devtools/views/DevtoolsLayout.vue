<script setup lang="ts">
/**
 * DevtoolsLayout.vue — 开发者工具主壳（页内多标签）
 *
 * 对应 jnx `src/App.vue` 里的壳层部分（标签栏 + KeepAlive + componentMap），
 * 差异来自运行环境：
 * 1. jnx 是 Tauri 桌面端，标签状态只存在内存里；Web 端刷新即丢，因此标签页状态
 *    全部走 `useDevtoolsStore`（pinia + 用户偏好持久化），见该 store 的 hydrate 注释。
 * 2. JNClub 的 App.vue 对 `<router-view>` 用了 `:key="route.path"`，路由切换会重建
 *    组件；pinia store 存活在应用级，所以「切走再回来」能原样恢复。
 * 3. 工具视图靠 KeepAlive 保活（输入框内容、滚动位置都留着），所以 KeepAlive 的
 *    `include` 必须与各视图 `defineOptions({ name })` 完全一致。
 *
 * ⚠ 本组件必须保持**单根**：App.vue 的 `<Transition mode="out-in">` 只能作用于单根，
 * 多根会让状态机卡死（旧组件卸载后新组件永不挂载 → 整页白屏）。
 */
import { computed, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { NDropdown, NSpin } from 'naive-ui'
import { ChevronDown, Command, Home, Keyboard, Plus, Settings2, X } from 'lucide-vue-next'
import MainLayout from '@/shared/layout/MainLayout.vue'
import JPageShell from '@/shared/layout/JPageShell.vue'
import JPageHeader from '@/shared/components/ui/JPageHeader.vue'
import HelpDrawer from '@/shared/components/HelpDrawer.vue'
import DevtoolsPalette from '../components/DevtoolsPalette.vue'
import { useAppShortcuts } from '@/shared/composables/useAppShortcuts'
import type { ModuleKey } from '@/shared/types/nav'
import { ALL_TOOLS, type PaletteAction } from '../types'
import { useDevtoolsStore } from '../stores/tools'
import { useDevtoolsSettingsStore } from '../stores/settings'
import { useShortcutBindingsStore } from '../stores/shortcutBindings'
import { pruneAllDrafts } from '../composables/useToolDraft'
import { useKeyboardShortcut } from '../composables/useKeyboardShortcut'
import HomeView from './HomeView.vue'
import ToolJson from './ToolJson.vue'
import ToolCurl from './ToolCurl.vue'
import ToolConverter from './ToolConverter.vue'
import ToolCron from './ToolCron.vue'
import ToolClipboard from './ToolClipboard.vue'
import JsonJavabean from './JsonJavabean.vue'
import ToolDdlJava from './ToolDdlJava.vue'
import DevtoolsSettings from './DevtoolsSettings.vue'
import DevtoolsShortcuts from './DevtoolsShortcuts.vue'

const props = defineProps<{ isDark: boolean }>()
const emit = defineEmits<{ 'toggle-theme': [] }>()

const router = useRouter()
const store = useDevtoolsStore()
const settings = useDevtoolsSettingsStore()
const bindings = useShortcutBindingsStore()

/* ─── 组件注册表（键 = ALL_TOOLS[].component） ─── */
const componentMap: Record<string, Component> = {
  HomeView,
  ToolJson,
  ToolCurl,
  ToolConverter,
  ToolCron,
  ToolClipboard,
  JsonJavabean,
  ToolDdlJava,
  DevtoolsSettings,
  DevtoolsShortcuts,
}

/** KeepAlive 白名单：启动台 + 所有工具，名字与各视图 defineOptions({ name }) 一致 */
const keepAliveNames = ['DevtoolsHome', ...ALL_TOOLS.map((t) => t.component)]

const activeTab = computed(() => store.activeTab)
const currentComponent = computed<Component>(
  () => componentMap[activeTab.value?.component ?? 'HomeView'] ?? HomeView,
)
/** 每个标签独立缓存实例：KeepAlive 用 vnode key 区分 */
const currentKey = computed(() => store.activeTabId)

/* ─── 首屏就绪态（hydrate 完成前不渲染内容，避免闪一下启动台） ─── */
const ready = ref(false)

onMounted(async () => {
  pruneAllDrafts()
  // 偏好与绑定都是幂等的，可并行
  await Promise.all([settings.load(), bindings.load()])
  await store.hydrate()
  // 「恢复上次标签页」关闭时，每次进入都回到启动台
  if (!settings.values.reopenLastTab) store.goHome()
  ready.value = true
})

/* ─── 标签栏拖拽排序（原生 HTML5 DnD：标签栏里还有「启动台」与「+」两个非标签按钮，
       用 SortableJS 会把它们也当成可拖拽项，故手写最小实现） ─── */
const dragFrom = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

function onTabDragStart(index: number, e: DragEvent): void {
  dragFrom.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    // Firefox 需要写点数据才会触发 drop
    e.dataTransfer.setData('text/plain', String(index))
  }
}

function onTabDragOver(index: number, e: DragEvent): void {
  if (dragFrom.value === null) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOverIndex.value = index
}

function onTabDrop(index: number, e: DragEvent): void {
  e.preventDefault()
  const from = dragFrom.value
  dragFrom.value = null
  dragOverIndex.value = null
  if (from === null || from === index) return
  store.moveTab(from, index)
}

function onTabDragEnd(): void {
  dragFrom.value = null
  dragOverIndex.value = null
}

/* ─── 标签操作 ─── */
function selectTab(id: string): void {
  if (id === store.activeTabId) return
  store.setActiveTab(id)
}

function closeTab(id: string, e?: MouseEvent): void {
  e?.stopPropagation()
  store.closeTab(id)
}

/** 中键关闭（桌面端习惯） */
function onTabAuxClick(id: string, e: MouseEvent): void {
  if (e.button !== 1) return
  e.preventDefault()
  store.closeTab(id)
}

function closeCurrent(): void {
  if (store.activeTabId !== store.HOME_ID) store.closeTab(store.activeTabId)
}

/** 循环切标签（⌥←/⌥→） */
function cycle(step: number): void {
  const ids = store.openTabIds
  if (!ids.length) return
  const cur = ids.indexOf(store.activeTabId)
  if (cur < 0) {
    store.setActiveTab(ids[0])
    return
  }
  const next = (cur + step + ids.length) % ids.length
  store.setActiveTab(ids[next])
}

/* ─── 「+」下拉：全部工具 ─── */
const addOptions = computed(() =>
  ALL_TOOLS.map((t) => ({
    key: t.id,
    label: t.label,
    // 已打开的项标灰提示（仍可点，点了只切激活）
    disabled: false,
  })),
)
function handleAdd(key: string): void {
  store.setActiveTab(key)
}

/* ─── 命令面板（⌘⇧P，对齐 jnx 的 global.palette） ─── */
const showPalette = ref(false)

const paletteActions = computed<PaletteAction[]>(() => {
  const nav = ALL_TOOLS.map((t) => ({
    id: `tool.${t.id}`,
    label: t.label,
    category: '打开工具',
    keywords: [t.id, ...(t.keywords ?? []), t.desc ?? ''],
    run: () => store.setActiveTab(t.id),
  }))
  return [
    {
      id: 'nav.home',
      label: '回到启动台',
      category: '导航',
      keywords: ['home', '启动台', '首页'],
      run: () => store.goHome(),
    },
    ...nav,
    {
      id: 'nav.settings',
      label: '工具设置',
      category: '导航',
      keywords: ['settings', '设置', '偏好'],
      run: () => store.setActiveTab('settings'),
    },
    {
      id: 'nav.shortcuts',
      label: '快捷键设置',
      category: '导航',
      keywords: ['shortcuts', '快捷键', '键位'],
      run: () => store.setActiveTab('shortcuts'),
    },
    {
      id: 'tab.close',
      label: '关闭当前标签页',
      category: '标签页',
      keywords: ['close', '关闭'],
      run: closeCurrent,
    },
    {
      id: 'tab.closeAll',
      label: '关闭全部标签页',
      category: '标签页',
      keywords: ['close all', '关闭全部'],
      run: () => store.closeAll(),
    },
    {
      id: 'theme.toggle',
      label: '切换浅色 / 深色主题',
      category: '外观',
      keywords: ['theme', 'dark', 'light', '主题', '深色', '浅色'],
      run: () => emit('toggle-theme'),
    },
  ]
})

useKeyboardShortcut('palette.toggle', () => {
  showPalette.value = !showPalette.value
})

/* ─── 页内快捷键 ─── */
useKeyboardShortcut('nav.toHome', () => store.goHome())
useKeyboardShortcut('nav.toJson', () => store.setActiveTab('json'))
useKeyboardShortcut('nav.toHttp', () => store.setActiveTab('curl'))
useKeyboardShortcut('nav.toClipboard', () => store.setActiveTab('clipboard'))
useKeyboardShortcut('nav.toConverter', () => store.setActiveTab('converter'))
useKeyboardShortcut('nav.toCron', () => store.setActiveTab('cron'))
useKeyboardShortcut('nav.toSettings', () => store.setActiveTab('settings'))
useKeyboardShortcut('nav.toShortcuts', () => store.setActiveTab('shortcuts'))
useKeyboardShortcut('nav.closeTab', closeCurrent)
useKeyboardShortcut('nav.cycleNext', () => cycle(1))
useKeyboardShortcut('nav.cyclePrev', () => cycle(-1))

/* ─── 壳层交互 ─── */
const showHelp = ref(false)
const handleModuleChange = (module: ModuleKey): void => {
  router.push({ path: '/', query: { module } })
}

useAppShortcuts({
  onToggleTheme: () => emit('toggle-theme'),
  onModuleChange: handleModuleChange,
  onOpenHelp: () => {
    showHelp.value = true
  },
})

const subtitle = computed(() => {
  const count = store.openTabIds.length
  if (store.activeTabId === store.HOME_ID) return count ? `启动台 · 已打开 ${count} 个工具` : '启动台 · 选择一个工具开始'
  return `${activeTab.value?.label ?? ''} · 共 ${count} 个标签页`
})
</script>

<template>
  <MainLayout
    :is-dark="props.isDark"
    :active-module="'bookmarks'"
    @module-change="handleModuleChange"
  >
    <JPageShell panel-padding="0">
      <template #header>
        <JPageHeader
          title="开发者工具"
          :subtitle="subtitle"
          theme
          :is-dark="props.isDark"
          @toggle-theme="emit('toggle-theme')"
        >
          <template #actions>
            <button
              type="button"
              class="dt-hd-btn"
              title="命令面板（⌘⇧P）"
              aria-label="命令面板"
              @click="showPalette = true"
            >
              <Command :size="16" />
            </button>
            <button
              type="button"
              class="dt-hd-btn"
              :class="{ active: store.isHomeActive }"
              title="工具启动台"
              aria-label="工具启动台"
              @click="store.goHome()"
            >
              <Home :size="16" />
            </button>
            <button
              type="button"
              class="dt-hd-btn"
              :class="{ active: store.activeTabId === 'settings' }"
              title="工具设置"
              aria-label="工具设置"
              @click="store.setActiveTab('settings')"
            >
              <Settings2 :size="16" />
            </button>
            <button
              type="button"
              class="dt-hd-btn"
              :class="{ active: store.activeTabId === 'shortcuts' }"
              title="快捷键"
              aria-label="快捷键"
              @click="store.setActiveTab('shortcuts')"
            >
              <Keyboard :size="16" />
            </button>
          </template>
        </JPageHeader>
      </template>

      <div class="dt-shell">
        <!-- ─── 标签栏 ─── -->
        <div class="dt-tabbar" role="tablist" aria-label="开发者工具标签页">
          <button
            type="button"
            class="dt-tab dt-tab-home"
            :class="{ active: store.isHomeActive }"
            role="tab"
            :aria-selected="store.isHomeActive"
            title="工具启动台"
            @click="store.goHome()"
          >
            <Home :size="14" class="dt-tab-icon" />
            <span class="dt-tab-label">启动台</span>
          </button>

          <div
            v-for="(tab, index) in store.openTabs"
            :key="tab.id"
            class="dt-tab"
            :class="{
              active: store.activeTabId === tab.id,
              'drag-over': dragOverIndex === index && dragFrom !== index,
              dragging: dragFrom === index,
            }"
            :data-id="tab.id"
            role="tab"
            :aria-selected="store.activeTabId === tab.id"
            :title="tab.label"
            draggable="true"
            @dragstart="onTabDragStart(index, $event)"
            @dragover="onTabDragOver(index, $event)"
            @drop="onTabDrop(index, $event)"
            @dragend="onTabDragEnd"
            @click="selectTab(tab.id)"
            @auxclick="onTabAuxClick(tab.id, $event)"
          >
            <component :is="tab.icon" :size="14" class="dt-tab-icon" />
            <span class="dt-tab-label">{{ tab.label }}</span>
            <span
              class="dt-tab-close"
              role="button"
              tabindex="0"
              aria-label="关闭标签页"
              title="关闭"
              @click.stop="closeTab(tab.id, $event)"
              @keydown.enter.stop="closeTab(tab.id)"
              @keydown.space.stop.prevent="closeTab(tab.id)"
            >
              <X :size="12" />
            </span>
          </div>

          <NDropdown trigger="click" :options="addOptions" placement="bottom-start" @select="handleAdd">
            <button type="button" class="dt-tab-add" title="打开工具" aria-label="打开工具">
              <Plus :size="14" />
              <ChevronDown :size="12" />
            </button>
          </NDropdown>

          <div class="dt-tabbar-tail">
            <button
              v-if="store.openTabIds.length"
              type="button"
              class="dt-tabbar-text"
              title="关闭全部标签页"
              @click="store.closeAll()"
            >
              全部关闭
            </button>
          </div>
        </div>

        <!-- ─── 内容区 ─── -->
        <div class="dt-body">
          <div v-if="!ready" class="dt-loading">
            <NSpin size="small" />
            <span>正在恢复上次的标签页…</span>
          </div>
          <KeepAlive v-else :include="keepAliveNames">
            <component :is="currentComponent" :key="currentKey" />
          </KeepAlive>
        </div>
      </div>
    </JPageShell>

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
    <DevtoolsPalette v-model:show="showPalette" :actions="paletteActions" />
  </MainLayout>
</template>

<style scoped>
.dt-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* ═══ 页头操作按钮（与 JPageHeader 内置按钮同尺寸） ═══ */
.dt-hd-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  transition: background var(--dur-fast, 0.15s) ease, color var(--dur-fast, 0.15s) ease;
}
.dt-hd-btn:hover {
  background: var(--hover-bg);
  color: var(--text-1);
}
.dt-hd-btn.active {
  background: var(--brand-soft);
  color: var(--brand);
}
.dt-hd-btn:active {
  transform: scale(0.94);
}

/* ═══ 标签栏 ═══ */
.dt-tabbar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  padding: 8px 12px;
  overflow-x: auto;
  overflow-y: hidden;
  border-bottom: 1px solid var(--border);
  background: var(--bg-page);
  scrollbar-width: thin;
}
.dt-tabbar::-webkit-scrollbar {
  height: 4px;
}
.dt-tabbar::-webkit-scrollbar-thumb {
  background: var(--border);
  border-radius: var(--radius-pill);
}

.dt-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  max-width: 200px;
  height: 32px;
  padding: 0 8px 0 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-2);
  font-size: var(--fs-sm);
  font-family: inherit;
  cursor: pointer;
  user-select: none;
  transition: background var(--dur-fast, 0.15s) ease, color var(--dur-fast, 0.15s) ease,
    border-color var(--dur-fast, 0.15s) ease;
}
.dt-tab:hover {
  background: var(--hover-bg);
  color: var(--text-1);
}
.dt-tab.active {
  background: var(--bg-card);
  border-color: var(--border);
  color: var(--text-1);
  box-shadow: var(--shadow-1);
}
.dt-tab-home {
  padding-right: 10px;
}
.dt-tab-icon {
  flex-shrink: 0;
  color: var(--text-3);
}
.dt-tab.active .dt-tab-icon {
  color: var(--brand);
}
.dt-tab-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dt-tab.dragging {
  opacity: 0.45;
}
.dt-tab.drag-over {
  border-color: var(--brand);
  background: var(--brand-soft);
}
.dt-tab-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border-radius: var(--radius-2xs);
  color: var(--text-3);
  opacity: 0.55;
  transition: background var(--dur-fast, 0.15s) ease, opacity var(--dur-fast, 0.15s) ease;
}
.dt-tab:hover .dt-tab-close,
.dt-tab.active .dt-tab-close {
  opacity: 1;
}
.dt-tab-close:hover {
  background: var(--hover-bg);
  color: var(--danger);
}

.dt-tab-add {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
  height: 32px;
  padding: 0 8px;
  border: 1px dashed var(--border);
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
  transition: color var(--dur-fast, 0.15s) ease, border-color var(--dur-fast, 0.15s) ease;
}
.dt-tab-add:hover {
  color: var(--brand);
  border-color: var(--brand);
}

.dt-tabbar-tail {
  margin-left: auto;
  padding-left: 8px;
  flex: 0 0 auto;
}
.dt-tabbar-text {
  border: none;
  background: transparent;
  color: var(--text-3);
  font-size: var(--fs-xs);
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
}
.dt-tabbar-text:hover {
  color: var(--danger);
}

/* ═══ 内容区 ═══ */
.dt-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  position: relative;
}
.dt-body > :deep(*) {
  height: 100%;
}
.dt-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 100%;
  color: var(--text-3);
  font-size: var(--fs-sm);
}

/* ═══ 移动端 ═══ */
@media (max-width: 767px) {
  .dt-tabbar {
    padding: 6px 8px;
  }
  .dt-tab {
    max-width: 150px;
    height: 34px;
  }
  .dt-tab-close {
    width: 22px;
    height: 22px;
  }
  .dt-tabbar-tail {
    display: none;
  }
}
</style>
