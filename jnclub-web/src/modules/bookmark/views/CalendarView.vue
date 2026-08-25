<script setup lang="ts">
/**
 * CalendarView.vue — 日历月视图
 * 聚合当月待办（dueDate）+ 便签（更新时间）；拖拽待办改期；点击空白格快捷新建待办
 */
import { ref, computed, watch, onMounted } from 'vue'
import { NButton, NIcon, NCheckbox, NInput, NSelect, useMessage, NModal } from 'naive-ui'
import { ChevronLeft, ChevronRight, StickyNote, Trash2 } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import axios from 'axios'
import JSkeletonGrid from '../../../shared/components/ui/JSkeletonGrid.vue'

interface TodoItem {
  id: number
  title: string
  note?: string | null
  priority: number
  completed: number
  dueDate: string | null
}
interface NoteItem {
  id: number
  title: string
  updateTime: string
}

interface DayCell {
  date: Date
  inMonth: boolean
  isToday: boolean
  todos: TodoItem[]
  notes: NoteItem[]
}

const props = defineProps<{ refresh: number }>()
const message = useMessage()
const router = useRouter()

const loading = ref(false)
const cursor = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const todos = ref<TodoItem[]>([])
const overdueTodos = ref<TodoItem[]>([])
const notes = ref<NoteItem[]>([])

const monthTitle = computed(() =>
  `${cursor.value.getFullYear()} 年 ${cursor.value.getMonth() + 1} 月`,
)

const fetchMonth = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/calendar/month', {
      params: { year: cursor.value.getFullYear(), month: cursor.value.getMonth() + 1 },
    })
    if (res.data.code === 200) {
      todos.value = res.data.data?.todos || []
      overdueTodos.value = res.data.data?.overdueTodos || []
      notes.value = res.data.data?.notes || []
    } else {
      message.error(res.data.message || '加载失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '加载失败')
  } finally { loading.value = false }
}

const reload = () => fetchMonth()
watch(() => props.refresh, reload)
onMounted(fetchMonth)

/* ─── 月网格构建 ─── */
const shiftMonth = (delta: number) => {
  cursor.value = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + delta, 1)
  fetchMonth()
}
const goToday = () => {
  cursor.value = new Date(new Date().getFullYear(), new Date().getMonth(), 1)
  fetchMonth()
}

const dayKey = (d: Date) => `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
/** API 使用 ISO 格式，月/日必须补零，否则后端 LocalDate 反序列化 500 */
const fmtDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const byDay = computed(() => {
  const map: Record<string, { todos: TodoItem[]; notes: NoteItem[] }> = {}
  for (const t of todos.value) {
    if (!t.dueDate) continue
    const k = t.dueDate.slice(0, 10)
    ;(map[k] ||= { todos: [], notes: [] }).todos.push(t)
  }
  for (const n of notes.value) {
    if (!n.updateTime) continue
    const d = new Date(n.updateTime)
    const k = dayKey(d)
    ;(map[k] ||= { todos: [], notes: [] }).notes.push(n)
  }
  return map
})

const cells = computed<DayCell[]>(() => {
  const y = cursor.value.getFullYear()
  const m = cursor.value.getMonth()
  const first = new Date(y, m, 1)
  const startWeekday = (first.getDay() + 6) % 7 // 周一为一周起点
  const today = new Date()
  const result: DayCell[] = []
  const start = new Date(y, m, 1 - startWeekday)
  for (let i = 0; i < 42; i++) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)
    const inMonth = d.getMonth() === m
    const isToday = d.getFullYear() === today.getFullYear() && d.getMonth() === today.getMonth() && d.getDate() === today.getDate()
    const key = dayKey(d)
    result.push({
      date: d,
      inMonth,
      isToday,
      todos: byDay.value[key]?.todos || [],
      notes: byDay.value[key]?.notes || [],
    })
  }
  return result
})

/* ─── 待办交互 ─── */
const toggleTodo = async (t: TodoItem) => {
  const next = t.completed === 1 ? false : true
  try {
    const res = await axios.put(`/api/todos/${t.id}/complete`, { completed: next })
    if (res.data.code === 200) {
      t.completed = next ? 1 : 0
    } else message.error(res.data.message || '操作失败')
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '操作失败')
  }
}

/** 拖拽改期：把 todo 拖到目标日期格 */
let dragTodoId: number | null = null
const onTodoDragStart = (e: DragEvent, t: TodoItem) => {
  dragTodoId = t.id
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
const onDayDrop = async (cell: DayCell) => {
  if (!dragTodoId) return
  const targetDate = fmtDate(cell.date)
  try {
    const res = await axios.put(`/api/todos/${dragTodoId}`, { dueDate: targetDate })
    if (res.data.code === 200) {
      message.success('已调整到 ' + targetDate)
      fetchMonth()
    } else message.error(res.data.message || '调整失败')
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '调整失败')
  }
  dragTodoId = null
}

/* ─── 快捷新建（点击空白格） ─── */
const quickDate = ref<string | null>(null)
const quickTitle = ref('')
const quickPriority = ref(1)
const quickShow = ref(false)
const quickAdding = ref(false)

const openQuickAdd = (cell: DayCell) => {
  if (!cell.inMonth) return
  quickDate.value = fmtDate(cell.date)
  quickTitle.value = ''
  quickPriority.value = 1
  quickShow.value = true
}
const submitQuick = async () => {
  if (!quickTitle.value.trim()) { message.warning('请输入待办内容'); return }
  quickAdding.value = true
  try {
    const res = await axios.post('/api/todos', {
      title: quickTitle.value.trim(),
      priority: quickPriority.value,
      dueDate: quickDate.value,
    })
    if (res.data.code === 200) {
      message.success('已添加')
      quickShow.value = false
      fetchMonth()
    } else message.error(res.data.message || '添加失败')
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '添加失败')
  } finally { quickAdding.value = false }
}

const removeTodo = async (t: TodoItem) => {
  try {
    const res = await axios.delete(`/api/todos/${t.id}`)
    if (res.data.code === 200) {
      message.success('已删除')
      fetchMonth()
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '删除失败')
  }
}

const openNote = (n: NoteItem) => router.push(`/notes/${n.id}`)

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
</script>

<template>
  <div class="cal-wrap">
    <!-- 工具栏 -->
    <div class="cal-toolbar">
      <div class="cal-nav">
        <NButton quaternary circle size="small" @click="shiftMonth(-1)">
          <template #icon><NIcon :component="ChevronLeft" size="16" /></template>
        </NButton>
        <span class="cal-month">{{ monthTitle }}</span>
        <NButton quaternary circle size="small" @click="shiftMonth(1)">
          <template #icon><NIcon :component="ChevronRight" size="16" /></template>
        </NButton>
        <NButton size="small" secondary @click="goToday">今天</NButton>
      </div>
      <div class="cal-legend">
        <span class="legend-item"><span class="legend-dot legend-todo" />待办</span>
        <span class="legend-item"><NIcon :component="StickyNote" size="13" class="legend-note-ic" />便签</span>
        <span class="legend-item"><span class="legend-dot legend-overdue" />逾期</span>
      </div>
    </div>

    <!-- 跨月逾期提示条 -->
    <div v-if="overdueTodos.length" class="cal-overdue">
      <span class="overdue-title">跨月逾期 {{ overdueTodos.length }} 项：</span>
      <span
        v-for="t in overdueTodos.slice(0, 4)" :key="t.id"
        class="overdue-chip" role="button" tabindex="0"
        @click="router.push('/todos')"
        @keydown.enter.prevent="router.push('/todos')"
        @keydown.space.prevent="router.push('/todos')"
      >
        {{ t.title }}
      </span>
      <span v-if="overdueTodos.length > 4" class="overdue-more">…</span>
      <NButton size="tiny" text type="primary" class="overdue-go" @click="router.push('/todos')">去处理 →</NButton>
    </div>

    <!-- 网格 -->
    <div class="cal-body" :class="{ loading }">
      <JSkeletonGrid v-if="loading" :count="7" />
      <template v-else>
        <div class="cal-weekdays">
          <div v-for="w in WEEKDAYS" :key="w" class="cal-weekday">{{ w }}</div>
        </div>
        <div class="cal-grid">
          <div
            v-for="(cell, idx) in cells" :key="idx"
            class="cal-cell"
            :class="{
              'out-month': !cell.inMonth,
              today: cell.isToday,
              'drop-hover': !!dragTodoId && cell.inMonth,
            }"
            role="button"
            tabindex="0"
            :aria-label="`${cell.date.getMonth() + 1}月${cell.date.getDate()}日`"
            @click="openQuickAdd(cell)"
            @keydown.enter.prevent="openQuickAdd(cell)"
            @keydown.space.prevent="openQuickAdd(cell)"
            @dragover.prevent="cell.inMonth && (($event as any).dataTransfer!.dropEffect = 'move')"
            @drop.prevent="onDayDrop(cell)"
          >
            <div class="cell-head">
              <span class="cell-date">{{ cell.date.getDate() }}</span>
              <NIcon v-if="cell.notes.length" :component="StickyNote" size="12" class="cell-note-ic" />
            </div>
            <div class="cell-todos">
              <div
                v-for="t in cell.todos.slice(0, 3)" :key="t.id"
                class="cell-todo"
                :class="[`prio-${t.priority}`, { done: t.completed === 1 }]"
                draggable="true"
                @dragstart="onTodoDragStart($event, t)"
                @dragend="dragTodoId = null"
                @click.stop
              >
                <NCheckbox
                  :checked="t.completed === 1"
                  size="small"
                  @update:checked="() => toggleTodo(t)"
                  class="cell-todo-check"
                />
                <span class="cell-todo-title" :title="t.title">{{ t.title }}</span>
                <NButton quaternary circle size="tiny" class="cell-todo-del" @click.stop="removeTodo(t)">
                  <template #icon><NIcon :component="Trash2" size="11" /></template>
                </NButton>
              </div>
              <div v-if="cell.todos.length > 3" class="cell-more">+{{ cell.todos.length - 3 }} 项</div>
            </div>
            <div v-if="cell.notes.length" class="cell-notes">
              <span
                v-for="n in cell.notes.slice(0, 2)" :key="n.id"
                class="cell-note-chip" role="button" tabindex="0"
                @click.stop="openNote(n)"
                @keydown.enter.prevent.stop="openNote(n)"
                @keydown.space.prevent.stop="openNote(n)"
              >
                {{ n.title || '无标题' }}
              </span>
              <span v-if="cell.notes.length > 2" class="cell-more">+{{ cell.notes.length - 2 }}</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 快捷新建待办 -->
    <NModal v-model:show="quickShow" preset="card" title="新建待办" style="width: 360px" :bordered="false">
      <div class="quick-form">
        <p class="quick-date">{{ quickDate }}</p>
        <NInput v-model:value="quickTitle" placeholder="待办内容" autofocus @keyup.enter="submitQuick" />
        <NSelect
          v-model:value="quickPriority"
          :options="[
            { label: '低优先级', value: 0 },
            { label: '中优先级', value: 1 },
            { label: '高优先级', value: 2 },
          ]"
        />
        <NButton type="primary" block :loading="quickAdding" @click="submitQuick">添加</NButton>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.cal-wrap { display: flex; flex-direction: column; gap: 14px; height: 100%; }
.cal-toolbar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 2px 4px;
}
.cal-nav { display: flex; align-items: center; gap: 6px; }
.cal-month { font-size: 18px; font-weight: 800; color: var(--text-1); min-width: 120px; text-align: center; }
.cal-legend { display: flex; align-items: center; gap: 14px; font-size: var(--fs-xs); color: var(--text-3); }
.legend-item { display: inline-flex; align-items: center; gap: 5px; }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; }
.legend-todo { background: var(--brand); }
.legend-overdue { background: var(--danger); }
.legend-note-ic { color: var(--text-3); }

.cal-overdue {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 8px 14px;
  background: var(--danger-soft);
  border: 1px solid color-mix(in srgb, var(--danger) 25%, transparent);
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm); color: var(--text-2);
}
.overdue-title { font-weight: 600; color: var(--danger); flex-shrink: 0; }
.overdue-chip {
  background: var(--glass-chip-bg); border: 1px solid var(--glass-chip-border);
  padding: 1px 8px; border-radius: var(--radius-pill); cursor: pointer;
  max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.overdue-chip:hover { color: var(--danger); }
.overdue-more { color: var(--text-3); }
.overdue-go { margin-left: auto; flex-shrink: 0; }

.cal-body { flex: 1; min-height: 0; }
.cal-weekdays {
  display: grid; grid-template-columns: repeat(7, 1fr);
  margin-bottom: 6px;
}
.cal-weekday {
  text-align: center; font-size: var(--fs-xs); font-weight: 600;
  color: var(--text-3); padding: 4px 0;
}
.cal-grid {
  display: grid; grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  height: calc(100% - 26px);
}
.cal-cell {
  display: flex; flex-direction: column; gap: 4px;
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  padding: 6px 8px;
  min-height: 0;
  overflow: hidden;
  cursor: pointer;
  transition: border-color var(--dur) var(--ease), background var(--dur) var(--ease);
}
.cal-cell:hover { border-color: var(--brand); }
.cal-cell.out-month { opacity: .35; }
.cal-cell.today { border-color: var(--brand); box-shadow: 0 0 0 1px var(--brand) inset; }
.cal-cell.drop-hover { border-color: var(--brand); background: var(--brand-soft); }

.cell-head { display: flex; align-items: center; justify-content: space-between; }
.cell-date { font-size: var(--fs-sm); font-weight: 700; color: var(--text-2); }
.today .cell-date { color: var(--brand); }
.cell-note-ic { color: var(--text-3); }

.cell-todos { display: flex; flex-direction: column; gap: 3px; min-height: 0; overflow: hidden; }
.cell-todo {
  display: flex; align-items: center; gap: 4px;
  padding: 2px 4px;
  border-radius: var(--radius-xs);
  font-size: var(--fs-xs);
  background: color-mix(in srgb, var(--module-bookmark) 8%, transparent);
  cursor: grab;
}
.cell-todo.prio-2 { background: color-mix(in srgb, var(--danger) 12%, transparent); }
.cell-todo.prio-1 { background: color-mix(in srgb, var(--warning-text) 10%, transparent); }
.cell-todo.done { opacity: .5; }
.cell-todo.done .cell-todo-title { text-decoration: line-through; }
.cell-todo-check { pointer-events: auto; }
.cell-todo-title {
  flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  color: var(--text-1);
}
.cell-todo-del { opacity: 0; flex-shrink: 0; }
.cell-todo:hover .cell-todo-del { opacity: 1; }
.cell-more { font-size: var(--fs-xs); color: var(--text-3); padding-left: 2px; }

.cell-notes { display: flex; gap: 4px; flex-wrap: wrap; margin-top: auto; }
.cell-note-chip {
  font-size: 10px; color: var(--text-2);
  background: var(--glass-chip-bg);
  border: 1px dashed var(--glass-chip-border);
  border-radius: var(--radius-xs);
  padding: 0 5px;
  max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  cursor: pointer;
}
.cell-note-chip:hover { color: var(--brand); border-color: var(--brand); }

.quick-form { display: flex; flex-direction: column; gap: 10px; }
.quick-date { font-size: var(--fs-sm); color: var(--brand); font-weight: 600; margin: 0; }
</style>
