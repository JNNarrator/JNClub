// 剪贴板工具：优先 navigator.clipboard（安全上下文），
// 否则降级 document.execCommand('copy')（明文 HTTP 生产环境可用）
export async function copyText(text: string): Promise<boolean> {
  if (!text) return false
  // 方式一：Clipboard API（仅 HTTPS / localhost 安全上下文存在）
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    /* 落到降级路径 */
  }
  // 方式二：隐藏 textarea + execCommand（HTTP 环境兜底）
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}
