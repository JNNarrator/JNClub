/**
 * background/service-worker.js — 插件后台
 * 职责：右键菜单收藏 / 保存便签、token 管理（content script 同步）、消息路由、桌面通知
 */
import { api, getState, saveState, serverRoot, DEFAULT_SERVER, createNote } from '../lib/api.js'

const MENU_ID = 'jnclub-save-page'
const MENU_NOTE_ID = 'jnclub-save-note'
const MENU_READ_LATER_ID = 'jnclub-save-read-later'

/**
 * 旧版本迁移：老默认地址为 localhost（本地开发），现默认线上地址。
 * 已安装扩展若还存着旧默认 server，自动迁移到线上，避免点登录仍进本地。
 */
async function migrateDefaultServer() {
  const state = await getState()
  if (state.server && state.server !== DEFAULT_SERVER) {
    const oldDefaults = ['http://localhost:19005', 'https://localhost:19005']
    const normalized = state.server.replace(/\/+$/, '')
    if (oldDefaults.includes(normalized)) {
      await saveState({ server: DEFAULT_SERVER })
    }
  }
}

/** 初始化：注册右键菜单 + 迁移旧默认服务器（onInstalled 覆盖安装/更新） */
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: MENU_ID,
    title: '收藏到 JNClub',
    contexts: ['page', 'link'],
  })
  chrome.contextMenus.create({
    id: MENU_NOTE_ID,
    title: '保存为 JNClub 便签',
    contexts: ['page'],
  })
  chrome.contextMenus.create({
    id: MENU_READ_LATER_ID,
    title: '收藏为稍后读',
    contexts: ['page', 'link'],
  })
  migrateDefaultServer()
})

// 顶层启动兜底迁移（幂等，仅当 server 仍为旧默认时改写）
migrateDefaultServer()

/** 右键菜单收藏：pageUrl 或 linkUrl，标题取当前标签页标题 */
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (info.menuItemId === MENU_NOTE_ID) {
    savePageAsNote(tab)
    return
  }
  if (info.menuItemId === MENU_READ_LATER_ID) {
    savePageAsReadLater(info, tab)
    return
  }
  if (info.menuItemId !== MENU_ID) return
  const url = info.linkUrl || info.pageUrl
  const title = info.selectionText ? info.selectionText.slice(0, 100) : tab?.title || url
  const state = await getState()
  if (!state.token) {
    notify('未登录', '请先打开 JNClub 收藏助手完成登录')
    return
  }
  const dirId = state.defaultDirId
  if (!dirId) {
    notify('未设置默认目录', '请在插件弹窗中先收藏一次或选择默认目录')
    return
  }
  const res = await createBookmarkSafe({ title, url, directoryId: dirId })
  if (res.ok) {
    notify('已收藏到 JNClub', `${title} → ${dirName(dirId)}`)
  } else if (res.status === 401) {
    notify('登录已过期', '请打开 JNClub 收藏助手重新登录')
  } else {
    notify('收藏失败', res.message)
  }
})

/** 右键菜单：收藏为稍后读（默认收藏目录，readLater=1） */
async function savePageAsReadLater(info, tab) {
  const url = info.linkUrl || info.pageUrl
  const title = info.selectionText ? info.selectionText.slice(0, 100) : tab?.title || url
  const state = await getState()
  if (!state.token) {
    notify('未登录', '请先打开 JNClub 收藏助手完成登录')
    return
  }
  const dirId = state.defaultDirId
  if (!dirId) {
    notify('未设置默认目录', '请在插件弹窗中先收藏一次或选择默认目录')
    return
  }
  const res = await createBookmarkSafe({ title, url, directoryId: dirId, readLater: 1 })
  if (res.ok) {
    notify('已加入稍后读', `${title} → ${dirName(dirId)}`)
  } else if (res.status === 401) {
    notify('登录已过期', '请打开 JNClub 收藏助手重新登录')
  } else {
    notify('收藏失败', res.message)
  }
}

/** 右键菜单：把当前页转为 Markdown 便签（默认便签目录，无则取第一个并记忆） */
async function savePageAsNote(tab) {
  const state = await getState()
  if (!state.token) {
    notify('未登录', '请先打开 JNClub 收藏助手完成登录')
    return
  }
  let md
  try {
    md = await chrome.tabs.sendMessage(tab.id, { type: 'EXTRACT_MARKDOWN' })
  } catch {
    notify('无法转换', '该页面不支持转换（可能为浏览器内置页面）')
    return
  }
  if (!md?.ok) {
    notify('无法转换', md?.message || '该页面不支持转换')
    return
  }
  const dirId = state.noteDefaultDirId
  if (!dirId) {
    const dirs = await fetchNoteDirs()
    const first = dirs[0]
    if (first) {
      await saveState({ noteDefaultDirId: first.id })
      return createNoteWithDir(tab, md, first.id)
    }
    notify('无便签目录', '请先在 JNClub 创建便签目录')
    return
  }
  return createNoteWithDir(tab, md, dirId)
}

async function createNoteWithDir(tab, md, directoryId) {
  const res = await createNote({ title: md.title || '', content: md.markdown, directoryId })
  if (res.ok) {
    notify('已保存为便签', `${md.title || '无标题'} → 便签 #${directoryId}`)
  } else if (res.status === 401) {
    notify('登录已过期', '请打开 JNClub 收藏助手重新登录')
  } else {
    notify('保存失败', res.message)
  }
}

async function fetchNoteDirs() {
  const res = await api('/api/directories?type=2')
  return res.ok ? res.data || [] : []
}

/** 右键菜单点击时也刷新菜单可见性（未登录也允许点，弹提示） */

/** 桌面通知 */
function notify(title, message) {
  chrome.notifications.create({
    type: 'basic',
    iconUrl: chrome.runtime.getURL('assets/icon128.png'),
    title,
    message,
  })
}

/** 收藏（带默认目录兜底：无默认目录时用第一个目录） */
async function createBookmarkSafe({ title, url, directoryId, readLater = 0 }) {
  if (!directoryId) {
    const dirs = await fetchDirs()
    const first = dirs[0]
    if (first) {
      await saveState({ defaultDirId: first.id })
      return createBookmarkWithDir({ title, url, directoryId: first.id, readLater })
    }
    return { ok: false, message: '收藏夹还没有目录，请先在 JNClub 创建' }
  }
  return createBookmarkWithDir({ title, url, directoryId, readLater })
}

async function createBookmarkWithDir({ title, url, directoryId, readLater = 0 }) {
  const res = await api('/api/bookmarks', {
    method: 'POST',
    body: { title, url, directoryId, ...(readLater ? { readLater: 1 } : {}) },
  })
  if (!res.ok && res.code === 401) {
    return { ok: false, status: 401, message: res.message }
  }
  return res
}

async function fetchDirs() {
  const res = await api('/api/directories?type=1')
  return res.ok ? res.data || [] : []
}

/** 目录名映射（通知用） */
function dirName(id) {
  return `#${id}`
}

/* ==================== 消息路由 ==================== */

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  switch (msg?.type) {
    case 'TOKEN_SYNC':
      // content script 从 JNClub 页面 localStorage 读到 token
      saveState({ token: msg.token })
        .then(() => sendResponse({ ok: true }))
        .catch(() => sendResponse({ ok: false }))
      return true

    case 'GET_STATE':
      getState().then(sendResponse)
      return true

    case 'LOGIN':
      // 打开 JNClub 登录页（走既有 SSO）
      getState().then(async (s) => {
        const url = `${serverRoot(s.server)}/jnclub/sso/login`
        await chrome.tabs.create({ url })
        sendResponse({ ok: true, url })
      })
      return true

    case 'LOGOUT':
      saveState({ token: '' })
        .then(() => sendResponse({ ok: true }))
        .catch(() => sendResponse({ ok: false }))
      return true

    case 'FAVORITE':
      // popup/batch 创建收藏
      createBookmarkSafe(msg.data)
        .then(sendResponse)
        .catch((e) => sendResponse({ ok: false, message: String(e) }))
      return true

    case 'CREATE_NOTE':
      // popup 网页转便签：直接创建（目录由弹窗选择）
      createNote(msg.data)
        .then(sendResponse)
        .catch((e) => sendResponse({ ok: false, message: String(e) }))
      return true

    case 'FAVORITE_MANY':
      // batch 批量收藏：逐条创建，返回成功/失败统计
      (async () => {
        const list = msg.data || []
        let okCount = 0
        const errors = []
        for (const item of list) {
          const r = await createBookmarkSafe(item)
          if (r.ok) okCount++
          else if (r.status === 401) {
            return sendResponse({ ok: false, status: 401, okCount, errors: [r.message] })
          } else errors.push(`${item.title}: ${r.message}`)
        }
        sendResponse({ ok: true, okCount, total: list.length, errors })
      })()
      return true

    case 'NOTIFY':
      notify(msg.title, msg.message)
      sendResponse({ ok: true })
      return true
  }
  return false
})
