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
})();

/**
 * 网页转 Markdown：监听 EXTRACT_MARKDOWN 消息
 * 流程：克隆 document → Readability 提取正文 → 图片 src 补全绝对 URL → Turndown 转 Markdown。
 * 依赖 manifest 中先注入的 vendor/Readability.js 与 vendor/turndown.js（全局 Readability / TurndownService）。
 */
chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg?.type !== 'EXTRACT_MARKDOWN') return false

  try {
    // 克隆文档，避免 Readability 原地删除节点污染页面
    const clone = document.cloneNode(true)
    const article = new Readability(clone).parse()
    if (!article || !article.content) {
      sendResponse({ ok: false, message: '未能提取到正文内容' })
      return true
    }

    // 图片相对路径补全为绝对 URL（否则在便签里会相对 JNClub 域名而失效）
    const tmp = document.createElement('div')
    tmp.innerHTML = article.content
    tmp.querySelectorAll('img[src]').forEach((img) => {
      try {
        img.setAttribute('src', new URL(img.getAttribute('src'), location.href).href)
      } catch { /* 保留原值 */ }
    })

    // 兜底清洗：Readability 对内容少/结构特殊的页面可能保留导航等噪音，确定性移除
    tmp.querySelectorAll('nav, footer, form, script, style, iframe').forEach((el) => el.remove())

    const markdown = new TurndownService({ headingStyle: 'atx', codeBlockStyle: 'fenced' }).turndown(tmp)
    sendResponse({ ok: true, title: article.title || document.title, markdown, url: location.href })
  } catch (e) {
    sendResponse({ ok: false, message: `转换失败：${e?.message || e}` })
  }
  return true
})
