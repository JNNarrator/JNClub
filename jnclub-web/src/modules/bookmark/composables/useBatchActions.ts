/**
 * useBatchActions.ts — 收藏/便签批量选择与批量删除
 * 批量移动 / 批量打标签弹窗由 BatchActionModals.vue 负责，本 composable 只管理：
 * - 多选模式开关与选中集合
 * - 全选当前列表
 * - 批量删除（二次确认）
 * - 完成后统一刷新
 */
import { ref, computed, watch } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import axios from 'axios'

export type BatchModule = 'bookmarks' | 'notes' | 'files' | 'vault'

export function useBatchActions(opts: {
  activeModule: () => BatchModule
  currentList: () => Array<{ id: number }>
  loadData: () => Promise<void>
  loadTags: () => Promise<void>
}) {
  const batchMode = ref(false)
  const selectedIds = ref<number[]>([])
  const message = useMessage()
  const dialog = useDialog()

  const allSelected = computed(() =>
    opts.currentList().length > 0 && selectedIds.value.length === opts.currentList().length
  )

  const toggleBatchMode = (on: boolean) => {
    batchMode.value = on
    if (!on) selectedIds.value = []
  }

  const toggleSelect = (id: number) => {
    const i = selectedIds.value.indexOf(id)
    if (i >= 0) selectedIds.value.splice(i, 1)
    else selectedIds.value.push(id)
  }

  const toggleAll = () => {
    if (allSelected.value) selectedIds.value = []
    else selectedIds.value = opts.currentList().map(i => i.id)
  }

  const finishBatch = () => {
    toggleBatchMode(false)
    opts.loadData()
    opts.loadTags()
  }

  const handleBatchDelete = () => {
    if (!selectedIds.value.length) return
    const module = opts.activeModule()
    if (module !== 'bookmarks' && module !== 'notes') return

    dialog.warning({
      title: '批量删除',
      content: `确定删除选中的 ${selectedIds.value.length} 项吗？删除后进入回收站，可在回收站恢复。`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        const url = module === 'notes' ? '/api/notes/batch' : '/api/bookmarks/batch'
        try {
          await axios.delete(url, { data: { ids: selectedIds.value } })
          message.success(`已删除 ${selectedIds.value.length} 项`)
          finishBatch()
        } catch (e: any) {
          message.error(e.response?.data?.message || '批量删除失败')
        }
      },
    })
  }

  // 切模块时退出多选
  watch(() => opts.activeModule(), () => toggleBatchMode(false))

  return {
    batchMode,
    selectedIds,
    allSelected,
    toggleBatchMode,
    toggleSelect,
    toggleAll,
    finishBatch,
    handleBatchDelete,
  }
}
