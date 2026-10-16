<script setup lang="ts">
/**
 * CollectionList.vue — 极简列表视图
 * 包裹多行 CollectionRow，stagger 渐入
 */
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useVirtualList } from '@vueuse/core'
import { NSpin } from 'naive-ui'
import CollectionRow from './CollectionRow.vue'
import type { BookmarkItem } from './CollectionRow.vue'
import { useDraggableSort } from '../../../shared/composables/useDraggableSort'
import { useItemDragContext } from '../composables/useItemDragContext'
import { useBreakpoint } from '../../../shared/composables/useBreakpoint'

const props = defineProps<{
  bookmarks: BookmarkItem[]
  loading?: boolean
  batchMode?: boolean
  selectedIds?: number[]
}>()

const emit = defineEmits<{
  refresh: []
  edit: [bookmark: BookmarkItem]
  read: [bookmark: BookmarkItem]
  sort: [orderedIds: number[]]
  'toggle-select': [id: number]
}>()

/* Stagger 渐入：每项延迟递增 */
const visible = ref(false)
const listRef = ref<HTMLElement | null>(null)
const { setDragging } = useItemDragContext()
const { isCoarse } = useBreakpoint()

const handleDragStart = (e: DragEvent, item: BookmarkItem) => {
  setDragging({
    itemId: item.id,
    module: 'bookmarks',
    currentDirectoryId: item.directoryId ?? null,
  })
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    try { e.dataTransfer.setData('text/plain', String(item.id)) } catch { /* 忽略 */ }
  }
}

const handleDragEnd = () => setDragging(null)

/* 大列表启用虚拟滚动；虚拟模式下拖拽排序不可用（避免只排序已渲染片段） */
const isVirtual = computed(() => props.bookmarks.length > 80)
/* itemHeight 必须 ≥ 行的真实占高，否则虚拟模式下行会溢出 wrapper 造成重叠。
   收藏行实测：padding 12×2 + 内容 24.9 = 48.9，加 1px 上下边框 + 8px 行间距 ≈ 58.9，
   64 有余量（标签换行时会更高，与本次改动前一致）。 */
const virtual = useVirtualList(computed(() => props.bookmarks), { itemHeight: 64, overscan: 8 })
const virtualItems = computed(() => Array.isArray(virtual.list.value) ? virtual.list.value : [])
const dragDisabled = computed(() => isVirtual.value)
const { init: initSort, destroy: destroySort } = useDraggableSort(listRef, (ids) => {
  emit('sort', ids.map(Number))
}, dragDisabled)

onMounted(() => {
  requestAnimationFrame(() => {
    visible.value = true
  })
  if (!isVirtual.value) initSort()
})

watch(isVirtual, async (v) => {
  if (v) {
    destroySort()
  } else {
    await nextTick()
    initSort()
  }
})
</script>

<template>
  <div class="collection-list">
    <NSpin :show="loading">
      <div v-if="isVirtual" v-bind="virtual.containerProps" class="virtual-scroll">
        <!-- wrapperProps 是 ComputedRef，必须显式取 .value：virtual 是普通对象，
             Vue 的模板 ref 解包只作用于顶层，嵌套属性的 ref 不会解包。少了 .value
             会把 ComputedRef 自身当 props 展开（__v_isref/fn/effect 变成 DOM 属性），
             style 从未落到 DOM —— wrapper 既无占位高度也无滚动偏移，虚拟滚动实际失效。 -->
        <div v-bind="virtual.wrapperProps.value">
          <div
            v-for="item in virtualItems"
            :key="item.data.id"
            :data-id="item.data.id"
            class="list-item-wrap virtual-item"
            :style="{ '--i': item.index % 20 }"
          >
            <CollectionRow :bookmark="item.data" @refresh="emit('refresh')" @edit="emit('edit', $event)" @read="emit('read', $event)" :batch-mode="batchMode" :selected="selectedIds?.includes(item.data.id)" @toggle-select="emit('toggle-select', item.data.id)" />
          </div>
        </div>
      </div>
      <div v-else ref="listRef" :class="['list-items', { visible }]">
        <div
          v-for="(bk, i) in props.bookmarks"
          :key="bk.id"
          :data-id="bk.id"
          class="list-item-wrap"
          :style="{ '--i': i }"
          :draggable="!isCoarse"
          @dragstart="handleDragStart($event, bk)"
          @dragend="handleDragEnd"
        >
          <CollectionRow :bookmark="bk" @refresh="emit('refresh')" @edit="emit('edit', $event)" @read="emit('read', $event)"  :batch-mode="batchMode" :selected="selectedIds?.includes(bk.id)" @toggle-select="emit('toggle-select', bk.id)" />
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.collection-list {
  min-height: 100px;
}

.virtual-scroll {
  /* 高度不再自己按视口猜（原 calc(100vh - 240px) 与实际 chrome 失配，会同时激活
     外层滚动条形成双滚动条），改为填满父级 flex 容器给的高度。
     父级链路见 Home.vue 的 .spin-area 注释；本组件的根元素也必须是 flex 容器
     （Home.vue 已用 :deep() 设好），否则 flex:1 不生效、列表会撑开而失去虚拟滚动。 */
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}
.virtual-item {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
  /* 虚拟模式的间距必须做进 item 自身（wrapper 无 gap），
     且该值已计入下方 useVirtualList 的 itemHeight —— 改这里要同步改 itemHeight */
  margin-bottom: 8px;
}

.list-items {
  display: flex;
  flex-direction: column;
  /* 与 DiskView/VaultView/RecycleView 的列表间距统一（原 2px） */
  gap: 8px;
}

/* Stagger 渐入（全局 jnclub-cardIn，统一节奏） */
.list-item-wrap {
  opacity: 0;
  transform: translateY(10px);
  animation: jnclub-cardIn var(--dur-slow) var(--ease) forwards;
  animation-delay: calc(var(--i, 0) * var(--dur-stagger));
}

.list-items.visible .list-item-wrap {
  animation-name: jnclub-cardIn;
}

/* 拖拽排序时抑制 stagger 渐入动画重播 */
.list-items.sorting .list-item-wrap {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
}
</style>
