/**
 * settings.js — 设置页：服务器地址 + 登录状态
 */
import { getState, saveState, serverRoot, DEFAULT_SERVER } from './lib/api.js'

const $ = (id) => document.getElementById(id)

async function render() {
  const state = await getState()
  $('inputServer').value = state.server || DEFAULT_SERVER
  const logged = !!state.token
  $('loginStateText').textContent = logged ? '已登录' : '未登录'
  $('loginStateText').className = logged ? 'jn-ok' : 'jn-err'
  $('btnLogout').classList.toggle('hidden', !logged)
}

$('btnSave').addEventListener('click', async () => {
  let server = $('inputServer').value.trim()
  server = serverRoot(server)
  try {
    // 轻量校验：可选探测（不做强制网络检查，避免误报）
    new URL(server)
  } catch {
    $('status').textContent = '请输入合法的服务器地址，如 https://jiangnan.88933.vip'
    $('status').className = 'status err'
    return
  }
  await saveState({ server })
  $('status').textContent = '已保存'
  $('status').className = 'status ok'
})

$('btnOpen').addEventListener('click', async () => {
  const state = await getState()
  chrome.tabs.create({ url: `${serverRoot(state.server)}/jnclub/` })
})

$('btnLogout').addEventListener('click', async () => {
  await saveState({ token: '' })
  render()
})

render()
