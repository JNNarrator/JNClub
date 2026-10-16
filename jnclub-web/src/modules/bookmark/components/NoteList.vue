<script setup lang="ts">
/**
 * NoteList.vue — 便签极简列表视图
 * 包装 NoteRow 列表，支持拖拽排序
 */
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useVirtualList } from '@vueuse/core'
import { NSpin } from 'naive-ui'
import NoteRow from './NoteRow.vue'
import type { Note } from '../stores/note'
import { useDraggableSort } from '../../../shared/composables/useDraggableSort'

const props = defineProps<{
  notes: Note[]
  loading?: boolean
  batchMode?: boolean
  selectedIds?: number[]
}>()

const emit = defineEmits<{
  preview: [note: Note]
  edit: [note: Note]
  delete: [note: Note]
  refresh: []
  sort: [orderedIds: number[]]
  'toggle-select': [id: number]
}>()

const listRef = ref<HTMLElement | null>(null)
const isVirtual = computed(() => props.notes.length > 80)
/* itemHeight 必须 ≥ 行的真实占高，否则虚拟模式下行会溢出 wrapper 造成重叠。
   便签行实测：padding 12×2 + 内容 43.6（标题 22.4 + 元信息 19.2）= 67.6，
   加 1px 上下边框 + 8px 行间距 ≈ 77.6，取 80 留余量。 */
const virtual = useVirtualList(computed(() => props.notes), { itemHeight: 80, overscan: 8 })
const virtualItems = computed(() => Array.isArray(virtual.list.value) ? virtual.list.value : [])
const dragDisabled = computed(() => isVirtual.value)
const { init: initSort, destroy: destroySort } = useDraggableSort(listRef, (ids) => {
  emit('sort', ids.map(Number))
}, dragDisabled)
onMounted(() => {
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
  <div class="note-list">
    <NSpin :show="loading">
      <div v-if="isVirtual" v-bind="virtual.containerProps" class="virtual-scroll">
        <!-- wrapperProps 是 ComputedRef，必须显式取 .value（原因见 CollectionList.vue） -->
        <div v-bind="virtual.wrapperProps.value">
          <NoteRow
            v-for="item in virtualItems"
            :key="item.data.id"
            :data-id="item.data.id"
            :note="item.data"
            :batch-mode="batchMode"
            :selected="selectedIds?.includes(item.data.id)"
            class="virtual-item"
            :style="{ '--i': item.index % 20 }"
            @toggle-select="emit('toggle-select', item.data.id)"
            @preview="(n: Note) => emit('preview', n)"
            @edit="(n: Note) => emit('edit', n)"
            @delete="(n: Note) => emit('delete', n)"
            @refresh="emit('refresh')"
          />
        </div>
      </div>
      <div v-else ref="listRef" class="list-inner">
        <NoteRow
          v-for="note in props.notes"
          :key="note.id"
          :data-id="note.id"
          :note="note"
          :batch-mode="batchMode"
          :selected="selectedIds?.includes(note.id)"
          @toggle-select="emit('toggle-select', note.id)"
          @preview="(n: Note) => emit('preview', n)"
          @edit="(n: Note) => emit('edit', n)"
          @delete="(n: Note) => emit('delete', n)"
          @refresh="emit('refresh')"
        />
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.note-list { min-height: 100px; }
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
.list-inner {
  display: flex;
  flex-direction: column;
  /* 与 DiskView/VaultView/RecycleView 的列表间距统一（原 2px） */
  gap: 8px;
}
</style>
