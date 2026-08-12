/**
 * lib/api.js — JNClub API 客户端
 * 服务端地址 + token 存 chrome.storage.local；MV3 扩展对 host_permissions 声明域名的请求绕过网页 CORS。
 * 401 时回调 onUnauthorized（popup/background 各自处理提示重登）。
 */
export const STORAGE_KEY = 'jnclub-ext-state'

/** 默认服务器（线上生产；本地开发可在设置页改为 http://localhost:19005） */
export const DEFAULT_SERVER = 'https://jiangnan.88933.vip'

export async function getState() {
  const s = await chrome.storage.local.get(STORAGE_KEY)
  return (
    s[STORAGE_KEY] || {
      server: DEFAULT_SERVER,
      token: '',
      lastDirId: null,
      defaultDirId: null,
    }
  )
}

export async function saveState(patch) {
  const state = await getState()
  const next = { ...state, ...patch }
  await chrome.storage.local.set({ [STORAGE_KEY]: next })
  return next
}

/** 服务器根地址，统一去尾斜杠 */
export function serverRoot(server) {
  return (server || DEFAULT_SERVER).replace(/\/+$/, '')
}

/**
 * 请求 JNClub API
 * @param {string} path 以 / 开头的接口路径（如 /api/bookmarks）
 * @param {object} options { method, body, dirName? }
 * @returns {Promise<{ok, code, data, message}>}
 */
export async function api(path, { method = 'GET', body = null, server } = {}) {
  const state = await getState()
  const base = serverRoot(server || state.server)
  const url = `${base}${path}`
  const headers = {}
  if (state.token) headers['jn-token'] = state.token
  if (body !== null) headers['Content-Type'] = 'application/json'

  let res
  try {
    res = await fetch(url, {
      method,
      headers,
      body: body !== null ? JSON.stringify(body) : undefined,
    })
  } catch (e) {
    return { ok: false, code: -1, message: `无法连接服务器 ${base}`, error: e }
  }

  let payload = null
  try {
    payload = await res.json()
  } catch {
    /* 非 JSON 响应 */
  }

  if (res.status === 401) {
    return { ok: false, code: 401, message: '未登录或会话已过期', status: 401 }
  }
  if (!res.ok) {
    return {
      ok: false,
      code: res.status,
      message: payload?.message || payload?.error || `请求失败 (${res.status})`,
      status: res.status,
    }
  }
  // JNClub 统一返回 { code, data, message }，code 200 为成功
  if (payload && typeof payload.code === 'number') {
    return { ok: payload.code === 200, code: payload.code, data: payload.data, message: payload.message }
  }
  return { ok: true, data: payload }
}

/** 目录树（type 参数化：1=收藏夹 2=便签），返回树形数组 */
export async function fetchDirectories(type = 1) {
  const res = await api(`/api/directories?type=${type}`)
  return res.ok ? res.data || [] : []
}

/** 便签目录树（type=2） */
export async function fetchNoteDirs() {
  return fetchDirectories(2)
}

/** 展平目录树 → [{ label, value }] 供下拉 */
export function flattenDirs(dirs, depth = 0, out = []) {
  for (const d of dirs || []) {
    out.push({ label: `${'　'.repeat(depth)}${d.name}`, value: d.id })
    if (d.children?.length) flattenDirs(d.children, depth + 1, out)
  }
  return out
}

/** 创建收藏 */
export async function createBookmark({ title, url, directoryId }) {
  return api('/api/bookmarks', {
    method: 'POST',
    body: { title, url, directoryId },
  })
}

/** 查询某目录全部收藏（批量去重用） */
export async function fetchBookmarks(directoryId) {
  const res = await api(`/api/bookmarks?directoryId=${directoryId}`)
  return res.ok ? res.data || [] : []
}

/** 新建便签（userId 由后端从 token 取） */
export async function createNote({ title, content, directoryId }) {
  return api('/api/notes', {
    method: 'POST',
    body: { title, content, directoryId },
  })
}
