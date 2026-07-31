/**
 * stripMarkdown — 将 Markdown/HTML 文本转为纯文本摘要
 * 依次移除：图片/链接/代码/标题/列表/引用/HTML标签，保留链接文字
 */
export function stripMarkdown(s: string): string {
  if (!s) return ''

  let text = s

  // 1. 图片 ![alt](url) 和 <img ...> 标签
  text = text.replace(/!\[.*?\]\(.*?\)/g, '')
  text = text.replace(/<img[^>]*\/?>/gi, '')

  // 2. 链接 [text](url) → 保留 text
  text = text.replace(/\[([^\]]*)\]\(.*?\)/g, '$1')

  // 3. 代码围栏 ```...```
  text = text.replace(/```[\s\S]*?```/g, ' ')

  // 4. 行内代码 `...`
  text = text.replace(/`([^`]*)`/g, '$1')

  // 5. 标题符 #
  text = text.replace(/^#{1,6}\s+/gm, '')

  // 6. 无序列表符 - * +
  text = text.replace(/^[\s]*[-*+]\s+/gm, '')

  // 7. 有序列表符 1. 2.
  text = text.replace(/^[\s]*\d+\.\s+/gm, '')

  // 8. 引用 >
  text = text.replace(/^[\s]*>\s?/gm, '')

  // 9. HTML 标签
  text = text.replace(/<[^>]*>/g, '')

  // 10. 粗体/斜体标记
  text = text.replace(/\*{1,3}([^*]+)\*{1,3}/g, '$1')
  text = text.replace(/_{1,3}([^_]+)_{1,3}/g, '$1')

  // 11. 删除线 ~~
  text = text.replace(/~~([^~]+)~~/g, '$1')

  // 12. 水平线
  text = text.replace(/^[-*_]{3,}\s*$/gm, ' ')

  // 13. 折叠空白
  text = text.replace(/\s+/g, ' ').trim()

  return text
}

/**
 * 从内容生成摘要：先 strip markdown，再按长度截断
 */
export function getSummary(content: string | null | undefined, maxLen = 100): string {
  const plain = stripMarkdown(content || '')
  if (!plain) return '暂无内容'
  return plain.length > maxLen ? plain.substring(0, maxLen) + '…' : plain
}
