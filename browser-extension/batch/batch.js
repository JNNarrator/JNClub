/**
 * batch/batch.js — 批量收藏打开的标签页
 * chrome.tabs.query 列出全部标签页 → 排除受保护协议 → 勾选 → 选目录 → 批量收藏（去重：已收藏的标记）
 */
import { getState, saveState, api, fetchDirectories, flattenDirs, fetchBookmarks } from '../lib/api.js'

const $ = (id) => document.getElementById(id)
const listEl = $('tabList')

/** 浏览器内置协议页不可注入/不可用 */
const BROWSER_SCHEMES = /^(chrome|chrome-extension|edge|about|devtools|view-source|moz-extension|opera):/

let tabs = []
let savedUrls = new Set()

function setStatus(text, type = '') {
  $('status').textContent = text
  $('status').className = `status ${type}`
}

async function renderLogin() {
  const state = await getState()
  if (state.token) {
    $('loginBar').classList.add('hidden')
    $('toolbar').classList.remove('hidden')
    return true
  }
  $('loginBar').classList.remove('hidden')
  $('toolbar').classList.add('hidden')
  return false
}

async function loadTabs() {
  const all = await chrome.tabs.query({})
  tabs = all
    .filter((t) => t.url && !BROWSER_SCHEMES.test(t.url))
    .map((t) => ({ ...t, checked: false }))
  renderList()
}

/** 已收藏 URL 集合（当前选中目录下），用于去重标记 */
async function loadSavedUrls(directoryId) {
  savedUrls = new Set()
  if (!directoryId) return
  const list = await fetchBookmarks(directoryId)
  for (const b of list || []) {
    if (b.url) savedUrls.add(normalizeUrl(b.url))
  }
}

function normalizeUrl(u) {
  try {
    return new URL(u).href.replace(/\/$/, '')
  } catch {
    return u
  }
}

function renderList() {
  const hideChrome = $('chkHideChrome').checked
  const show = tabs.filter((t) => !hideChrome || !BROWSER_SCHEMES.test(t.url))
  $('tabCount').textContent = `${tabs.length} 个标签页`
  listEl.innerHTML = ''
  if (!show.length) {
    listEl.innerHTML = '<li class="empty jn-muted">没有可收藏的标签页</li>'
    return
  }
  for (const t of show) {
    const li = document.createElement('li')
    li.className = 'tab-item'
    if (savedUrls.has(normalizeUrl(t.url))) li.classList.add('saved')

    li.innerHTML = `
      <input type="checkbox" data-id="${t.id}" ${t.checked ? 'checked' : ''} />
      <div class="tab-main">
        <div class="tab-title">${escapeHtml(t.title || t.url)}</div>
        <div class="tab-url jn-muted">${escapeHtml(t.url)}</div>
      </div>
      <span class="saved-badge">已收藏</span>
    `
    li.querySelector('input').addEventListener('change', (e) => {
      const tab = tabs.find((x) => x.id === t.id)
      if (tab) tab.checked = e.target.checked
    })
    listEl.appendChild(li)
  }
  syncChkAll()
}

function syncChkAll() {
  const visible = tabs.filter((t) => !$('chkHideChrome').checked || !BROWSER_SCHEMES.test(t.url))
  const checked = visible.filter((t) => t.checked).length
  $('chkAll').checked = visible.length > 0 && checked === visible.length
  $('chkAll').indeterminate = checked > 0 && checked < visible.length
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}

async function fillDirs() {
  const res = await api('/api/directories?type=1')
  const select = $('selectDir')
  if (!res.ok) {
    if (res.status === 401) { setStatus('登录已过期', 'err'); return }
    throw res
  }
  const dirs = flattenDirs(res.data || [])
  select.innerHTML = ''
  for (const d of dirs) {
    const opt = document.createElement('option')
    opt.value = String(d.value)
    opt.textContent = d.label
    select.appendChild(opt)
  }
  const st = await getState()
  if (st.lastDirId && dirs.some((d) => d.value === st.lastDirId)) select.value = String(st.lastDirId)
}

async function doSave() {
  const directoryId = Number($('selectDir').value)
  if (!directoryId) { setStatus('请先选择目录', 'err'); return }
  const targets = tabs.filter((t) => t.checked && !savedUrls.has(normalizeUrl(t.url)))
  if (!targets.length) { setStatus('没有需要收藏的标签页（勾选或未收藏的）', 'err'); return }

  setStatus(`正在收藏 ${targets.length} 个标签页…`)
  const res = await chrome.runtime.sendMessage({
    type: 'FAVORITE_MANY',
    data: targets.map((t) => ({ title: t.title || t.url, url: t.url, directoryId })),
  })
  if (res?.status === 401) {
    setStatus('登录已过期，请重新登录', 'err')
    return
  }
  if (res?.ok) {
    const skipped = res.total - res.okCount
    setStatus(`收藏完成：成功 ${res.okCount}${skipped ? `，跳过 ${skipped}（失败/重复）` : ''}${res.errors?.length ? ` · ${res.errors[0]}` : ''}`, 'ok')
    await saveState({ lastDirId: directoryId })
    // 清空已成功项勾选
    tabs.forEach((t) => { if (targets.includes(t)) t.checked = false })
    await loadSavedUrls(directoryId)
    renderList()
  } else {
    setStatus(res?.message || '收藏失败', 'err')
  }
}

$('btnLogin').addEventListener('click', () => {
  chrome.runtime.sendMessage({ type: 'LOGIN' })
})
$('btnSave').addEventListener('click', doSave)
$('chkAll').addEventListener('change', (e) => {
  tabs.forEach((t) => {
    if (!$('chkHideChrome').checked || !BROWSER_SCHEMES.test(t.url)) t.checked = e.target.checked
  })
  renderList()
})
$('chkHideChrome').addEventListener('change', renderList)
$('selectDir').addEventListener('change', async () => {
  await loadSavedUrls(Number($('selectDir').value))
  renderList()
})

;(async () => {
  const logged = await renderLogin()
  if (!logged) return
  try {
    await fillDirs()
    await loadSavedUrls(Number($('selectDir').value) || 0)
    await loadTabs()
  } catch (e) {
    setStatus(e?.message || '加载失败', 'err')
  }
})()
