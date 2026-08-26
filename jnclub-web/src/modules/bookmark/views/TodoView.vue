<script setup lang="ts">
/**
 * TodoView.vue — 待办清单 2.0
 * 快速添加（标题/优先级/截止日期/截止时间/提醒/重复）+ 筛选
 * （全部/进行中/已完成/今天/已逾期/明天/未来7天/无日期/高优先级）
 * + 行内完成切换 + 子任务 + 编辑弹窗 + 桌面通知
 */
import { ref, computed, watch, onMounted, nextTick, h } from 'vue'
import {
  NInput, NSelect, NButton, NIcon, NCheckbox, useMessage, NPopconfirm,
  NTag, NModal, NDatePicker, NTimePicker, NInputNumber,
} from 'naive-ui'
import { Plus, Trash2, Pencil, Calendar, Bell, ChevronDown, ChevronRight, ListChecks, Repeat2 } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import axios from 'axios'
import JSkeletonList from '../../../shared/components/ui/JSkeletonList.vue'
import JFilterBar from '../../../shared/components/ui/JFilterBar.vue'
import JEmptyState from '../../../shared/components/ui/JEmptyState.vue'
import JErrorState from '../../../shared/components/ui/JErrorState.vue'
import { parseTodoNlp } from '../../../shared/utils/todoNlp'
import { useRecentItems } from '../../../shared/composables/useRecentItems'

interface TodoItem {
  id: number
  todoId?: number
  title: string
  completed: number
  sortOrder?: number
  createTime?: string | null
  updateTime?: string | null
}

interface Todo {
  id: number
  title: string
  note?: string | null
  priority: number
  dueDate?: string | null
  dueTime?: string | null
  remindAt?: string | null
  remindNotified?: number
  recurrence?: string | null
  recurrenceInterval?: number
  completed: number
  completedAt?: string | null
  items?: TodoItem[] | null
  itemCount?: number
  itemCompletedCount?: number
  createTime?: string | null
}

/** API 使用 ISO 日期，必须按本地时区补零，避免 toISOString 在东八区跨日/凌晨变成前一天 */
const toDateStr = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const toDateTimeStr = (d: Date) =>
  `${toDateStr(d)} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`

const parseDateTime = (s?: string | null): number | null => {
  if (!s) return null
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/)
  if (!m) return null
  const [, y, mo, d, hh, mi, ss = '0'] = m
  return new Date(Number(y), Number(mo) - 1, Number(d), Number(hh), Number(mi), Number(ss)).getTime()
}

const props = defineProps<{ refresh: number }>()
const message = useMessage()
const route = useRoute()
const { record: recordRecentItem } = useRecentItems()

const todos = ref<Todo[]>([])
const highlightTodoId = ref<number | null>(null)
const filter = ref<'all' | 'active' | 'completed' | 'today' | 'overdue' | 'tomorrow' | 'week' | 'noDate' | 'high'>('all')
const loading = ref(false)
const loadError = ref(false)

// 概览统计：进行中 / 今日到期 / 已逾期
const stats = ref({ active: 0, dueToday: 0, overdue: 0 })

const fetchTodos = async () => {
  loading.value = true
  loadError.value = false
  try {
    const res = await axios.get('/api/todos', { params: { filter: filter.value } })
    if (res.data.code === 200) todos.value = res.data.data || []
    else {
      loadError.value = true
      message.error(res.data.message || '加载失败')
    }
  } catch (e: any) {
    loadError.value = true
    message.error(e.response?.data?.message || e.message || '加载失败')
  } finally {
    loading.value = false
    const q = route.query.highlight
    if (q) {
      highlightTodoId.value = Number(q)
      await nextTick()
      document.querySelector('.todo-highlight')?.scrollIntoView({ block: 'center' })
    }
  }
}

const fetchStats = async () => {
  try {
    const res = await axios.get('/api/stats/summary')
    if (res.data.code === 200 && res.data.data?.todos) {
      stats.value = res.data.data.todos
    }
  } catch { /* 静默 */ }
}

const reload = () => { fetchTodos(); fetchStats() }

watch(() => props.refresh, reload)
onMounted(() => { reload(); checkReminders() })

/* ─── 快速添加 ─── */
const newTitle = ref('')
const newPriority = ref(1)
const newDue = ref<number | null>(null) // naive date picker 时间戳
const newDueTime = ref<string | null>(null)
const newRemindAt = ref<number | null>(null)
const newRecurrence = ref('')
const newRecurrenceInterval = ref(1)
const newNote = ref('')
const showNoteInput = ref(false)
const adding = ref(false)

const recurrenceOptions = [
  { label: '不重复', value: '' },
  { label: '每天', value: 'DAILY' },
  { label: '每周', value: 'WEEKLY' },
  { label: '每月', value: 'MONTHLY' },
  { label: '每年', value: 'YEARLY' },
]

const addTodo = async () => {
  if (!newTitle.value.trim()) { message.warning('请输入待办内容'); return }
  adding.value = true
  try {
    const parsed = parseTodoNlp(newTitle.value)
    const payload: any = {
      title: parsed.title || newTitle.value.trim(),
      priority: parsed.priority || newPriority.value,
    }
    if (parsed.dueDate || newDue.value) payload.dueDate = parsed.dueDate || toDateStr(new Date(newDue.value!))
    if (parsed.dueTime || newDueTime.value) payload.dueTime = parsed.dueTime || newDueTime.value
    if (parsed.remindAt || newRemindAt.value) payload.remindAt = parsed.remindAt || toDateTimeStr(new Date(newRemindAt.value!))
    const recurrence = parsed.recurrence || newRecurrence.value
    if (recurrence) {
      payload.recurrence = recurrence
      payload.recurrenceInterval = Math.max(1, newRecurrenceInterval.value || 1)
    }
    if (newNote.value.trim()) payload.note = newNote.value.trim()
    const res = await axios.post('/api/todos', payload)
    if (res.data.code === 200) {
      message.success('已添加')
      newTitle.value = ''
      newDue.value = null
      newDueTime.value = null
      newRemindAt.value = null
      newRecurrence.value = ''
      newRecurrenceInterval.value = 1
      newNote.value = ''
      showNoteInput.value = false
      reload()
    } else {
      message.error(res.data.message || '添加失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '添加失败')
  } finally {
    adding.value = false
  }
}

/* ─── 完成切换（乐观更新，失败回滚） ─── */
const toggleComplete = async (t: Todo) => {
  const next = t.completed === 1 ? false : true
  const prev = t.completed
  t.completed = next ? 1 : 0
  fetchStats()
  try {
    const res = await axios.put(`/api/todos/${t.id}/complete`, { completed: next })
    if (res.data.code !== 200) throw new Error(res.data.message || '操作失败')
    // 重复待办完成后后端会自动推进到下个周期并保持进行中，需要重新拉取
    if (t.recurrence) reload()
  } catch (e: any) {
    t.completed = prev
    fetchStats()
    message.error(e.response?.data?.message || e.message || '操作失败')
  }
}

/* ─── 删除（乐观移除 + 可撤销） ─── */
const removeTodo = async (t: Todo) => {
  const prev = todos.value
  todos.value = todos.value.filter(x => x.id !== t.id)
  fetchStats()
  try {
    const res = await axios.delete(`/api/todos/${t.id}`)
    if (res.data.code !== 200) throw new Error(res.data.message || '删除失败')
    message.success('', {
      duration: 6000,
      render: () => h('div', { style: 'display:flex;align-items:center;gap:12px;' }, [
        h('span', '已删除'),
        h('a', {
          style: 'cursor:pointer;color:var(--brand);font-weight:600;',
          onClick: async () => {
            try {
              await axios.post('/api/todos', {
                title: t.title,
                note: t.note || '',
                priority: t.priority ?? 1,
                dueDate: t.dueDate || null,
                dueTime: t.dueTime || null,
                remindAt: t.remindAt || null,
                recurrence: t.recurrence || null,
                recurrenceInterval: t.recurrenceInterval || 1,
              })
              message.success('已恢复')
              reload()
            } catch {
              message.error('恢复失败')
            }
          },
        }, '撤销'),
      ]),
    })
  } catch (e: any) {
    todos.value = prev
    fetchStats()
    message.error(e.response?.data?.message || e.message || '删除失败')
  }
}

/* ─── 编辑弹窗 ─── */
const editing = ref<Todo | null>(null)
const editForm = ref({
  title: '',
  note: '',
  priority: 1,
  due: null as number | null,
  dueTime: null as string | null,
  remindAt: null as number | null,
  recurrence: '',
  recurrenceInterval: 1,
})
const savingEdit = ref(false)

const openEdit = (t: Todo) => {
  // 编辑待办 → 记入「最近打开」
  recordRecentItem({ kind: 'todo', id: t.id, title: t.title || '未命名待办' })
  editing.value = t
  editForm.value = {
    title: t.title,
    note: t.note || '',
    priority: t.priority ?? 1,
    due: t.dueDate ? new Date(t.dueDate + 'T00:00:00').getTime() : null,
    dueTime: t.dueTime || null,
    remindAt: parseDateTime(t.remindAt),
    recurrence: t.recurrence || '',
    recurrenceInterval: t.recurrenceInterval ?? 1,
  }
}

const saveEdit = async () => {
  if (!editing.value) return
  if (!editForm.value.title.trim()) { message.warning('标题不能为空'); return }
  savingEdit.value = true
  try {
    const payload: any = { title: editForm.value.title.trim() }
    if (editForm.value.note !== (editing.value.note || '')) payload.note = editForm.value.note
    if (editForm.value.priority !== (editing.value.priority ?? 1)) payload.priority = editForm.value.priority
    const dueStr = editForm.value.due ? toDateStr(new Date(editForm.value.due)) : null
    if (dueStr !== (editing.value.dueDate || null)) {
      payload.dueDate = dueStr
      if (dueStr === null) payload.clearDueDate = true
    }
    const dueTimeStr = editForm.value.dueTime || null
    if (dueTimeStr !== (editing.value.dueTime || null)) {
      payload.dueTime = dueTimeStr
      if (dueTimeStr === null) payload.clearDueTime = true
    }
    const remindStr = editForm.value.remindAt ? toDateTimeStr(new Date(editForm.value.remindAt)) : null
    if (remindStr !== (editing.value.remindAt || null)) {
      payload.remindAt = remindStr
      if (remindStr === null) payload.clearRemindAt = true
    }
    const recurrence = editForm.value.recurrence || ''
    if (recurrence !== (editing.value.recurrence || '')) {
      payload.recurrence = recurrence
      if (recurrence) payload.recurrenceInterval = Math.max(1, editForm.value.recurrenceInterval || 1)
    }
    const res = await axios.put(`/api/todos/${editing.value.id}`, payload)
    if (res.data.code === 200) {
      message.success('已保存')
      editing.value = null
      reload()
    } else {
      message.error(res.data.message || '保存失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '保存失败')
  } finally {
    savingEdit.value = false
  }
}

/* ─── 子任务 ─── */
const expandedIds = ref<number[]>([])
const itemLoadingId = ref<number | null>(null)
const addingItemId = ref<number | null>(null)
const newItemTexts = ref<Record<number, string>>({})
const editingItemId = ref<number | null>(null)
const editingItemText = ref('')

const isExpanded = (id: number) => expandedIds.value.includes(id)

const toggleExpand = async (t: Todo) => {
  if (isExpanded(t.id)) {
    expandedIds.value = expandedIds.value.filter(id => id !== t.id)
    return
  }
  // 展开待办查看子任务 → 记入「最近打开」
  recordRecentItem({ kind: 'todo', id: t.id, title: t.title || '未命名待办' })
  if (!t.items) {
    itemLoadingId.value = t.id
    try {
      const res = await axios.get(`/api/todos/${t.id}/items`)
      if (res.data.code === 200) {
        t.items = res.data.data || []
      } else {
        message.error(res.data.message || '子任务加载失败')
      }
    } catch (e: any) {
      message.error(e.response?.data?.message || e.message || '子任务加载失败')
    } finally {
      itemLoadingId.value = null
    }
  }
  expandedIds.value = [...expandedIds.value, t.id]
}

const addItem = async (t: Todo) => {
  const title = (newItemTexts.value[t.id] || '').trim()
  if (!title) { message.warning('请输入子任务内容'); return }
  addingItemId.value = t.id
  try {
    const res = await axios.post(`/api/todos/${t.id}/items`, { title })
    if (res.data.code === 200) {
      if (!t.items) t.items = []
      t.items.push(res.data.data)
      t.itemCount = (t.itemCount || 0) + 1
      newItemTexts.value[t.id] = ''
    } else {
      message.error(res.data.message || '添加子任务失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '添加子任务失败')
  } finally {
    addingItemId.value = null
  }
}

const adjustItemCounts = (t: Todo, oldCompleted: number, newCompleted: number) => {
  if (t.itemCompletedCount != null) t.itemCompletedCount += newCompleted - oldCompleted
}

const toggleItemComplete = async (t: Todo, item: TodoItem) => {
  const next = item.completed === 1 ? false : true
  const prev = item.completed
  item.completed = next ? 1 : 0
  adjustItemCounts(t, prev, next ? 1 : 0)
  try {
    const res = await axios.put(`/api/todos/${t.id}/items/${item.id}/complete`, { completed: next })
    if (res.data.code !== 200) throw new Error(res.data.message || '操作失败')
  } catch (e: any) {
    item.completed = prev
    adjustItemCounts(t, next ? 1 : 0, prev)
    message.error(e.response?.data?.message || e.message || '操作失败')
  }
}

const startEditItem = (item: TodoItem) => {
  editingItemId.value = item.id
  editingItemText.value = item.title
}

const saveEditItem = async (t: Todo, item: TodoItem) => {
  const title = editingItemText.value.trim()
  if (!title) { editingItemId.value = null; return }
  try {
    const res = await axios.put(`/api/todos/${t.id}/items/${item.id}`, { title })
    if (res.data.code === 200) {
      item.title = title
      editingItemId.value = null
    } else {
      message.error(res.data.message || '保存子任务失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '保存子任务失败')
  }
}

const removeItem = async (t: Todo, item: TodoItem) => {
  if (t.items) t.items = t.items.filter(x => x.id !== item.id)
  t.itemCount = Math.max(0, (t.itemCount || 0) - 1)
  if (item.completed === 1) t.itemCompletedCount = Math.max(0, (t.itemCompletedCount || 0) - 1)
  try {
    const res = await axios.delete(`/api/todos/${t.id}/items/${item.id}`)
    if (res.data.code !== 200) {
      message.error(res.data.message || '删除子任务失败')
      reload()
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '删除子任务失败')
    reload()
  }
}

/* ─── 展示辅助 ─── */
const todayStr = toDateStr(new Date())

const PRIORITY_META: Record<number, { label: string; type: 'error' | 'warning' | 'default' }> = {
  2: { label: '高', type: 'error' },
  1: { label: '中', type: 'warning' },
  0: { label: '低', type: 'default' },
}

const REPEAT_META: Record<string, string> = {
  DAILY: '每天',
  WEEKLY: '每周',
  MONTHLY: '每月',
  YEARLY: '每年',
}

const recurrenceLabel = (r?: string | null) => (r ? REPEAT_META[r] || r : '')

const isOverdue = (t: Todo) => t.completed === 0 && !!t.dueDate && t.dueDate < todayStr
const isToday = (t: Todo) => !!t.dueDate && t.dueDate === todayStr

function fmtDue(d: string | null | undefined): string {
  if (!d) return ''
  const [, m, day] = d.split('-')
  return `${Number(m)}月${Number(day)}日`
}

function fmtRemind(s: string | null | undefined): string {
  if (!s) return ''
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  if (!m) return s
  return `${Number(m[2])}月${Number(m[3])}日 ${m[4]}:${m[5]}`
}

const filterOptions: Array<{ label: string; value: 'all' | 'active' | 'completed' | 'today' | 'overdue' | 'tomorrow' | 'week' | 'noDate' | 'high' }> = [
  { label: '全部', value: 'all' },
  { label: '进行中', value: 'active' },
  { label: '已完成', value: 'completed' },
  { label: '今天', value: 'today' },
  { label: '已逾期', value: 'overdue' },
  { label: '明天', value: 'tomorrow' },
  { label: '未来7天', value: 'week' },
  { label: '无日期', value: 'noDate' },
  { label: '高优先级', value: 'high' },
]

const priorityOptions = [
  { label: '高', value: 2 },
  { label: '中', value: 1 },
  { label: '低', value: 0 },
]

/* ─── 桌面通知 ─── */
const checkReminders = async () => {
  if (!('Notification' in window) || Notification.permission !== 'granted') return
  if (stats.value.overdue > 0 || stats.value.dueToday > 0) {
    new Notification('JNClub 待办提醒', {
      body: `已逾期 ${stats.value.overdue} 条 · 今日到期 ${stats.value.dueToday} 条`,
    })
  }
}

const requestNotify = async () => {
  if (!('Notification' in window)) { message.warning('当前浏览器不支持桌面通知'); return }
  try {
    if (Notification.permission === 'default') {
      const p = await Notification.requestPermission()
      if (p === 'granted') { message.success('已开启桌面通知'); checkReminders() }
      else message.warning('未授权桌面通知')
    } else if (Notification.permission === 'granted') {
      message.success('桌面通知已开启')
      checkReminders()
    } else {
      message.warning('通知被拒绝，请在浏览器站点设置中允许')
    }
  } catch {
    message.warning('通知授权失败')
  }
}

const emptyText = computed(() => {
  switch (filter.value) {
    case 'active': return '没有进行中的待办'
    case 'completed': return '还没有完成的待办'
    case 'today': return '今天没有待办'
    case 'overdue': return '没有已逾期的待办'
    case 'tomorrow': return '明天没有待办'
    case 'week': return '未来 7 天没有待办'
    case 'noDate': return '没有无日期的待办'
    case 'high': return '没有高优先级待办'
    default: return '还没有待办，输入一条开始吧'
  }
})
</script>

<template>
  <div class="todo-view">
    <!-- 概览统计条 -->
    <div class="todo-stats">
      <div class="todo-stat"><b>{{ stats.active }}</b><span>进行中</span></div>
      <div class="todo-stat" :class="{ 'stat-warn': stats.dueToday > 0 }"><b>{{ stats.dueToday }}</b><span>今日到期</span></div>
      <div class="todo-stat" :class="{ 'stat-danger': stats.overdue > 0 }"><b>{{ stats.overdue }}</b><span>已逾期</span></div>
      <div class="stat-spacer" />
      <NButton size="tiny" quaternary class="notify-btn" @click="requestNotify">
        <template #icon><NIcon :component="Bell" size="14" /></template>
        桌面提醒
      </NButton>
    </div>

    <!-- 快速添加 -->
    <div class="quick-add glass-card--modal">
      <div class="quick-add-row">
        <NInput
          v-model:value="newTitle"
          placeholder="添加一条待办…"
          clearable
          size="medium"
          class="quick-title"
          @keyup.enter="addTodo"
        />
        <NSelect v-model:value="newPriority" :options="priorityOptions" size="medium" class="quick-priority" />
        <NDatePicker v-model:value="newDue" type="date" size="medium" class="quick-due" clearable placeholder="截止" />
        <NButton size="medium" type="primary" secondary class="quick-add-btn" :loading="adding" @click="addTodo">
          <template #icon><NIcon :component="Plus" size="16" /></template>
          添加
        </NButton>
      </div>
      <div class="quick-add-sub">
        <div class="quick-extra-row">
          <NTimePicker
            v-model:formatted-value="newDueTime"
            format="HH:mm:ss"
            clearable
            placeholder="截止时间"
            size="small"
            class="quick-time"
          />
          <NDatePicker
            v-model:value="newRemindAt"
            type="datetime"
            clearable
            placeholder="提醒时间"
            size="small"
            class="quick-remind"
          />
          <NSelect
            v-model:value="newRecurrence"
            :options="recurrenceOptions"
            size="small"
            clearable
            placeholder="重复"
            class="quick-recur"
          />
          <NInputNumber
            v-if="newRecurrence"
            v-model:value="newRecurrenceInterval"
            :min="1"
            :max="365"
            size="small"
            class="quick-interval"
            placeholder="间隔"
          />
          <NButton size="tiny" quaternary @click="showNoteInput = !showNoteInput">
            {{ showNoteInput ? '收起备注' : '+ 备注' }}
          </NButton>
        </div>
        <NInput
          v-if="showNoteInput"
          v-model:value="newNote"
          type="textarea"
          :rows="2"
          placeholder="备注（可选）"
          class="quick-note"
        />
      </div>
    </div>

    <!-- 筛选 -->
    <JFilterBar
      class="filter-bar"
      :items="filterOptions.map(f => ({ label: f.label, value: f.value }))"
      :model-value="filter"
      @select="(v: string | number | null) => { filter = (v ?? 'all') as typeof filter; fetchTodos() }"
    />

    <!-- 列表 -->
    <div class="todo-spin">
      <JSkeletonList v-if="loading" />
      <template v-else>
        <JErrorState
          v-if="loadError"
          message="待办加载失败"
          hint="请检查网络后重试"
          @retry="fetchTodos"
        />
        <div v-else-if="!todos.length" class="todo-empty">
          <JEmptyState :message="emptyText" hint="添加一条待办，开始规划今天" />
        </div>
        <div v-else class="todo-list">
          <div
            v-for="t in todos" :key="t.id"
            :class="['todo-item', 'jnclub-bouncy', 'prio-' + (t.priority ?? 1), { 'todo-done': t.completed === 1, 'todo-overdue': isOverdue(t), 'todo-highlight': highlightTodoId === t.id }]"
          >
            <NCheckbox :checked="t.completed === 1" class="todo-check" @update:checked="() => toggleComplete(t)" />
            <div class="todo-main">
              <div class="todo-title-row">
                <span class="todo-title">{{ t.title }}</span>
                <NTag size="tiny" round :bordered="false" :type="PRIORITY_META[t.priority ?? 1]?.type || 'default'" class="todo-priority">
                  {{ PRIORITY_META[t.priority ?? 1]?.label || '中' }}
                </NTag>
                <NTag v-if="t.recurrence" size="tiny" round :bordered="false" type="info" class="todo-recur">
                  <NIcon :component="Repeat2" size="11" /> {{ recurrenceLabel(t.recurrence) }}
                </NTag>
                <span v-if="t.dueDate" :class="['todo-due', { 'due-overdue': isOverdue(t), 'due-today': isToday(t) }]">
                  <NIcon :component="Calendar" size="12" />
                  {{ fmtDue(t.dueDate) }}{{ t.dueTime ? ' ' + t.dueTime.slice(0, 5) : '' }}{{ isOverdue(t) ? ' · 已逾期' : isToday(t) ? ' · 今天' : '' }}
                </span>
                <span v-if="t.remindAt" class="todo-remind">
                  <NIcon :component="Bell" size="12" />
                  {{ fmtRemind(t.remindAt) }}
                </span>
                <span
                  v-if="t.itemCount != null && t.itemCount > 0"
                  class="todo-items-toggle"
                  @click="toggleExpand(t)"
                >
                  <NIcon :component="isExpanded(t.id) ? ChevronDown : ChevronRight" size="13" />
                  <NIcon :component="ListChecks" size="13" />
                  {{ t.itemCompletedCount || 0 }}/{{ t.itemCount }}
                </span>
              </div>
              <div v-if="t.itemCount != null && t.itemCount > 0" class="todo-progress" aria-hidden="true">
                <div class="todo-progress-bar" :style="{ width: ((t.itemCompletedCount || 0) / t.itemCount * 100) + '%' }" />
              </div>
              <div v-if="t.note" class="todo-note">{{ t.note }}</div>
            </div>
            <div class="todo-actions">
              <NButton quaternary circle size="small" title="编辑" @click="openEdit(t)">
                <template #icon><NIcon :component="Pencil" size="14" /></template>
              </NButton>
              <NPopconfirm @positive-click="removeTodo(t)">
                <template #trigger>
                  <NButton quaternary circle size="small" title="删除" class="todo-del-btn">
                    <template #icon><NIcon :component="Trash2" size="14" /></template>
                  </NButton>
                </template>
                确定删除这条待办？
              </NPopconfirm>
            </div>

            <!-- 子任务展开区 -->
            <div v-if="isExpanded(t.id)" class="todo-subitems">
              <div v-if="itemLoadingId === t.id" class="sub-loading">子任务加载中…</div>
              <template v-else>
                <div v-if="t.items?.length" class="sub-list">
                  <div v-for="item in t.items" :key="item.id" class="sub-item">
                    <NCheckbox :checked="item.completed === 1" size="small" @update:checked="() => toggleItemComplete(t, item)" />
                    <template v-if="editingItemId === item.id">
                      <NInput
                        v-model:value="editingItemText"
                        size="small"
                        class="sub-edit-input"
                        @keyup.enter="saveEditItem(t, item)"
                        @blur="saveEditItem(t, item)"
                      />
                    </template>
                    <span v-else :class="['sub-title', { 'sub-done': item.completed === 1 }]">{{ item.title }}</span>
                    <div class="sub-actions">
                      <NButton quaternary circle size="tiny" title="编辑子任务" @click="startEditItem(item)">
                        <template #icon><NIcon :component="Pencil" size="12" /></template>
                      </NButton>
                      <NButton quaternary circle size="tiny" title="删除子任务" class="todo-del-btn" @click="removeItem(t, item)">
                        <template #icon><NIcon :component="Trash2" size="12" /></template>
                      </NButton>
                    </div>
                  </div>
                </div>
                <div v-else class="sub-empty">还没有子任务</div>
                <div class="sub-add">
                  <NInput
                    v-model:value="newItemTexts[t.id]"
                    size="small"
                    placeholder="添加子任务，回车确认"
                    @keyup.enter="addItem(t)"
                  />
                  <NButton size="small" secondary type="primary" :loading="addingItemId === t.id" @click="addItem(t)">
                    添加
                  </NButton>
                </div>
              </template>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 编辑弹窗 -->
    <NModal
      :show="!!editing"
      @update:show="(v: boolean) => !v && (editing = null)"
      preset="card"
      :style="{ width: '560px', maxWidth: '92vw' }"
      title="编辑待办"
    >
      <div class="edit-form">
        <div class="edit-field">
          <label class="edit-label">内容</label>
          <NInput v-model:value="editForm.title" placeholder="待办内容" clearable autofocus />
        </div>
        <div class="edit-grid">
          <div class="edit-field">
            <label class="edit-label">优先级</label>
            <NSelect v-model:value="editForm.priority" :options="priorityOptions" />
          </div>
          <div class="edit-field">
            <label class="edit-label">截止日期</label>
            <NDatePicker v-model:value="editForm.due" type="date" clearable placeholder="不设截止" style="width: 100%" />
          </div>
          <div class="edit-field">
            <label class="edit-label">截止时间</label>
            <NTimePicker v-model:formatted-value="editForm.dueTime" format="HH:mm:ss" clearable placeholder="不设截止时间" style="width: 100%" />
          </div>
          <div class="edit-field">
            <label class="edit-label">提醒时间</label>
            <NDatePicker v-model:value="editForm.remindAt" type="datetime" clearable placeholder="不设提醒" style="width: 100%" />
          </div>
          <div class="edit-field">
            <label class="edit-label">重复</label>
            <NSelect v-model:value="editForm.recurrence" :options="recurrenceOptions" clearable placeholder="不重复" />
          </div>
          <div class="edit-field">
            <label class="edit-label">重复间隔</label>
            <NInputNumber v-model:value="editForm.recurrenceInterval" :min="1" :max="365" :disabled="!editForm.recurrence" style="width: 100%" />
          </div>
        </div>
        <div class="edit-field">
          <label class="edit-label">备注</label>
          <NInput v-model:value="editForm.note" type="textarea" :rows="3" placeholder="备注（可选）" />
        </div>
      </div>
      <template #footer>
        <div class="edit-foot">
          <NButton size="small" quaternary @click="editing = null">取消</NButton>
          <NButton size="small" type="primary" secondary :loading="savingEdit" @click="saveEdit">保存</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.todo-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 860px;
  margin: 0 auto;
}

/* 统计条 */
.todo-stats {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  background: var(--glass-bg-trans);
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-md);
}
.todo-stat {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.todo-stat b {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-1);
}
.todo-stat.stat-warn b { color: var(--warning-text); }
.todo-stat.stat-danger b { color: var(--danger); }
.stat-spacer { flex: 1; }
.notify-btn { color: var(--text-2); }

/* 快速添加 */
.quick-add {
  padding: 14px;
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.quick-add-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.quick-title { flex: 1; }
.quick-priority { width: 88px; }
.quick-due { width: 148px; }
.quick-add-sub { display: flex; flex-direction: column; gap: 8px; }
.quick-extra-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.quick-time { width: 118px; }
.quick-remind { width: 158px; }
.quick-recur { width: 110px; }
.quick-interval { width: 84px; }
.quick-note { margin-top: 2px; }

/* 筛选 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.filter-chip {
  padding: 5px 14px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--glass-chip-border);
  background: var(--glass-chip-bg);
  color: var(--glass-chip-text);
  font-size: var(--fs-sm);
  cursor: pointer;
}
.filter-chip-active {
  background: var(--brand-soft);
  border-color: var(--brand);
  color: var(--brand);
  font-weight: 600;
}

/* 列表 */
.todo-spin { min-height: 120px; }
.todo-empty { padding: 40px 0; }
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.todo-item {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 14px;
  background: var(--glass-bg-trans);
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-md);
  transition: border-color var(--dur) var(--ease), opacity var(--dur) var(--ease);
}
.todo-item:hover { border-color: var(--brand); }
.todo-item.prio-2 { border-left: 3px solid var(--danger); }
.todo-item.prio-2:hover { border-left-color: var(--danger); }
.todo-item.todo-overdue { border-left: 3px solid var(--danger); }
.todo-item.todo-overdue:hover { border-left-color: var(--danger); }
.todo-item.todo-done { opacity: 0.55; }
.todo-item.todo-highlight {
  outline: 2px solid var(--brand);
  outline-offset: 1px;
  animation: todo-highlight-pulse 1.4s ease 2;
}
@keyframes todo-highlight-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: .6; }
}
.todo-check { margin-top: 2px; }
.todo-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.todo-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.todo-title {
  font-size: var(--fs-base);
  color: var(--text-1);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.todo-done .todo-title { text-decoration: line-through; color: var(--text-3); }
.todo-priority { flex-shrink: 0; min-width: 28px; justify-content: center; }
.todo-recur { display: inline-flex; align-items: center; gap: 2px; flex-shrink: 0; }
.todo-due {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--glass-chip-bg);
}
.todo-due.due-overdue {
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 12%, transparent);
}
.todo-due.due-today {
  color: var(--warning-text);
  background: color-mix(in srgb, var(--warning-text) 14%, transparent);
}
.todo-remind {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  flex-shrink: 0;
}
.todo-items-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-xs);
  color: var(--brand);
  cursor: pointer;
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--brand-soft);
  font-weight: 600;
}
.todo-note {
  font-size: var(--fs-sm);
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.todo-progress {
  height: 3px;
  margin-top: 6px;
  border-radius: var(--radius-pill);
  background: var(--glass-chip-bg);
  overflow: hidden;
}
.todo-progress-bar {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--brand), color-mix(in srgb, var(--brand) 60%, var(--warning-text)));
  transition: width 0.3s var(--ease);
}
.todo-done .todo-progress-bar { opacity: 0.6; }
.todo-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0.35;
  transition: opacity var(--dur) var(--ease);
}
.todo-item:hover .todo-actions { opacity: 1; }
.todo-del-btn { color: var(--text-3); }
.todo-del-btn:hover { color: var(--danger); }

/* 子任务 */
.todo-subitems {
  flex-basis: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 0 2px 24px;
  border-top: 1px dashed var(--glass-chip-border);
  margin-top: 2px;
}
.sub-list { display: flex; flex-direction: column; gap: 6px; }
.sub-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
}
.sub-item:hover { background: var(--glass-chip-bg); }
.sub-edit-input { flex: 1; }
.sub-title {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-sm);
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sub-title.sub-done { text-decoration: line-through; color: var(--text-3); }
.sub-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}
.sub-item:hover .sub-actions { opacity: 1; }
.sub-empty { font-size: var(--fs-sm); color: var(--text-3); padding: 4px 6px; }
.sub-loading { font-size: var(--fs-sm); color: var(--text-3); }
.sub-add {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sub-add .n-input { flex: 1; }

/* 编辑弹窗 */
.edit-form { display: flex; flex-direction: column; gap: 12px; }
.edit-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.edit-field { display: flex; flex-direction: column; gap: 6px; }
.edit-label { font-size: var(--fs-sm); color: var(--glass-text-secondary); }
.edit-foot { display: flex; justify-content: flex-end; gap: 10px; }

/* 移动端 */
@media (max-width: 767px) {
  .todo-view { max-width: 100%; }
  .quick-add-row { flex-wrap: wrap; }
  .quick-title { flex: 1 1 100%; }
  .quick-priority { flex: 1; }
  .quick-due { flex: 1; }
  .todo-stats { flex-wrap: wrap; gap: 10px; }
  .quick-time,
  .quick-remind,
  .quick-recur,
  .quick-interval { flex: 1 1 40%; }
  .edit-grid { grid-template-columns: 1fr; }
  .todo-subitems { padding-left: 0; }
}
</style>
