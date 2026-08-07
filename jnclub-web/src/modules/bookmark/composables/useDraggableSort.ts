/**
 * useDraggableSort — 基于 SortableJS 的可复用拖拽排序
 * 供收藏夹/便签的卡片网格与极简列表复用，统一拖拽交互与排序回调
 */
import { onBeforeUnmount, type Ref } from 'vue'
import Sortable from 'sortablejs'

/**
 * 初始化容器拖拽排序
 * @param containerRef 拖拽容器（`.grid-cards` 或 `.list-inner`）
 * @param onSort 拖拽结束回调：入参为按新 DOM 顺序排列的 id 数组。
 *               父组件在此更新 store 数据顺序并调用后端排序接口。
 *               返回 true 表示顺序发生变化、需提交；false 表示无变化跳过。
 * @param disabledRef 是否禁用拖拽（如加载中）
 */
export function useDraggableSort(
  containerRef: Ref<HTMLElement | null>,
  onSort: (orderedIds: number[]) => Promise<void> | void | boolean,
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
        // 按容器当前 DOM 子节点顺序取 id（v-for 为每项设 data-id）
        const orderedIds = Array.from(containerRef.value?.children || [])
          .map((el) => Number((el as HTMLElement).dataset.id))
          .filter((id) => !Number.isNaN(id))
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
