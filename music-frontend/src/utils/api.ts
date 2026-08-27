/**
 * utils/api.ts — 音乐 API 客户端（匿名设备隔离）
 * 自动持久化 X-Device-Id 并注入请求头；统一返回 { success, data, error }。
 */

const DEVICE_KEY = 'jnmusic.deviceId'

export function getDeviceId(): string {
  if (typeof window === 'undefined') return 'anonymous'
  let id = window.localStorage.getItem(DEVICE_KEY)
  if (!id) {
    id = typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `dev-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    window.localStorage.setItem(DEVICE_KEY, id)
  }
  return id
}

export type ApiResult<T = unknown> = {
  success: boolean
  data?: T
  error?: { code?: string; message?: string }
}

export async function api<T = unknown>(path: string, options: RequestInit = {}): Promise<ApiResult<T>> {
  const headers = new Headers(options.headers)
  headers.set('X-Device-Id', getDeviceId())
  const hasBody = options.body !== undefined && options.body !== null
  if (hasBody && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  try {
    const res = await fetch(`/music${path.startsWith('/') ? path : `/${path}`}`, { ...options, headers })
    const payload = await res.json().catch(() => null)
    if (!payload) {
      return { success: false, error: { message: '响应解析失败' } }
    }
    return {
      success: payload.success === true,
      data: payload.data,
      error: payload.error ? { code: payload.error.code, message: payload.error.message } : undefined,
    }
  } catch {
    return { success: false, error: { message: '网络异常' } }
  }
}