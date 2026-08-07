/**
 * useDraggableSort — 基于 SortableJS 的可复用拖拽排序
 * 供收藏夹/便签/云盘的网格与列表，以及侧栏导航复用，统一拖拽交互与排序回调
 */
import { onBeforeUnmount, type Ref } from 'vue'
import Sortable from 'sortablejs'

/** data-id 支持数字 id 或字符串 key（如导航模块 key） */
export type SortableId = number | string

/**
 * 初始化容器拖拽排序
 * @param containerRef 拖拽容器（`.grid-cards` / `.list-inner` / `.file-list` / `.nav-list`）
 * @param onSort 拖拽结束回调：入参为按新 DOM 顺序排列的 id 数组（来自 data-id）。
 *               父组件在此更新数据顺序并调用后端排序接口。
 * @param disabledRef 是否禁用拖拽（如加载中）
 */
export function useDraggableSort(
  containerRef: Ref<HTMLElement | null>,
  onSort: (orderedIds: SortableId[]) => Promise<void> | void | boolean,
  disabledRef?: Ref<boolean>,
) {
  let sortable: Sortable | null = null

  const init = () => {
    if (!containerRef.value || sortable) return
    sortable = Sortable.create(containerRef.value, {
      animation: 180,
      ghostClass: 'sortable-ghost',
      chosenClass: 'sortable-chosen',
      dragClass: 'sortable-drag',
      onStart() {
        containerRef.value?.classList.add('sorting')
      },
      onEnd(evt) {
        containerRef.value?.classList.remove('sorting')
        if (evt.oldIndex === undefined || evt.newIndex === undefined) return
        // 顺序未变则跳过
        if (evt.oldIndex === evt.newIndex) return
        // 按容器当前 DOM 子节点顺序取 id（v-for 为每项设 data-id，保留原始类型）
        const orderedIds = Array.from(containerRef.value?.children || [])
          .map((el) => {
            const raw = (el as HTMLElement).dataset.id
            if (raw === undefined) return null
            // 纯数字字符串 → number，其余保留字符串（如导航 key）
            return /^\d+$/.test(raw) ? Number(raw) : raw
          })
          .filter((id): id is SortableId => id !== null)
        if (orderedIds.length === 0) return
        onSort(orderedIds)
      },
      disabled: disabledRef?.value ?? false,
    })
  }

  const destroy = () => {
    sortable?.destroy()
    sortable = null
  }

  onBeforeUnmount(destroy)

  return { init, destroy }
}
