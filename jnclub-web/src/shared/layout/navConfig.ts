/**
 * navConfig.ts — 导航注册表
 * 左栏可配置导航项的唯一定义：SideNav / NavEditorDrawer / MobileTabBar 共享
 * 可见性偏好 nav.hidden（隐藏列表）、顺序偏好 nav.order（全量顺序，可见在前/隐藏在后）
 */
import { Bookmark, StickyNote, Cloud, KeyRound, Music, Trash2, Puzzle, LayoutDashboard, ListTodo, CalendarDays, Rss, GlobeLock } from 'lucide-vue-next'

/** 可配置导航项 key */
export type NavKey = 'overview' | 'bookmarks' | 'notes' | 'files' | 'vault' | 'todos' | 'calendar' | 'feeds' | 'music' | 'recycle' | 'webdav' | 'extension'

export interface NavDef {
  key: NavKey
  icon: any
  label: string
  /** module：主壳内 4 大模块（emit module-change）；route：独立路由（router.push） */
  kind: 'module' | 'route'
  /** kind='route' 时的跳转路径 */
  target: string
}

export const NAV_META: Record<NavKey, Omit<NavDef, 'key'>> = {
  overview: { icon: LayoutDashboard, label: '概览', kind: 'route', target: '/overview' },
  bookmarks: { icon: Bookmark, label: '收藏夹', kind: 'module', target: '' },
  notes: { icon: StickyNote, label: '便签', kind: 'module', target: '' },
  files: { icon: Cloud, label: '云盘', kind: 'module', target: '' },
  vault: { icon: KeyRound, label: '密码库', kind: 'module', target: '' },
  todos: { icon: ListTodo, label: '待办', kind: 'route', target: '/todos' },
  calendar: { icon: CalendarDays, label: '日历', kind: 'route', target: '/calendar' },
  feeds: { icon: Rss, label: '订阅', kind: 'route', target: '/feeds' },
  music: { icon: Music, label: '音乐', kind: 'route', target: '/music' },
  recycle: { icon: Trash2, label: '回收站', kind: 'route', target: '/recycle' },
  webdav: { icon: GlobeLock, label: 'WebDAV', kind: 'route', target: '/webdav' },
  extension: { icon: Puzzle, label: '下载中心', kind: 'route', target: '/extension' },
}

/** 默认全量顺序（兼容旧 nav.order 缺失项时补齐） */
export const DEFAULT_ORDER: NavKey[] = ['overview', 'bookmarks', 'notes', 'files', 'vault', 'todos', 'calendar', 'feeds', 'music', 'recycle', 'webdav', 'extension']

/** 移动端底部 TabBar 参与项（音乐/插件/WebDAV 页移动端不显示；日历/订阅纳入，横向滚动容纳更多入口） */
export const MOBILE_KEYS: NavKey[] = ['overview', 'bookmarks', 'notes', 'files', 'vault', 'todos', 'calendar', 'feeds', 'recycle']

/** 过滤出合法导航 key（剔除脏数据） */
export function normalizeNavKeys(arr: any[]): NavKey[] {
  return arr.filter((k): k is NavKey => typeof k === 'string' && !!NAV_META[k as NavKey])
}

/** 补齐缺失的默认导航项（防旧数据缺项导致入口消失） */
export function completeOrder(keys: NavKey[]): NavKey[] {
  const present = new Set<NavKey>(keys)
  const result = [...keys]
  for (const k of DEFAULT_ORDER) {
    if (present.has(k)) continue
    // 锚点插入：放在第一个「默认序位于 k 之后」的现存项前面，
    // 既保持 DEFAULT_ORDER 的相对位置（如概览始终在最前），又不破坏用户自定义的相对顺序
    const anchor = DEFAULT_ORDER.indexOf(k)
    let pos = result.length
    for (let i = 0; i < result.length; i++) {
      if (DEFAULT_ORDER.indexOf(result[i]) > anchor) {
        pos = i
        break
      }
    }
    result.splice(pos, 0, k)
    present.add(k)
  }
  return result
}
