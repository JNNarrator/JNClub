/**
 * background/service-worker.js — 插件后台
 * 职责：右键菜单收藏、token 管理（content script 同步）、消息路由、桌面通知
 */
import { api, getState, saveState, serverRoot } from '../lib/api.js'

const MENU_ID = 'jnclub-save-page'

/** 初始化：注册右键菜单 */
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: MENU_ID,
    title: '收藏到 JNClub',
    contexts: ['page', 'link'],
  })
})

/** 右键菜单收藏：pageUrl 或 linkUrl，标题取当前标签页标题 */
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
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
async function createBookmarkSafe({ title, url, directoryId }) {
  if (!directoryId) {
    const dirs = await fetchDirs()
    const first = dirs[0]
    if (first) {
      await saveState({ defaultDirId: first.id })
      return createBookmarkWithDir({ title, url, directoryId: first.id })
    }
    return { ok: false, message: '收藏夹还没有目录，请先在 JNClub 创建' }
  }
  return createBookmarkWithDir({ title, url, directoryId })
}

async function createBookmarkWithDir({ title, url, directoryId }) {
  const res = await api('/api/bookmarks', {
    method: 'POST',
    body: { title, url, directoryId },
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
