<script setup lang="ts">
/**
 * TodoView.vue — 待办清单
 * 快速添加（标题/优先级/截止日期/备注）+ 筛选（全部/进行中/已完成/今天/逾期）
 * + 行内完成切换 + 编辑弹窗 + 桌面通知提醒（打开页面时检查逾期/今日到期）
 */
import { ref, computed, watch, onMounted } from 'vue'
import {
  NInput, NSelect, NButton, NIcon, NCheckbox, useMessage, NPopconfirm,
  NTag, NEmpty, NSpin, NModal, NDatePicker,
} from 'naive-ui'
import { Plus, Trash2, Pencil, Calendar, Bell } from 'lucide-vue-next'
import axios from 'axios'

interface Todo {
  id: number
  title: string
  note?: string | null
  priority: number
  dueDate?: string | null
  completed: number
  completedAt?: string | null
  createTime?: string | null
}

const props = defineProps<{ refresh: number }>()
const message = useMessage()

const todos = ref<Todo[]>([])
const filter = ref<'all' | 'active' | 'completed' | 'today' | 'overdue'>('all')
const loading = ref(false)

// 概览统计：进行中 / 今日到期 / 已逾期
const stats = ref({ active: 0, dueToday: 0, overdue: 0 })

const fetchTodos = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/todos', { params: { filter: filter.value } })
    if (res.data.code === 200) todos.value = res.data.data || []
    else message.error(res.data.message || '加载失败')
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '加载失败')
  } finally {
    loading.value = false
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
const newNote = ref('')
const showNoteInput = ref(false)
const adding = ref(false)

const addTodo = async () => {
  if (!newTitle.value.trim()) { message.warning('请输入待办内容'); return }
  adding.value = true
  try {
    const payload: any = {
      title: newTitle.value.trim(),
      priority: newPriority.value,
    }
    if (newDue.value) payload.dueDate = new Date(newDue.value).toISOString().slice(0, 10)
    if (newNote.value.trim()) payload.note = newNote.value.trim()
    const res = await axios.post('/api/todos', payload)
    if (res.data.code === 200) {
      message.success('已添加')
      newTitle.value = ''
      newDue.value = null
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

/* ─── 完成切换 ─── */
const toggleComplete = async (t: Todo) => {
  const next = t.completed === 1 ? false : true
  try {
    const res = await axios.put(`/api/todos/${t.id}/complete`, { completed: next })
    if (res.data.code === 200) {
      t.completed = next ? 1 : 0
      fetchStats()
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '操作失败')
  }
}

/* ─── 删除 ─── */
const removeTodo = async (t: Todo) => {
  try {
    const res = await axios.delete(`/api/todos/${t.id}`)
    if (res.data.code === 200) {
      message.success('已删除')
      todos.value = todos.value.filter(x => x.id !== t.id)
      fetchStats()
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '删除失败')
  }
}

/* ─── 编辑弹窗 ─── */
const editing = ref<Todo | null>(null)
const editForm = ref({ title: '', note: '', priority: 1, due: null as number | null })
const savingEdit = ref(false)

const openEdit = (t: Todo) => {
  editing.value = t
  editForm.value = {
    title: t.title,
    note: t.note || '',
    priority: t.priority ?? 1,
    due: t.dueDate ? new Date(t.dueDate + 'T00:00:00').getTime() : null,
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
    const dueStr = editForm.value.due ? new Date(editForm.value.due).toISOString().slice(0, 10) : null
    if (dueStr !== (editing.value.dueDate || null)) payload.dueDate = dueStr
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

/* ─── 展示辅助 ─── */
const today = new Date()
const todayStr = today.toISOString().slice(0, 10)

const PRIORITY_META: Record<number, { label: string; type: 'error' | 'warning' | 'default' }> = {
  2: { label: '高', type: 'error' },
  1: { label: '中', type: 'warning' },
  0: { label: '低', type: 'default' },
}

const isOverdue = (t: Todo) => t.completed === 0 && !!t.dueDate && t.dueDate < todayStr
const isToday = (t: Todo) => !!t.dueDate && t.dueDate === todayStr

function fmtDue(d: string | null | undefined): string {
  if (!d) return ''
  const [, m, day] = d.split('-')
  return `${Number(m)}月${Number(day)}日`
}

const filterOptions: Array<{ label: string; value: 'all' | 'active' | 'completed' | 'today' | 'overdue' }> = [
  { label: '全部', value: 'all' },
  { label: '进行中', value: 'active' },
  { label: '已完成', value: 'completed' },
  { label: '今天', value: 'today' },
  { label: '已逾期', value: 'overdue' },
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
        <NButton size="tiny" quaternary @click="showNoteInput = !showNoteInput">
          {{ showNoteInput ? '收起备注' : '+ 备注' }}
        </NButton>
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
    <div class="filter-bar">
      <button
        v-for="f in filterOptions" :key="f.value"
        :class="['filter-chip', 'jnclub-bouncy', { 'filter-chip-active': filter === f.value }]"
        @click="filter = f.value; fetchTodos()"
      >{{ f.label }}</button>
    </div>

    <!-- 列表 -->
    <NSpin :show="loading" class="todo-spin">
      <div v-if="!todos.length && !loading" class="todo-empty">
        <NEmpty :description="emptyText" class="todo-empty-inner" />
      </div>
      <div v-else class="todo-list">
        <div
          v-for="t in todos" :key="t.id"
          :class="['todo-item', 'jnclub-bouncy', { 'todo-done': t.completed === 1, 'todo-overdue': isOverdue(t) }]"
        >
          <NCheckbox :checked="t.completed === 1" class="todo-check" @update:checked="() => toggleComplete(t)" />
          <div class="todo-main">
            <div class="todo-title-row">
              <span class="todo-title">{{ t.title }}</span>
              <NTag size="tiny" round :bordered="false" :type="PRIORITY_META[t.priority ?? 1]?.type || 'default'" class="todo-priority">
                {{ PRIORITY_META[t.priority ?? 1]?.label || '中' }}
              </NTag>
              <span v-if="t.dueDate" :class="['todo-due', { 'due-overdue': isOverdue(t), 'due-today': isToday(t) }]">
                <NIcon :component="Calendar" size="12" />
                {{ fmtDue(t.dueDate) }}{{ isOverdue(t) ? ' · 已逾期' : isToday(t) ? ' · 今天' : '' }}
              </span>
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
        </div>
      </div>
    </NSpin>

    <!-- 编辑弹窗 -->
    <NModal
      :show="!!editing"
      @update:show="(v: boolean) => !v && (editing = null)"
      preset="card"
      :style="{ width: '520px', maxWidth: '92vw' }"
      title="编辑待办"
    >
      <div class="edit-form">
        <div class="edit-field">
          <label class="edit-label">内容</label>
          <NInput v-model:value="editForm.title" placeholder="待办内容" clearable />
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
.todo-stat.stat-warn b { color: #f0a13a; }
.todo-stat.stat-danger b { color: #ef5b6b; }
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
  gap: 10px;
  padding: 12px 14px;
  background: var(--glass-bg-trans);
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-md);
  transition: border-color var(--dur) var(--ease), opacity var(--dur) var(--ease);
}
.todo-item:hover { border-color: var(--brand); }
.todo-item.todo-overdue { border-left: 3px solid #ef5b6b; }
.todo-item.todo-done { opacity: 0.55; }
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
.todo-priority { flex-shrink: 0; }
.todo-due {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  flex-shrink: 0;
}
.todo-due.due-overdue { color: #ef5b6b; }
.todo-due.due-today { color: #f0a13a; }
.todo-note {
  font-size: var(--fs-sm);
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
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
.todo-del-btn:hover { color: #ef5b6b; }

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
}
</style>
