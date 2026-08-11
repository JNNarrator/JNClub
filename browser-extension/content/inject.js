/**
 * content/inject.js — 注入所有页面，监听 JNClub 的 jn-token 同步到插件
 * 只在 JNClub 服务器页面（含 token 的 localStorage key）生效，其余页面静默。
 * 登录流程：插件打开 {server}/jnclub/sso/login → SSO 登录 → 回跳 jnclub 页面
 * 前端 user.ts initToken() 把 token 写入 localStorage 'jn-token' → 本脚本读取并同步。
 */
(() => {
  const KEY = 'jn-token'

  const isJNClubPage = () => {
    // 通过能否读到 key 判断（避免误判同 key 的其他站点）；读取是安全操作
    try {
      return typeof localStorage !== 'undefined' && localStorage.getItem(KEY) !== null
    } catch {
      return false
    }
  }

  const sync = () => {
    try {
      const token = localStorage.getItem(KEY)
      if (!token) return
      chrome.runtime.sendMessage({ type: 'TOKEN_SYNC', token })
    } catch {
      /* 页面销毁/断连时忽略 */
    }
  }

  // 页面加载后轮询数次（SSO 回跳时 user.ts 可能稍后才写 token）
  let attempts = 0
  const timer = setInterval(() => {
    if (isJNClubPage()) {
      sync()
      if (++attempts >= 6) clearInterval(timer) // 最多 ~3s
    } else if (++attempts >= 6) {
      clearInterval(timer)
    }
  }, 500)
  // 立即尝试一次（已在页面上的情况）
  setTimeout(sync, 300)

  // 其他标签页写入 token 时（storage 事件跨 tab 触发）
  window.addEventListener('storage', (e) => {
    if (e.key === KEY) sync()
  })
})()
