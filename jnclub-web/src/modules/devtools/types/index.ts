/**
 * types/index.ts — 开发者工具注册表
 *
 * 迁移自 jnx `src/types/index.ts`，差异：
 * - 图标由内联 SVG 字符串改为 lucide-vue-next 组件（与 JNClub 设计体系一致）
 * - 分类配色改为 JNClub 设计 token（不硬编码色值）
 * - 移除 HOME_TAB（首页由 DevtoolsLayout 的启动台承担，不再是「标签页」之一）
 */
import type { Component } from 'vue'
import {
  Braces,
  Repeat,
  Terminal,
  ClipboardList,
  Clock,
  FileCode2,
  Database,
  Settings,
  Keyboard,
  House,
} from 'lucide-vue-next'

/** 工具分类 */
export type CategoryId = 'format' | 'network' | 'efficiency' | 'other'

export interface CategoryMeta {
  label: string
  icon: Component
  /** 分类强调色，取 JNClub 设计 token */
  color: string
  desc: string
}

export const CATEGORY_META: Record<CategoryId, CategoryMeta> = {
  format: { label: '格式处理', icon: Braces, color: 'var(--brand)', desc: '结构化数据的解析、美化与互转' },
  network: { label: '网络工具', icon: Terminal, color: 'var(--state-success)', desc: '接口调试与请求构造' },
  efficiency: { label: '效率工具', icon: Clock, color: 'var(--state-warning)', desc: '日常开发中的高频小工具' },
  other: { label: '系统工具', icon: Settings, color: 'var(--text-3)', desc: '偏好设置与快捷键' },
}

/** 单个工具（= 一个可打开的标签页） */
export interface ToolTab {
  id: string
  label: string
  /** 启动台卡片标题下方的补充说明 */
  desc?: string
  icon: Component
  /** 组件名，需与 DevtoolsLayout 的 componentMap 键一致 */
  component: string
  /** 启动台中占两列 */
  pinned?: boolean
  category?: CategoryId
  order?: number
  keywords?: string[]
  /** 系统工具：不在启动台主网格展示，单独一排 */
  isSystem?: boolean
}

/** 启动台入口（不是标签页） */
export const DEVTOOLS_HOME = {
  id: 'devtools-home',
  label: '工具启动台',
  icon: House,
} as const

export const ALL_TOOLS: ToolTab[] = [
  {
    id: 'json',
    label: 'JSON 工具',
    desc: '格式化 · 校验 · 树形浏览',
    icon: Braces,
    component: 'ToolJson',
    pinned: true,
    category: 'format',
    order: 0,
    keywords: ['json', 'format', '格式化', '校验', '树'],
  },
  {
    id: 'converter',
    label: '格式互转',
    desc: 'JSON / YAML / TOML / XML / CSV / Properties',
    icon: Repeat,
    component: 'ToolConverter',
    category: 'format',
    order: 1,
    keywords: ['convert', 'yaml', 'toml', 'xml', 'csv', 'properties', '转换'],
  },
  {
    id: 'json-javabean',
    label: 'JSON ⇄ JavaBean',
    desc: '实体类与 JSON 双向生成',
    icon: FileCode2,
    component: 'JsonJavabean',
    category: 'format',
    order: 2,
    keywords: ['java', 'bean', 'javabean', '实体', 'lombok'],
  },
  {
    id: 'ddl-java',
    label: 'DDL ⇄ Java',
    desc: '建表语句与实体类双向转换',
    icon: Database,
    component: 'ToolDdlJava',
    category: 'format',
    order: 3,
    keywords: ['ddl', 'sql', 'java', 'mysql', 'postgresql', 'oceanbase', '建表'],
  },
  {
    id: 'curl',
    label: 'HTTP 请求',
    desc: '接口调试 · cURL 导入',
    icon: Terminal,
    component: 'ToolCurl',
    pinned: true,
    category: 'network',
    order: 0,
    keywords: ['http', 'curl', '请求', '接口', 'api'],
  },
  {
    id: 'clipboard',
    label: '剪贴板',
    desc: '剪贴板历史与快速取用',
    icon: ClipboardList,
    component: 'ToolClipboard',
    category: 'efficiency',
    order: 0,
    keywords: ['clipboard', '剪贴板', '历史'],
  },
  {
    id: 'cron',
    label: 'Cron 表达式',
    desc: '解析 · 生成 · 执行时间预览',
    icon: Clock,
    component: 'ToolCron',
    category: 'efficiency',
    order: 1,
    keywords: ['cron', '表达式', '定时', 'quartz'],
  },
  {
    id: 'settings',
    label: '工具设置',
    desc: '标签页与工具记忆偏好',
    icon: Settings,
    component: 'DevtoolsSettings',
    isSystem: true,
    category: 'other',
    order: 0,
  },
  {
    id: 'shortcuts',
    label: '快捷键',
    desc: '自定义开发者工具内的键位',
    icon: Keyboard,
    component: 'DevtoolsShortcuts',
    isSystem: true,
    category: 'other',
    order: 1,
  },
]

/** 可打开为标签页的工具 id 集合（用于校验持久化数据） */
export const TOOL_IDS = ALL_TOOLS.map((t) => t.id)

export function findTool(id: string): ToolTab | undefined {
  return ALL_TOOLS.find((t) => t.id === id)
}

/* ─── JSON 工具用到的树节点类型（迁移自 jnx types） ─── */
export interface FlatJsonNode {
  id: string
  parentId: string | null
  key: string
  value: any
  valueType: 'object' | 'array' | 'string' | 'number' | 'boolean' | 'null'
  depth: number
  expanded: boolean
  hasChildren: boolean
  childCount: number
  path: string
}

export interface JsonParseResult {
  nodes: FlatJsonNode[]
  rootId: string
  formatted: string
}

/* ─── 命令面板动作（⌘⇧P） ─── */
export interface PaletteAction {
  id: string
  label: string
  category?: string
  keywords?: string[]
  run: () => void
}
