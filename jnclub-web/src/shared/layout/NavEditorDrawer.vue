<script setup lang="ts">
/**
 * NavEditorDrawer.vue — 导航编辑抽屉（侧栏底部「编辑导航」唤起）
 * 已显示项可拖拽排序/隐藏；被隐藏项在「可添加」区随时恢复
 * 改动即持久化到用户偏好（nav.order / nav.hidden，后端按用户隔离）
 */
import { ref, computed, watch, onMounted } from 'vue'
import { NDrawer, NButton, NIcon, useMessage } from 'naive-ui'
import { Settings2, GripVertical, EyeOff, Plus } from 'lucide-vue-next'
import { useUserPreferences } from '../composables/useUserPreferences'
import { useDraggableSort } from '../../modules/bookmark/composables/useDraggableSort'
import { NAV_META, DEFAULT_ORDER, normalizeNavKeys, completeOrder, type NavKey } from './navConfig'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
  /** 每次持久化后通知 SideNav 刷新（以 prefs 为准） */
  saved: []
}>()

const prefs = useUserPreferences()
const message = useMessage()

/** 全量顺序（可见在前 + 隐藏在后）与隐藏集合，打开抽屉时从偏好初始化 */
const order = ref<NavKey[]>([])
const hidden = ref<NavKey[]>([])

const reload = () => {
  const o = prefs.get<NavKey[]>('nav.order', DEFAULT_ORDER)
  order.value = completeOrder(Array.isArray(o) ? normalizeNavKeys(o) : [])
  const h = prefs.get<NavKey[]>('nav.hidden', [])
  hidden.value = Array.isArray(h) ? normalizeNavKeys(h) : []
}

watch(() => props.show, (v) => { if (v) reload() })

const visibleItems = computed(() =>
  order.value.filter(k => !hidden.value.includes(k)).map(k => ({ key: k, ...NAV_META[k] }))
)
const hiddenItems = computed(() =>
  order.value.filter(k => hidden.value.includes(k)).map(k => ({ key: k, ...NAV_META[k] }))
)

/** 移动端抽屉全宽，桌面 420（复用 SearchDrawer 形态） */
const isMobileWidth = () => (typeof window !== 'undefined' && window.innerWidth < 768 ? '100%' : 420)

const persistOrder = () => { prefs.set('nav.order', order.value); emit('saved') }
const persistHidden = () => { prefs.set('nav.hidden', hidden.value); emit('saved') }

const hideItem = (key: NavKey) => {
  if (visibleItems.value.length <= 1) {
    message.warning('至少保留一个导航项')
    return
  }
  hidden.value.push(key)
  persistHidden()
}

const addItem = (key: NavKey) => {
  hidden.value = hidden.value.filter(k => k !== key)
  persistHidden()
}

/** 已显示区拖拽排序：可见项新序在前，隐藏项保持相对序在后 */
const visibleListRef = ref<HTMLElement | null>(null)
let sortTimer: ReturnType<typeof setTimeout> | null = null
const { init: initVisibleSort } = useDraggableSort(visibleListRef, (orderedKeys) => {
  const valid = normalizeNavKeys(orderedKeys)
  if (!valid.length) return
  const hiddenRest = order.value.filter(k => hidden.value.includes(k))
  order.value = [...valid, ...hiddenRest]
  if (sortTimer) clearTimeout(sortTimer)
  sortTimer = setTimeout(persistOrder, 300)
})
onMounted(() => { initVisibleSort() })
</script>

<template>
  <NDrawer
    v-model:show="props.show"
    :width="isMobileWidth()"
    placement="right"
    class="nav-editor-drawer"
    @update:show="(v: boolean) => !v && emit('close')"
  >
    <div class="nav-editor-panel">
      <div class="editor-header">
        <div class="editor-title">
          <NIcon :component="Settings2" size="16" />
          编辑导航
        </div>
        <span class="editor-hint">拖拽调整顺序，可随时隐藏 / 恢复</span>
      </div>

      <!-- 已显示 -->
      <div class="editor-section">
        <div class="section-label">已显示（{{ visibleItems.length }}）</div>
        <div ref="visibleListRef" class="visible-list">
          <div
            v-for="item in visibleItems"
            :key="item.key"
            :data-id="item.key"
            class="editor-row"
          >
            <span class="drag-handle"><NIcon :component="GripVertical" size="14" /></span>
            <NIcon :component="item.icon" size="16" class="row-icon" />
            <span class="row-label">{{ item.label }}</span>
            <NButton quaternary circle size="tiny" class="row-action" title="隐藏" @click="hideItem(item.key)">
              <template #icon><NIcon :component="EyeOff" size="14" /></template>
            </NButton>
          </div>
        </div>
      </div>

      <!-- 可添加 -->
      <div v-if="hiddenItems.length" class="editor-section">
        <div class="section-label">可添加（{{ hiddenItems.length }}）</div>
        <div class="hidden-list">
          <div
            v-for="item in hiddenItems"
            :key="item.key"
            class="editor-row"
          >
            <span class="drag-handle placeholder" />
            <NIcon :component="item.icon" size="16" class="row-icon muted" />
            <span class="row-label muted">{{ item.label }}</span>
            <NButton quaternary circle size="tiny" class="row-action" title="添加" @click="addItem(item.key)">
              <template #icon><NIcon :component="Plus" size="14" /></template>
            </NButton>
          </div>
        </div>
      </div>
    </div>
  </NDrawer>
</template>

<style scoped>
.nav-editor-drawer :deep(.n-drawer-body-content-wrapper) {
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
}

.nav-editor-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 4px 2px;
}

.editor-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.editor-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}
.editor-hint {
  font-size: 12px;
  color: var(--text-3);
}

.editor-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-3);
  letter-spacing: 0.5px;
}

.visible-list,
.hidden-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.editor-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur) var(--ease);
  cursor: default;
}
.editor-row:hover {
  border-color: var(--brand);
}
.drag-handle {
  display: inline-flex;
  align-items: center;
  color: var(--text-4);
  cursor: grab;
  flex-shrink: 0;
}
.drag-handle.placeholder {
  width: 14px;
}
.row-icon {
  flex-shrink: 0;
  color: var(--brand);
}
.row-icon.muted {
  color: var(--text-3);
}
.row-label {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-label.muted {
  color: var(--text-3);
}
.row-action {
  flex-shrink: 0;
  color: var(--text-3);
}
.row-action:hover {
  color: var(--brand);
}

/* 拖拽视觉 */
.visible-list :deep(.sortable-ghost) {
  opacity: 0.5;
  background: var(--brand-soft) !important;
  outline: 2px dashed var(--brand);
  outline-offset: -2px;
}
.visible-list :deep(.sortable-chosen) { cursor: grabbing; }
.visible-list :deep(.sortable-chosen .editor-row) { background: var(--hover-bg); }
</style>
