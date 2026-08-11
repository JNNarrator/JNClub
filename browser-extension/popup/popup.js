/**
 * popup/popup.js — 弹窗：当前页一键收藏 + 登录态管理 + 入口
 */
import { getState, saveState, serverRoot, api, fetchDirectories, flattenDirs } from '../lib/api.js'

const $ = (id) => document.getElementById(id)

const loginView = $('loginView')
const saveView = $('saveView')
const statusEl = $('saveStatus')

/** 拉取当前激活标签页信息 */
async function getActiveTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  return tab || {}
}

/** 填充目录下拉（记忆上次选择 / 默认目录） */
async function fillDirs(state) {
  const res = await api('/api/directories?type=1')
  const select = $('selectDir')
  if (!res.ok) throw res
  const dirs = flattenDirs(res.data || [])
  select.innerHTML = ''
  if (!dirs.length) {
    select.innerHTML = '<option value="">（暂无目录，请先到 JNClub 创建）</option>'
    select.disabled = true
    return
  }
  select.disabled = false
  for (const d of dirs) {
    const opt = document.createElement('option')
    opt.value = String(d.value)
    opt.textContent = d.label
    select.appendChild(opt)
  }
  const preferred = state.lastDirId || state.defaultDirId
  if (preferred) {
    const exists = dirs.some((d) => d.value === preferred)
    if (exists) select.value = String(preferred)
  }
}

function setStatus(text, type = '') {
  statusEl.textContent = text
  statusEl.className = `status ${type}`
}

/** 渲染登录态 */
async function render() {
  const state = await getState()
  $('serverText').textContent = state.server || '-'
  if (!state.token) {
    loginView.classList.remove('hidden')
    saveView.classList.add('hidden')
    return
  }
  loginView.classList.add('hidden')
  saveView.classList.remove('hidden')

  const tab = await getActiveTab()
  $('inputTitle').value = tab.title || ''
  $('inputUrl').value = tab.url || ''
  $('inputUrl').dataset.readonlyUrl = tab.url || ''

  try {
    await fillDirs(state)
  } catch (e) {
    setStatus(e?.status === 401 ? '登录已过期，请重新登录' : (e?.message || '加载目录失败'), 'err')
  }
}

/* ========== 事件 ========== */

$('btnLogin').addEventListener('click', () => {
  chrome.runtime.sendMessage({ type: 'LOGIN' })
  // 打开登录页后，关闭弹窗让用户完成登录（token 同步后回到弹窗）
  window.close()
})

$('btnSettings').addEventListener('click', () => {
  chrome.runtime.openOptionsPage ? chrome.runtime.openOptionsPage() : window.open(chrome.runtime.getURL('settings.html'))
  window.close()
})

$('btnLogout').addEventListener('click', async () => {
  await saveState({ token: '' })
  render()
})

$('btnSave').addEventListener('click', async () => {
  const title = $('inputTitle').value.trim()
  const url = $('inputUrl').value.trim()
  const directoryId = Number($('selectDir').value)
  if (!title) { setStatus('请输入标题', 'err'); return }
  if (!url) { setStatus('请输入网址', 'err'); return }
  if (!directoryId) { setStatus('请选择目录', 'err'); return }

  setStatus('收藏中…')
  const res = await chrome.runtime.sendMessage({
    type: 'FAVORITE',
    data: { title, url, directoryId },
  })
  if (res?.ok) {
    setStatus('已收藏 ✓', 'ok')
    const st = await getState()
    // 首次成功收藏的目录记为右键菜单默认目录
    await saveState({ lastDirId: directoryId, defaultDirId: st.defaultDirId || directoryId })
    setTimeout(() => window.close(), 600)
  } else if (res?.status === 401) {
    setStatus('登录已过期，请重新登录', 'err')
  } else {
    setStatus(res?.message || '收藏失败', 'err')
  }
})

$('btnBatch').addEventListener('click', () => {
  chrome.tabs.create({ url: chrome.runtime.getURL('batch/batch.html') })
  window.close()
})

render()
