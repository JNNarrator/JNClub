/**
 * popup/popup.js — 弹窗：当前页一键收藏（稍后读 / 网页快照 / 保存去重）+ 网页转便签 + 登录态管理 + 入口
 */
import {
  getState, saveState, serverRoot, api,
  fetchDirectories, fetchNoteDirs, flattenDirs, fetchBookmarks, captureSnapshot,
} from '../lib/api.js'
import { normalizeUrl } from '../lib/urls.js'

const $ = (id) => document.getElementById(id)

const loginView = $('loginView')
const saveView = $('saveView')
const noteView = $('noteView')
const statusEl = $('saveStatus')
const noteStatusEl = $('noteStatus')

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

/** 恢复/记忆保存选项偏好 */
async function loadSaveOptions() {
  const st = await getState()
  $('chkReadLater').checked = !!st.saveReadLater
  $('chkSnapshot').checked = !!st.saveSnapshot
}
async function persistSaveOptions() {
  await saveState({
    saveReadLater: $('chkReadLater').checked,
    saveSnapshot: $('chkSnapshot').checked,
  })
}

/** 目标目录内查重复收藏；目录过大（>500 条）时跳过，避免弹窗卡顿 */
async function findDuplicate(url, directoryId) {
  if (!url || !directoryId) return null
  const target = normalizeUrl(url)
  const list = await fetchBookmarks(directoryId)
  if (!list || list.length > 500) return null
  return list.find((b) => b.url && normalizeUrl(b.url) === target) || null
}

function setStatus(text, type = '') {
  statusEl.textContent = text
  statusEl.className = `status ${type}`
}

function setNoteStatus(text, type = '') {
  noteStatusEl.textContent = text
  noteStatusEl.className = `status ${type}`
}

/** 当前便签 Markdown 草稿（保存按钮用） */
let noteDraft = null

/** 切换视图：收藏 / 便签 / 登录 */
function switchView(view) {
  loginView.classList.toggle('hidden', view !== 'login')
  saveView.classList.toggle('hidden', view !== 'save')
  noteView.classList.toggle('hidden', view !== 'note')
}

/** 渲染登录态 */
async function render() {
  const state = await getState()
  $('serverText').textContent = state.server || '-'
  if (!state.token) {
    switchView('login')
    return
  }
  switchView('save')

  const tab = await getActiveTab()
  $('inputTitle').value = tab.title || ''
  $('inputUrl').value = tab.url || ''
  $('inputUrl').dataset.readonlyUrl = tab.url || ''

  try {
    await fillDirs(state)
    await loadSaveOptions()
  } catch (e) {
    setStatus(e?.status === 401 ? '登录已过期，请重新登录' : (e?.message || '加载目录失败'), 'err')
  }
}

/** 填充便签目录下拉（记忆上次选择 / 右键默认目录） */
async function fillNoteDirs(state) {
  const res = await api('/api/directories?type=2')
  const select = $('noteSelectDir')
  if (!res.ok) throw res
  const dirs = flattenDirs(res.data || [])
  select.innerHTML = ''
  if (!dirs.length) {
    select.innerHTML = '<option value="">（暂无便签目录，请先到 JNClub 创建）</option>'
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
  const preferred = state.noteLastDirId || state.noteDefaultDirId
  if (preferred) {
    const exists = dirs.some((d) => d.value === preferred)
    if (exists) select.value = String(preferred)
  }
}

/** 提取当前页 Markdown 并进入便签预览视图 */
async function openNoteFromTab(tab) {
  setNoteStatus('正在提取正文…')
  switchView('note')
  try {
    const res = await chrome.tabs.sendMessage(tab.id, { type: 'EXTRACT_MARKDOWN' })
    if (!res?.ok) {
      setNoteStatus(res?.message || '该页面无法转换（可能为浏览器内置页面）', 'err')
      return
    }
    noteDraft = res
    $('noteTitle').value = res.title || ''
    $('notePreview').value = res.markdown
    $('noteChars').textContent = `约 ${res.markdown.length} 字符`
    await fillNoteDirs(await getState())
    setNoteStatus('', '')
  } catch {
    setNoteStatus('该页面无法转换（可能为浏览器内置页面）', 'err')
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

  await persistSaveOptions()

  // 保存前去重提示（目标目录已存在同链接）
  let dup = null
  try {
    dup = await findDuplicate(url, directoryId)
  } catch { dup = null }
  if (dup) {
    const dupLabel = dup.title || dup.url || '同名收藏'
    if (!confirm(`「${dupLabel}」已在所选目录中，仍要保存吗？`)) {
      setStatus('已取消（目录中已有该链接）', 'err')
      return
    }
  }

  setStatus('收藏中…')
  const res = await chrome.runtime.sendMessage({
    type: 'FAVORITE',
    data: {
      title,
      url,
      directoryId,
      readLater: $('chkReadLater').checked ? 1 : 0,
    },
  })
  if (res?.ok) {
    setStatus('已收藏 ✓', 'ok')
    const st = await getState()
    // 首次成功收藏的目录记为右键菜单默认目录
    await saveState({ lastDirId: directoryId, defaultDirId: st.defaultDirId || directoryId })
    // 联动快照：收藏成功后触发（失败不阻断，给提示即可）
    if ($('chkSnapshot').checked && res.data?.id) {
      setStatus('收藏成功，保存快照中…')
      const snap = await captureSnapshot(res.data.id)
      setStatus(
        snap.ok ? '已收藏 ✓ · 快照已保存' : '已收藏 ✓ · 快照失败（稍后可在 Web 端重试）',
        snap.ok ? 'ok' : 'err',
      )
    }
    setTimeout(() => window.close(), 1200)
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

/* ========== 网页转便签 ========== */

$('btnToNote').addEventListener('click', async () => {
  const tab = await getActiveTab()
  if (!tab?.id) { setStatus('无法获取当前标签页', 'err'); return }
  await openNoteFromTab(tab)
})

$('btnNoteBack').addEventListener('click', () => {
  noteDraft = null
  switchView('save')
})

$('btnNoteSave').addEventListener('click', async () => {
  const directoryId = Number($('noteSelectDir').value)
  const content = noteDraft?.markdown || ''
  const title = $('noteTitle').value.trim() || noteDraft?.title || ''
  if (!directoryId) { setNoteStatus('请选择便签目录', 'err'); return }
  if (!content.trim()) { setNoteStatus('内容为空，无法保存', 'err'); return }

  setNoteStatus('保存中…')
  const res = await chrome.runtime.sendMessage({
    type: 'CREATE_NOTE',
    data: { title, content, directoryId },
  })
  if (res?.ok) {
    setNoteStatus('已保存为便签 ✓', 'ok')
    const st = await getState()
    // 记录便签默认目录（右键菜单「保存为 JNClub 便签」使用）
    await saveState({ noteLastDirId: directoryId, noteDefaultDirId: st.noteDefaultDirId || directoryId })
    setTimeout(() => window.close(), 600)
  } else if (res?.status === 401) {
    setNoteStatus('登录已过期，请重新登录', 'err')
  } else {
    setNoteStatus(res?.message || '保存失败', 'err')
  }
})

render()