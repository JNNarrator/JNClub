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

/**
 * 批量查询多条记录关联的标签，返回 refId → tags。
 * 列表页使用一次请求替代 N+1；服务端对缺失 refId 不返回 key，前端按空数组处理。
 */
export async function fetchRefTagsBatch(refType: string, refIds: number[]): Promise<Record<number, TagItem[]>> {
  if (!refIds.length) return {}
  const res = await axios.get('/api/tags/relations/batch', {
    params: { refType, refIds: refIds.join(',') },
  })
  if (res.data.code === 200) return res.data.data || {}
  return {}
}

/** 全量覆盖设置某记录标签 */
export async function setRefTags(refType: string, refId: number, tagNames: string[]): Promise<void> {
  const res = await axios.put('/api/tags/relations', { refType, refId, tagNames })
  if (res.data.code !== 200) {
    throw new Error(res.data.message || '保存标签失败')
  }
}
