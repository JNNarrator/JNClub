export interface ParsedCurl {
  method: string
  url: string
  headers: Array<{ key: string; value: string }>
  body: string
  followRedirects: boolean
  error?: string
}

const METHODS = ['GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS','CONNECT','TRACE']

export function parseCurl(input: string): ParsedCurl {
  // 1. Normalize: strip leading/trailing space, merge line continuations
  let text = input
    .replace(/\\\n/g, ' ')      // Unix \ 续行
    .replace(/\^\n/g, ' ')      // Windows ^ 续行
    .replace(/\r\n/g, '\n')     // normalize CRLF
    .trim()

  // 2. Remove 'curl ' prefix if present
  if (text.toLowerCase().startsWith('curl ')) {
    text = text.slice(5).trim()
  }

  // 3. Tokenize (respect quotes)
  const tokens = tokenize(text)

  // 4. Parse tokens
  const result: ParsedCurl = {
    method: 'GET',
    url: '',
    headers: [],
    body: '',
    followRedirects: false,
  }

  let i = 0
  while (i < tokens.length) {
    const token = tokens[i]

    // Long options: --xxx
    if (token.startsWith('--')) {
      const opt = token.slice(2)
      switch (opt) {
        case 'request':
        case 'method': {
          const val = nextToken(tokens, i)
          if (val && METHODS.includes(val.toUpperCase())) {
            result.method = val.toUpperCase()
            i++
          } else if (val) {
            result.error = `未知方法: ${val}`
            return result
          }
          break
        }
        case 'header': {
          const val = nextToken(tokens, i)
          if (val) { result.headers.push(parseHeader(val)); i++ }
          break
        }
        case 'data':
        case 'data-raw':
        case 'data-binary': {
          const val = nextToken(tokens, i)
          if (val) { result.body += (result.body ? '&' : '') + val; i++ }
          if (result.method === 'GET') result.method = 'POST'
          break
        }
        case 'data-urlencode': {
          const val = nextToken(tokens, i)
          if (val) { result.body += (result.body ? '&' : '') + encodeURIComponent(val); i++ }
          if (result.method === 'GET') result.method = 'POST'
          break
        }
        case 'cookie': {
          const val = nextToken(tokens, i)
          if (val) { result.headers.push({ key: 'Cookie', value: val }); i++ }
          break
        }
        case 'user': {
          const val = nextToken(tokens, i)
          if (val) {
            result.headers.push({ key: 'Authorization', value: 'Basic ' + btoa(val) })
            i++
          }
          break
        }
        case 'user-agent': {
          const val = nextToken(tokens, i)
          if (val) { result.headers.push({ key: 'User-Agent', value: val }); i++ }
          break
        }
        case 'referer': {
          const val = nextToken(tokens, i)
          if (val) { result.headers.push({ key: 'Referer', value: val }); i++ }
          break
        }
        case 'location':
        case 'location-trusted':
          result.followRedirects = true; break
        case 'compressed':
        case 'insecure':
        case 'silent':
        case 'verbose':
        case 'include':
        case 'head':
          break // ignore
        default:
          // unknown --option, may have value
          break
      }
    }
    // Short options: -X, -H, -d, etc.
    else if (token.startsWith('-') && token !== '-' && !token.startsWith('--')) {
      const chars = token.slice(1)
      let j = 0
      while (j < chars.length) {
        const c = chars[j]
        switch (c) {
          case 'X': {
            const val = nextToken(tokens, i, j + 1)
            if (val && METHODS.includes(val.toUpperCase())) {
              result.method = val.toUpperCase()
              consumePos(tokens, i, j + 1, (ni, nj) => { i = ni; j = nj })
            } else if (val) {
              result.error = `未知方法: ${val}`
              return result
            }
            break
          }
          case 'H': {
            const val = nextToken(tokens, i, j + 1)
            if (val) { result.headers.push(parseHeader(val)); consumePos(tokens, i, j + 1, (ni, nj) => { i = ni; j = nj }) }
            break
          }
          case 'd': {
            const val = nextToken(tokens, i, j + 1)
            if (val) { result.body += (result.body ? '&' : '') + val; consumePos(tokens, i, j + 1, (ni, nj) => { i = ni; j = nj }) }
            if (result.method === 'GET') result.method = 'POST'
            break
          }
          case 'b': {
            const val = nextToken(tokens, i, j + 1)
            if (val) { result.headers.push({ key: 'Cookie', value: val }); consumePos(tokens, i, j + 1, (ni, nj) => { i = ni; j = nj }) }
            break
          }
          case 'u': {
            const val = nextToken(tokens, i, j + 1)
            if (val) { result.headers.push({ key: 'Authorization', value: 'Basic ' + btoa(val) }); consumePos(tokens, i, j + 1, (ni, nj) => { i = ni; j = nj }) }
            break
          }
          case 'A': {
            const val = nextToken(tokens, i, j + 1)
            if (val) { result.headers.push({ key: 'User-Agent', value: val }); consumePos(tokens, i, j + 1, (ni, nj) => { i = ni; j = nj }) }
            break
          }
          case 'e': {
            const val = nextToken(tokens, i, j + 1)
            if (val) { result.headers.push({ key: 'Referer', value: val }); consumePos(tokens, i, j + 1, (ni, nj) => { i = ni; j = nj }) }
            break
          }
          case 'k': case 'L': case 's': case 'S': case 'v': case 'i': case 'N': case 'l':
            break // ignore flags
          default:
            // pos: unknown short option, may have value
            break
        }
        j++
      }
    }
    // Positional: URL
    else if (!result.url) {
      result.url = token
    }

    i++
  }

  if (!result.url) {
    result.error = '未找到 URL'
  }

  return result
}

function nextToken(tokens: string[], tokenIdx: number, charPos: number = -1): string | null {
  if (charPos >= 0) {
    // Consume remaining characters in current token as option value
    const chars = tokens[tokenIdx].slice(1) // strip leading -
    const remaining = chars.slice(charPos)
    if (remaining.length > 0) return remaining
  }
  return tokens[tokenIdx + 1] || null
}

function consumePos(
  tokens: string[], tokenIdx: number, charPos: number,
  cb: (ni: number, nj: number) => void
) {
  const chars = tokens[tokenIdx].slice(1)
  if (charPos < chars.length) {
    // Value was in the same token
  } else {
    cb(tokenIdx + 1, 0)
  }
}

function parseHeader(val: string): { key: string; value: string } {
  const colon = val.indexOf(':')
  if (colon <= 0) return { key: val.trim(), value: '' }
  return {
    key: val.slice(0, colon).trim(),
    value: val.slice(colon + 1).trim(),
  }
}

function tokenize(input: string): string[] {
  const tokens: string[] = []
  let current = ''
  let inSingle = false
  let inDouble = false
  let escape = false

  for (const ch of input) {
    if (escape) { current += ch; escape = false; continue }

    if (ch === '\\' && inDouble) { current += ch; escape = true; continue }

    if (ch === "'" && !inDouble) { inSingle = !inSingle; continue }
    if (ch === '"' && !inSingle) { inDouble = !inDouble; continue }

    if (ch === ' ' && !inSingle && !inDouble) {
      if (current.trim()) tokens.push(current.trim())
      current = ''
      continue
    }

    current += ch
  }

  if (current.trim()) tokens.push(current.trim())
  if (inSingle || inDouble) console.warn('[parseCurl] 引号不配对')
  return tokens
}
