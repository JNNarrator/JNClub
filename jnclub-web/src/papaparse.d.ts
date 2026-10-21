/**
 * papaparse.d.ts — papaparse 的最小类型声明
 *
 * 为什么不装 `@types/papaparse`：本项目只用到 `parse` / `unparse` 两个入口
 * （见 `modules/devtools/utils/converter.ts`），而 `@types/papaparse` 会额外引入
 * 一整套配置类型并参与 `noUnusedLocals` 检查；用局部声明更小更可控。
 * 若后续需要完整类型，删除本文件并 `npm i -D @types/papaparse` 即可。
 */
declare module 'papaparse' {
  export interface ParseConfig {
    header?: boolean
    skipEmptyLines?: boolean | 'greedy'
    dynamicTyping?: boolean
    delimiter?: string
    quotes?: boolean
    newline?: string
  }

  export interface ParseMeta {
    delimiter: string
    linebreak: string
    aborted: boolean
    truncated: boolean
    cursor: number
  }

  export interface ParseError {
    type: string
    code: string
    message: string
    row?: number
  }

  export interface ParseResult<T> {
    data: T
    errors: ParseError[]
    meta: ParseMeta
  }

  export function parse<T = unknown[]>(input: string, config?: ParseConfig): ParseResult<T>

  export interface UnparseConfig {
    quotes?: boolean | boolean[]
    quoteChar?: string
    delimiter?: string
    header?: boolean
    newline?: string
    skipEmptyLines?: boolean | 'greedy'
    columns?: string[]
  }

  export function unparse(data: unknown, config?: UnparseConfig): string

  const Papa: {
    parse: typeof parse
    unparse: typeof unparse
  }
  export default Papa
}
