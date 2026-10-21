<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FlatJsonNode } from '../types'

const props = defineProps<{ nodes: FlatJsonNode[]; searchQuery: string }>()
const emit = defineEmits<{
  (e: 'update:searchQuery', v: string): void
  (e: 'expandAll'): void
  (e: 'collapseAll'): void
  (e: 'toggleExpand', id: string): void
  (e: 'copyKey', path: string): void
  (e: 'copyValue', node: FlatJsonNode): void
  (e: 'loadSample'): void
}>()

const ctxMenu = ref<{ x: number; y: number; visible: boolean; node: FlatJsonNode | null }>({ x: 0, y: 0, visible: false, node: null })

const visibleNodes = computed(() => {
  const nodes = props.nodes
  if (!nodes.length) return []
  const q = props.searchQuery.trim().toLowerCase()
  const sa = q.length > 0

  if (sa) {
    const m = new Set<string>(), mp = new Map(nodes.map(n => [n.id, n]))
    for (const n of nodes) {
      if (String(n.key).toLowerCase().includes(q) || (!n.hasChildren && String(n.value).toLowerCase().includes(q))) {
        let p: string | null = n.id; while (p) { m.add(p); p = mp.get(p)?.parentId || null }
      }
    }
    const r: FlatJsonNode[] = [], s = new Set<string>(), st = [nodes[0]]
    while (st.length) { const n = st.pop()!; if (s.has(n.id)) continue; s.add(n.id); if (m.has(n.id)) { r.push(n); if (n.hasChildren) nodes.forEach(c => c.parentId === n.id && st.push(c)) } }
    return r
  }

  const r: FlatJsonNode[] = [], s = new Set<string>(), st = [nodes[0]]
  while (st.length) { const n = st.pop()!; if (s.has(n.id)) continue; s.add(n.id); r.push(n); if (n.hasChildren && n.expanded) { const c = nodes.filter(x => x.parentId === n.id); for (let i = c.length - 1; i >= 0; i--) st.push(c[i]) } }
  return r
})

const searchActive = computed(() => props.searchQuery.trim().length > 0)

function onRightClick(e: MouseEvent, node: FlatJsonNode) {
  e.preventDefault(); ctxMenu.value = { x: e.clientX, y: e.clientY, visible: true, node }
}
function fmt(v: any, t: string) { return t === 'null' ? 'null' : t === 'string' ? `"${v}"` : String(v) }

const typeColors: Record<string, string> = {
  string: 'var(--json-green, var(--state-success))',
  number: 'var(--json-blue, var(--brand))',
  boolean: 'var(--json-purple, var(--brand-suppl))',
  null: 'var(--json-gray, var(--text-3))',
}

function typeColor(t: string) { return typeColors[t] || 'var(--json-text, var(--text-1))' }

function bracketLabel(n: FlatJsonNode) {
  if (n.expanded) return ""
  const o = n.valueType === "array" ? "[" : "{", c = n.valueType === "array" ? "]" : "}"
  return `${o} ${n.valueType === "array" ? `${n.childCount} 项` : `${n.childCount} 个属性`} ${c}`
}
function copyNodePath(n: FlatJsonNode) {
  try { (navigator as any).clipboard.writeText(n.path) } catch {}
}
function clickHide() { ctxMenu.value.visible = false }
</script>
<template>
  <div class="tree-wrap" @click="clickHide">
    <!-- Header -->
    <div class="tree-header">
      <div class="header-left">
        <span class="header-label">树形视图</span>
        <span v-if="nodes.length" class="header-meta">{{ nodes.length }} 节点</span>
      </div>
      <div class="header-right">
        <div class="search-box" :class="{ active: searchActive }">
          <svg class="search-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="7" cy="7" r="5"/><path d="M11 11l3 3"/>
          </svg>
          <input
            :value="searchQuery"
            @input="emit('update:searchQuery', ($event.target as HTMLInputElement).value)"
            placeholder="搜索…"
            class="search-input"
          />
          <kbd class="search-kbd">⌘F</kbd>
        </div>
        <button class="icon-btn" title="展开全部" @click="emit('expandAll')">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M5 8l5 5 5-5"/>
          </svg>
        </button>
        <button class="icon-btn" title="合并全部" @click="emit('collapseAll')">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M15 12l-5-5-5 5"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Empty state -->
    <div v-if="!nodes.length" class="tree-empty">
      <svg class="empty-icon" viewBox="0 0 60 60" fill="none" stroke="var(--json-text-secondary, #6B6B6B)" stroke-width="1" opacity="0.4">
        <path d="M15 10h30l8 8v30a2 2 0 01-2 2H9a2 2 0 01-2-2V12a2 2 0 012-2h6z"/><path d="M15 10V8a2 2 0 012-2h26a2 2 0 012 2v2"/><path d="M20 28l4 4-4 4M28 36h8"/>
      </svg>
      <div class="empty-title">暂无数据</div>
      <div class="empty-desc">点击「执行」后在此展示树形结构</div>
      <button class="ghost-btn" @click="$emit('loadSample')">
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" style="width:14px;height:14px">
          <path d="M8 3v10M3 8h10"/>
        </svg>
        载入示例 JSON
      </button>
    </div>

    <!-- Tree -->
    <div v-else class="tree-view">
      <div
        v-for="n in visibleNodes"
        :key="n.id"
        class="tree-node"
        :style="{ paddingLeft: (n.depth * 20 + 12) + 'px' }"
        @contextmenu="onRightClick($event, n)"
      >
        <span v-if="n.hasChildren" class="toggle" @click="emit('toggleExpand', n.id)">
          <svg :class="{ rotated: n.expanded }" viewBox="0 0 12 12" fill="currentColor" width="10" height="10">
            <path d="M4 2l5 4-5 4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </span>
        <span v-else style="width:16px;flex-shrink:0;display:inline-block"></span>

        <span class="nk" :style="{ color: 'var(--json-primary, #E84C6F)' }">{{ n.key }}</span>
        <span v-if="!n.hasChildren" class="ns">: </span>
        <span v-if="!n.hasChildren" class="nv" :style="{ color: typeColor(n.valueType) }">{{ fmt(n.value, n.valueType) }}</span>
        <span v-if="n.hasChildren && !n.expanded" class="nb">{{ bracketLabel(n) }}</span>
      </div>
    </div>

    <!-- Context menu -->
    <Teleport to="body">
      <div v-if="ctxMenu.visible" class="ctx" :style="{ left: ctxMenu.x+'px', top: ctxMenu.y+'px' }" @click.stop>
        <div class="mi" @click="ctxMenu.node && emit('copyKey', ctxMenu.node.path)">复制键</div>
        <div class="mi" @click="ctxMenu.node && emit('copyValue', ctxMenu.node)">复制值</div>
        <div class="mi" @click="ctxMenu.node && copyNodePath(ctxMenu.node)">复制路径</div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.tree-wrap {
  display: flex; flex-direction: column; flex: 1; min-height: 0;
  border: 1px solid var(--json-border, rgba(232,76,111,0.12));
  border-radius: 10px;
  background: var(--json-bg, #FFFFFF);
  overflow: hidden;
}
.tree-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--json-border, rgba(232,76,111,0.08));
  flex-shrink: 0;
  background: var(--json-surface, #FAFAFA);
}
.header-left { display: flex; align-items: center; gap: 10px; }
.header-label { font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.4px; color: var(--json-text, #1A1A1A); }
.header-meta { font-size: 11px; color: var(--json-text-secondary, #6B6B6B); }
.header-right { display: flex; align-items: center; gap: 6px; }

.icon-btn {
  width: 28px; height: 28px; display: flex; align-items: center; justify-content: center;
  border: none; border-radius: 6px;
  background: transparent; color: var(--json-text-secondary, #6B6B6B);
  cursor: pointer; transition: all 0.15s;
}
.icon-btn:hover { background: var(--json-primary-light, rgba(232,76,111,0.08)); color: var(--json-primary, #E84C6F); }
.icon-btn svg { width: 16px; height: 16px; }

/* Search */
.search-box {
  display: flex; align-items: center; gap: 5px;
  padding: 0 8px; height: 28px;
  border: 1px solid var(--json-border, rgba(232,76,111,0.15));
  border-radius: 6px;
  background: var(--json-bg, #FFF);
  transition: border-color 0.2s;
}
.search-box:focus-within, .search-box.active { border-color: var(--json-primary, #E84C6F); }
.search-icon { width: 14px; height: 14px; color: var(--json-text-secondary); flex-shrink: 0; }
.search-input {
  border: none; outline: none; background: transparent;
  font-size: 12px; color: var(--json-text, #1A1A1A); font-family: inherit;
  width: 80px;
}
.search-input::placeholder { color: var(--json-text-secondary, #6B6B6B); }
.search-kbd {
  font-size: 9px; padding: 1px 4px; border-radius: 3px;
  background: var(--json-primary-light, rgba(232,76,111,0.06));
  color: var(--json-text-secondary, #6B6B6B); font-family: inherit;
}

/* Empty state */
.tree-empty {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; padding: 32px;
}
.empty-icon { width: 56px; height: 56px; margin-bottom: 4px; }
.empty-title { font-size: 15px; font-weight: 600; color: var(--json-text, #1A1A1A); }
.empty-desc { font-size: 13px; color: var(--json-text-secondary, #6B6B6B); }
.ghost-btn {
  display: flex; align-items: center; gap: 6px; margin-top: 8px;
  padding: 8px 16px; border: 1px solid var(--json-border, rgba(232,76,111,0.15));
  border-radius: 8px; background: transparent;
  font-size: 13px; color: var(--json-primary, #E84C6F); cursor: pointer;
  font-family: inherit; transition: all 0.15s;
}
.ghost-btn:hover { background: var(--json-primary-light, rgba(232,76,111,0.08)); }

/* Tree */
.tree-view {
  flex: 1; overflow-y: auto; padding: 8px 0;
  font-family: var(--font-mono);
  font-size: 13px; line-height: 1.8;
  background:
    repeating-linear-gradient(to right,
      transparent 8px, transparent 27px,
      var(--json-border, rgba(232,76,111,0.06)) 27px,
      transparent 28px
    );
}
.tree-node {
  display: flex; align-items: center; cursor: default; border-radius: 3px;
  transition: background 0.1s; padding-right: 8px;
}
.tree-node:hover { background: var(--json-primary-light, rgba(232,76,111,0.04)); }
.toggle {
  width: 16px; flex-shrink: 0; cursor: pointer;
  color: var(--json-text-secondary, #6B6B6B); display: inline-flex; align-items: center; justify-content: center;
}
.toggle svg { transition: transform 0.15s; }
.toggle svg.rotated { transform: rotate(90deg); }
.nk { color: var(--json-primary, #E84C6F); margin-right: 4px; cursor: context-menu; }
.ns { color: var(--json-gray, #999); }
.nv { cursor: context-menu; word-break: break-all; }
.nb { color: var(--json-gray, #999); font-size: 12px; margin-left: 4px; }

/* Context menu */
.ctx {
  position: fixed; z-index: 9999;
  background: var(--json-surface, #FAFAFA);
  border: 1px solid var(--json-border, rgba(232,76,111,0.15));
  border-radius: 10px; padding: 5px; min-width: 110px;
  box-shadow: var(--shadow-popover);
}
.mi {
  padding: 7px 12px; font-size: 13px;
  color: var(--json-text, #1A1A1A); border-radius: 6px; cursor: pointer;
  transition: background 0.1s;
}
.mi:hover { background: var(--json-primary-light, rgba(232,76,111,0.08)); color: var(--json-primary, #E84C6F); }
</style>
