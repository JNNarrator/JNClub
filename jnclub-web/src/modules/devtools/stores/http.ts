/**
 * useHttpStore — HTTP 请求工具的状态
 *
 * 迁移自 jnx `src/stores/http.ts`，逻辑保持一致，差异仅在注释与持久化策略：
 * - jnx 桌面端把请求体放内存即可（进程常驻）；Web 端刷新会丢，但**故意不持久化**：
 *   请求里常带 Authorization / API Key，落 localStorage 或后端 KV 都是泄露面。
 * - 响应同理只存内存。
 */
import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'

export interface HttpHeader {
  key: string
  value: string
  enabled: boolean
}

export interface HttpRequestData {
  method: string
  url: string
  headers: HttpHeader[]
  body: string
}

export interface HttpResponseData {
  status: number
  statusText: string
  ok: boolean
  headers: Record<string, string>
  body: string
  timeMs: number
  sizeBytes: number
  error?: string
}

export const useHttpStore = defineStore('devtoolsHttp', () => {
  const request = reactive<HttpRequestData>({
    method: 'GET',
    url: '',
    headers: [],
    body: '',
  })

  const response = ref<HttpResponseData | null>(null)
  const loading = ref(false)

  /** 从 cURL / 粘贴内容导入后填充请求 */
  function setFromParsed(p: {
    method: string
    url: string
    headers: Array<{ key: string; value: string }>
    body: string
  }): void {
    request.method = p.method
    request.url = p.url
    request.headers = p.headers.map((h) => ({ ...h, enabled: true }))
    request.body = p.body
    if (!request.headers.length) {
      request.headers.push({ key: '', value: '', enabled: true })
    }
  }

  function reset(): void {
    request.method = 'GET'
    request.url = ''
    request.headers = []
    request.body = ''
    response.value = null
    loading.value = false
  }

  return { request, response, loading, setFromParsed, reset }
})
