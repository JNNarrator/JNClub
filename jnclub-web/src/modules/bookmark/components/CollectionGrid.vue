<script setup lang="ts">
/**
 * CollectionGrid.vue — 卡片网格视图
 * grid-template-columns: repeat(auto-fill, minmax(220px,1fr)) + gap
 * 单卡不孤悬，stagger 渐入
 */
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useVirtualList, useElementSize } from '@vueuse/core'
import { NSpin } from 'naive-ui'
import CollectionCard from './CollectionCard.vue'
import type { BookmarkItem } from './CollectionRow.vue'
import { useDraggableSort } from '../../../shared/composables/useDraggableSort'
import { useItemDragContext } from '../composables/useItemDragContext'
import { useBreakpoint } from '../../../shared/composables/useBreakpoint'
import { useUiDensity } from '../../../shared/composables/useUiDensity'

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

const visible = ref(false)
const gridRef = ref<HTMLElement | null>(null)
const { setDragging } = useItemDragContext()
const { isCoarse } = useBreakpoint()
const { density } = useUiDensity()

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

/* 大列表网格虚拟滚动：按容器宽度把卡片分组为行，只渲染可视行 */
const rootRef = ref<HTMLElement | null>(null)
const { width: gridWidth } = useElementSize(rootRef)
const columns = computed(() => {
  const w = gridWidth.value
  if (!w) return 3
  return Math.max(1, Math.floor(w / 220))
})
const rows = computed(() => {
  const cols = columns.value
  const result: BookmarkItem[][] = []
  for (let i = 0; i < props.bookmarks.length; i += cols) {
    result.push(props.bookmarks.slice(i, i + cols))
  }
  return result
})
const isVirtual = computed(() => props.bookmarks.length > 80)

/**
 * 虚拟滚动的行高（技术债①）。
 * 卡片高度原先不是常量 —— 唯一的高度变量是 `.card-tags` 的换行行数：
 * 实测 0/1/2 行标签 = 132.58 / 156.58 / 176.58px（舒适档），
 * 紧凑档 = 120.58 / 142.58 / 162.58 / 182.58px。
 * 原先写死 `itemHeight: 220`，比最矮行（132.58 + 16 间距 = 148.58）虚高 48%：
 * wrapper 占位因此过高（120 条 → scrollHeight 8800px，真实内容约 5943px），
 * 滚到底出现大段空白，且 offsetTop = start × 220 与真实累计高度持续错位，
 * 实测滚动中首个已渲染行的视口 top 在 -159 与 +21 之间跳变（180px 跳动）。
 *
 * 修复分两步（即报告 11.1 预判的「先给卡片加上界」）：
 * ① CollectionCard 的标签区固定为单行（`.card-tags` 不再换行、始终占位），
 *    卡片高度因此成为常量：舒适档 156.58px、紧凑档 142.58px（实测）；
 * ② itemHeight 由密度映射到该常量 + 行间距 16px。
 *
 * 不用「渲染后实测每行高度再回写」：实测会让 totalHeight 在滚动过程中
 * 持续收缩（4748 → 3882px），浏览器随之钳制 scrollTop，反而制造出比固定值
 * 更剧烈的跳动。精度必须来自确定的布局，而不是运行时测量。
 */
const ROW_GAP = 16
/** 卡片定高：1 行标签的实测值（两种密度各一档） */
const CARD_HEIGHT = computed(() => (density.value === 'compact' ? 142.58 : 156.58))
const itemHeight = () => CARD_HEIGHT.value + ROW_GAP

const virtual = useVirtualList(rows, { itemHeight, overscan: 2 })
const virtualRows = computed(() => Array.isArray(virtual.list.value) ? virtual.list.value : [])
const dragDisabled = computed(() => isVirtual.value)
const { init: initSort, destroy: destroySort } = useDraggableSort(gridRef, (ids) => {
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
  <div ref="rootRef" class="collection-grid">
    <NSpin :show="loading">
      <div v-if="isVirtual" v-bind="virtual.containerProps" class="virtual-scroll">
        <!-- wrapperProps 是 ComputedRef，必须显式取 .value（原因见 CollectionList.vue） -->
        <div v-bind="virtual.wrapperProps.value">
          <div
            v-for="row in virtualRows"
            :key="row.index"
            class="virtual-row"
            :style="{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }"
          >
            <div
              v-for="bk in row.data"
              :key="bk.id"
              :data-id="bk.id"
              class="grid-item-wrap virtual-item"
              :draggable="!isCoarse"
              @dragstart="handleDragStart($event, bk)"
              @dragend="handleDragEnd"
            >
              <CollectionCard :bookmark="bk" @refresh="emit('refresh')" @edit="emit('edit', $event)" @read="emit('read', $event)" :batch-mode="batchMode" :selected="selectedIds?.includes(bk.id)" @toggle-select="emit('toggle-select', bk.id)" />
            </div>
          </div>
        </div>
      </div>
      <div v-else ref="gridRef" :class="['grid-cards', { visible }]">
        <div
          v-for="(bk, i) in props.bookmarks"
          :key="bk.id"
          :data-id="bk.id"
          class="grid-item-wrap"
          :style="{ '--i': i }"
          :draggable="!isCoarse"
          @dragstart="handleDragStart($event, bk)"
          @dragend="handleDragEnd"
        >
          <CollectionCard :bookmark="bk" @refresh="emit('refresh')" @edit="emit('edit', $event)" @read="emit('read', $event)"  :batch-mode="batchMode" :selected="selectedIds?.includes(bk.id)" @toggle-select="emit('toggle-select', bk.id)" />
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.collection-grid {
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
.virtual-row {
  display: grid;
  gap: 16px;
  margin-bottom: 16px;
}
.virtual-item {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
}

.grid-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

/* Stagger 渐入（全局 jnclub-cardIn，统一节奏） */
.grid-item-wrap {
  opacity: 0;
  transform: translateY(10px);
  animation: jnclub-cardIn var(--dur-slow) var(--ease) forwards;
  animation-delay: calc(var(--i, 0) * var(--dur-stagger));
}

.grid-cards.visible .grid-item-wrap {
  animation-name: jnclub-cardIn;
}

/* 拖拽排序时抑制 stagger 渐入动画重播 */
.grid-cards.sorting .grid-item-wrap {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
}
</style>
