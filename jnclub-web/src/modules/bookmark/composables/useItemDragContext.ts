import { ref } from 'vue'

/**
 * useItemDragContext — item 拖拽到目录树的跨容器上下文（模块级单例）
 * item 卡片/行启用 HTML5 draggable，dragstart 时写入被拖 item 信息；
 * FolderTree 的 drop 落点读取该上下文，识别来源模块并调对应 move 接口。
 * 用模块级上下文而非 dataTransfer，避免跨容器/浏览器对 dataTransfer 类型的限制。
 */
export interface ItemDragPayload {
  /** item 业务 id */
  itemId: number
  /** 模块 key：bookmarks / notes / files / vault */
  module: string
  /** item 当前所在目录 id（落点为目标目录时前端拦截"已在该目录"） */
  currentDirectoryId: number | null
}

const dragging = ref<ItemDragPayload | null>(null)

export function useItemDragContext() {
  const setDragging = (p: ItemDragPayload | null) => {
    dragging.value = p
  }

  return {
    dragging,
    setDragging,
  }
}
