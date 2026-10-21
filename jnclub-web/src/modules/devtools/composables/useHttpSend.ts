/**
 * useHttpSend.ts — 发送 HTTP 请求（浏览器版）
 *
 * 迁移自 jnx `src/composables/useHttpSend.ts`。jnx 走 Tauri 的
 * `invoke('custom_fetch')`（Rust 侧发请求，不受 CORS 限制）；Web 端只能用
 * 浏览器 `fetch`，因此行为有三点必须说明：
 *
 * 1. **CORS**：目标站点未放行本站 Origin 时，请求会被浏览器拦截，`fetch` 抛
 *    `TypeError: Failed to fetch`，拿不到状态码。这不是代码 bug，也无法在前端绕过
 *    ——这里把错误信息翻译成人话（提示可能是 CORS），并在 UI 上给出一行常驻说明。
 * 2. **无法读取全部响应头**：CORS 安全列表之外的头需要 `Access-Control-Expose-Headers`
 *    才可见，`Set-Cookie` 之类永远不可见。
 * 3. **取消**：用 AbortController 实现（jnx 那边是空实现 TODO）。
 */
import { ref, getCurrentScope, onScopeDispose } from 'vue'
import type { HttpResponseData, HttpRequestData } from '../stores/http'

export interface SendOptions {
  /** 超时（毫秒），默认 30s；0 表示不超时 */
  timeoutMs?: number
}

const BODYLESS_METHODS = new Set(['GET', 'HEAD'])

function isAbortError(err: unknown): boolean {
  return (
    !!err &&
    typeof err === 'object' &&
    ((err as { name?: string }).name === 'AbortError' ||
      (err as { code?: number }).code === 20)
  )
}

/** 把 fetch 的异常翻译成用户能看懂的说明 */
function explainError(err: unknown): string {
  if (err instanceof TypeError) {
    return '请求失败：网络不可达，或目标站点未允许跨域（CORS）。浏览器端无法绕过跨域限制。'
  }
  if (err instanceof Error) return err.message
  return String(err)
}

export function useHttpSend() {
  const loading = ref(false)
  let controller: AbortController | null = null

  async function send(
    req: Pick<HttpRequestData, 'method' | 'url' | 'headers' | 'body'>,
    opts: SendOptions = {},
  ): Promise<HttpResponseData> {
    const { timeoutMs = 30_000 } = opts

    const method = (req.method || 'GET').toUpperCase()
    const headers: Record<string, string> = {}
    for (const h of req.headers) {
      if (!h.enabled || !h.key.trim()) continue
      headers[h.key.trim()] = h.value
    }

    const body = !BODYLESS_METHODS.has(method) && req.body ? req.body : undefined

    controller?.abort()
    controller = new AbortController()
    const signal = controller.signal
    let timer: ReturnType<typeof setTimeout> | null = null
    if (timeoutMs > 0) {
      timer = setTimeout(() => controller?.abort(), timeoutMs)
    }

    loading.value = true
    const startedAt = performance.now()
    try {
      const res = await fetch(req.url, { method, headers, body, signal })
      const text = await res.text()
      const timeMs = Math.round(performance.now() - startedAt)
      const resHeaders: Record<string, string> = {}
      res.headers.forEach((value, key) => {
        resHeaders[key] = value
      })
      return {
        status: res.status,
        statusText: res.statusText,
        ok: res.ok,
        headers: resHeaders,
        body: text,
        timeMs,
        sizeBytes: new TextEncoder().encode(text).length,
      }
    } catch (err) {
      const timeMs = Math.round(performance.now() - startedAt)
      return {
        status: 0,
        statusText: isAbortError(err) ? 'Aborted' : 'Error',
        ok: false,
        headers: {},
        body: '',
        timeMs,
        sizeBytes: 0,
        error: isAbortError(err)
          ? `请求已取消或超时（${timeoutMs}ms）`
          : explainError(err),
      }
    } finally {
      if (timer) clearTimeout(timer)
      loading.value = false
      controller = null
    }
  }

  function cancel(): void {
    controller?.abort()
  }

  if (getCurrentScope()) {
    onScopeDispose(() => {
      controller?.abort()
    })
  }

  return { send, cancel, loading }
}
