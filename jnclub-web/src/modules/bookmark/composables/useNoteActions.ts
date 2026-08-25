/**
 * useNoteActions.ts — 便签模块的通用动作
 * 新开标签页编辑/预览、导出当前目录 Markdown、删除。
 */
import { h } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { useMessage, useDialog } from 'naive-ui'
import axios from 'axios'
import { useNoteStore } from '../stores/note'
import type { Note } from '../stores/note'
import { exportMarkdown, downloadFile } from './markdownIO'

export function useNoteActions(opts: {
  selectedDirectoryId: () => number | null
  loadData: () => Promise<void>
}) {
  const router = useRouter()
  const message = useMessage()
  const dialog = useDialog()
  const noteStore = useNoteStore()

  const openNoteInNewTab = (location: RouteLocationRaw) => {
    window.open(router.resolve(location).href, '_blank')
  }

  const handleCreateNote = () => {
    if (!opts.selectedDirectoryId()) {
      message.warning('请先选择一个目录')
      return
    }
    openNoteInNewTab({
      name: 'note-create',
      query: { directoryId: String(opts.selectedDirectoryId()) },
    })
  }

  const handleExportAllNotes = async () => {
    const notes = noteStore.notes
    if (!notes.length) { message.warning('当前目录没有便签'); return }
    const loadingMsg = message.loading(`正在导出 ${notes.length} 篇便签…`, { duration: 0 })
    let ok = 0
    for (const n of notes) {
      try {
        // 列表已瘦身不返回正文；导出属于低频操作，按需拉详情后仍按单篇 .md 下载
        let content = n.content
        if (!content) {
          await noteStore.fetchNoteDetail(n.id)
          content = noteStore.currentNote?.content || ''
        }
        const md = await exportMarkdown(content)
        downloadFile(`${(n.title || '未命名').replace(/[\\/:*?"<>|]/g, '_')}.md`, md, 'text/markdown')
        ok++
      } catch {
        /* 单篇失败跳过，继续导出其余 */
      }
    }
    loadingMsg.destroy()
    message.success(ok === notes.length ? `已导出 ${ok} 篇便签` : `已导出 ${ok}/${notes.length} 篇（部分失败）`)
  }

  const handleEditNote = (note: Note) => {
    openNoteInNewTab({ name: 'note-view', params: { id: note.id } })
  }

  const handlePreviewNote = (note: Note) => {
    openNoteInNewTab({ name: 'note-view', params: { id: note.id } })
  }

  const handleDeleteNote = async (note: Note) => {
    dialog.warning({
      title: '确认删除',
      content: `确定要删除便签"${note.title}"吗？`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        const id = note.id
        try {
          await axios.delete(`/api/notes/${id}`)
          message.success('', {
            duration: 6000,
            render: () => h('div', { style: 'display:flex;align-items:center;gap:12px;' }, [
              h('span', '已移入回收站'),
              h('a', {
                style: 'cursor:pointer;color:var(--brand);font-weight:600;',
                onClick: async () => {
                  try {
                    await axios.post('/api/recycle/restore', { type: 'note', id })
                    message.success('已恢复')
                    opts.loadData()
                  } catch {
                    message.error('恢复失败')
                  }
                },
              }, '撤销'),
            ]),
          })
          opts.loadData()
        } catch (e: any) {
          message.error(e.response?.data?.message || '删除失败')
        }
      },
    })
  }

  return {
    handleCreateNote,
    handleExportAllNotes,
    handleEditNote,
    handlePreviewNote,
    handleDeleteNote,
  }
}
