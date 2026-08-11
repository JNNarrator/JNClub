/**
 * markdownIO — 便签 Markdown 导入/导出工具（纯前端）
 * 导出：本地图片(/api/files/...) fetch 转 base64 内嵌；外链 https 原样保留
 * 导入：data URI 图片落地为线上 URL(/api/files/...)；外链与 /api/files 原样保留
 * 零依赖：Blob / FileReader / fetch / a[download]
 */
import axios from 'axios'

/** 提取正文中本地图片 URL（/api/files/...），与后端资产认领同款正则 */
const LOCAL_IMG_RE = /!\[[^\]]*]\(\/api\/files\/([^)\s]+)\)/g

export function extractLocalImages(md: string): string[] {
  const urls: string[] = []
  if (!md) return urls
  const re = new RegExp(LOCAL_IMG_RE.source, 'g')
  let m: RegExpExecArray | null
  while ((m = re.exec(md)) !== null) {
    urls.push('/api/files/' + m[1])
  }
  return urls
}

/** 提取正文中 data URI 图片（data:image/...;base64,...）完整串 */
const DATA_URI_RE = /!\[[^\]]*]\((data:image\/[a-zA-Z0-9+.-]+;base64,[^)\s]+)\)/g

export function extractDataUris(md: string): string[] {
  const uris: string[] = []
  if (!md) return uris
  const re = new RegExp(DATA_URI_RE.source, 'g')
  let m: RegExpExecArray | null
  while ((m = re.exec(md)) !== null) {
    uris.push(m[1])
  }
  return uris
}

/** fetch 图片 → blob → data URI（/api/files/** 公开 + CORS 全开，可直取） */
export async function mdToBase64(url: string): Promise<string> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`图片加载失败(${res.status})`)
  const blob = await res.blob()
  return await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(blob)
  })
}

/** Blob + a[download] 触发下载 */
export function downloadFile(name: string, content: string, mime: string) {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** data URI → File（仅图片；非图片返回 null） */
export function dataUriToFile(uri: string): File | null {
  const m = /^data:(image\/[a-zA-Z0-9+.-]+);base64,(.+)$/.exec(uri)
  if (!m) return null
  const mime = m[1]
  const ext = mime.split('/')[1]?.split('+')[0] || 'png'
  const bin = atob(m[2])
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return new File([bytes], `import-${Date.now()}.${ext}`, { type: mime })
}

/** 上传一张图片 → 返回可插入正文的 /api/files/... URL；失败抛错 */
export async function uploadImage(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await axios.post('/api/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  if (res.data.code === 200 && res.data.data?.url) return res.data.data.url
  throw new Error(res.data.message || '图片上传失败')
}

/** 便签正文导出：本地图嵌 base64，外链/https 原样 */
export async function exportMarkdown(md: string): Promise<string> {
  const images = extractLocalImages(md)
  let out = md
  for (const url of images) {
    try {
      const b64 = await mdToBase64(url)
      out = out.split(url).join(b64)
    } catch {
      /* 保留原 URL，由调用方提示 */
    }
  }
  return out
}
