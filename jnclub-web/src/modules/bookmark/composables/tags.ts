import axios from 'axios'

export interface TagItem {
  id: number
  name: string
  count?: number
}

/** 我的标签列表（refType: bookmark|note） */
export async function fetchTags(refType?: string): Promise<TagItem[]> {
  const res = await axios.get('/api/tags', { params: refType ? { refType } : {} })
  if (res.data.code === 200) return res.data.data || []
  return []
}

/** 某记录关联的标签 */
export async function fetchRefTags(refType: string, refId: number): Promise<TagItem[]> {
  const res = await axios.get('/api/tags/relations', { params: { refType, refId } })
  if (res.data.code === 200) return res.data.data || []
  return []
}

/** 全量覆盖设置某记录标签 */
export async function setRefTags(refType: string, refId: number, tagNames: string[]): Promise<void> {
  const res = await axios.put('/api/tags/relations', { refType, refId, tagNames })
  if (res.data.code !== 200) {
    throw new Error(res.data.message || '保存标签失败')
  }
}
