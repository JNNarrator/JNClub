/**
 * navConfig.ts — 导航注册表
 * 左栏可配置导航项的唯一定义：SideNav / NavEditorDrawer / MobileTabBar 共享
 * 可见性偏好 nav.hidden（隐藏列表）、顺序偏好 nav.order（全量顺序，可见在前/隐藏在后）
 */
import { Bookmark, StickyNote, Cloud, KeyRound, Music, Trash2, Puzzle } from 'lucide-vue-next'

/** 可配置导航项 key */
export type NavKey = 'bookmarks' | 'notes' | 'files' | 'vault' | 'music' | 'recycle' | 'extension'

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
  bookmarks: { icon: Bookmark, label: '收藏夹', kind: 'module', target: '' },
  notes: { icon: StickyNote, label: '便签', kind: 'module', target: '' },
  files: { icon: Cloud, label: '云盘', kind: 'module', target: '' },
  vault: { icon: KeyRound, label: '密码库', kind: 'module', target: '' },
  music: { icon: Music, label: '音乐', kind: 'route', target: '/music' },
  recycle: { icon: Trash2, label: '回收站', kind: 'route', target: '/recycle' },
  extension: { icon: Puzzle, label: '浏览器插件', kind: 'route', target: '/extension' },
}

/** 默认全量顺序（兼容旧 nav.order 缺失项时补齐） */
export const DEFAULT_ORDER: NavKey[] = ['bookmarks', 'notes', 'files', 'vault', 'music', 'recycle', 'extension']

/** 移动端底部 TabBar 参与项（音乐/插件页移动端不显示，保持 5 tab） */
export const MOBILE_KEYS: NavKey[] = ['bookmarks', 'notes', 'files', 'vault', 'recycle']

/** 过滤出合法导航 key（剔除脏数据） */
export function normalizeNavKeys(arr: any[]): NavKey[] {
  return arr.filter((k): k is NavKey => typeof k === 'string' && !!NAV_META[k as NavKey])
}

/** 补齐缺失的默认导航项（防旧数据缺项导致入口消失） */
export function completeOrder(keys: NavKey[]): NavKey[] {
  const present = new Set<NavKey>(keys)
  DEFAULT_ORDER.forEach(k => { if (!present.has(k)) keys.push(k) })
  return keys
}
