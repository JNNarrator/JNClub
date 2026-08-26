<script setup lang="ts">
/**
 * CalendarView.vue — 日历月/周视图
 * 聚合待办（普通 + 重复动态实例）+ 便签；支持拖拽改期/设置时间、快捷新建、自然语言添加
 */
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { NButton, NIcon, NCheckbox, NInput, NSelect, useMessage, NModal, NRadioGroup, NRadioButton } from 'naive-ui'
import { ChevronLeft, ChevronRight, StickyNote, Trash2, Repeat2, Clock } from 'lucide-vue-next'
import { useRouter, useRoute } from 'vue-router'
import axios from 'axios'
import JSkeletonGrid from '../../../shared/components/ui/JSkeletonGrid.vue'
import JErrorState from '../../../shared/components/ui/JErrorState.vue'
import { parseTodoNlp } from '../../../shared/utils/todoNlp'

interface CalendarTodo {
  id: number
  title: string
  note?: string | null
  priority: number
  completed: number
  dueDate: string
  dueTime?: string | null
  recurrence?: string | null
  recurrenceInterval?: number
  itemCount?: number
  itemCompletedCount?: number
}
interface CalendarNote {
  id: number
  title: string
  updateTime: string
}
interface DayData {
  date: string
  todos: CalendarTodo[]
  notes: CalendarNote[]
}

interface DayCell {
  date: Date
  inMonth: boolean
  isToday: boolean
  todos: CalendarTodo[]
  notes: CalendarNote[]
}

const props = defineProps<{ refresh: number }>()
const message = useMessage()
const router = useRouter()
const route = useRoute()

const loading = ref(false)
const loadError = ref(false)
const viewMode = ref<'month' | 'week'>('month')
const monthCursor = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const weekCursor = ref(startOfWeek(new Date()))
const days = ref<DayData[]>([])
const overdueTodos = ref<CalendarTodo[]>([])
const highlightTodoId = ref<number | null>(null)

const monthTitle = computed(() =>
  `${monthCursor.value.getFullYear()} 年 ${monthCursor.value.getMonth() + 1} 月`,
)
const weekTitle = computed(() => {
  const start = weekCursor.value
  const end = new Date(start.getFullYear(), start.getMonth(), start.getDate() + 6)
  return `${start.getFullYear()} 年 ${start.getMonth() + 1} 月 ${start.getDate()} 日 – ${end.getMonth() + 1} 月 ${end.getDate()} 日`
})

function startOfWeek(d: Date): Date {
  const date = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const diff = (date.getDay() + 6) % 7
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - diff)
}

const fmtDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const fetchData = async () => {
  loading.value = true
  loadError.value = false
  try {
    let start: Date
    let end: Date
    if (viewMode.value === 'month') {
      start = monthCursor.value
      end = new Date(start.getFullYear(), start.getMonth() + 1, 0)
    } else {
      start = weekCursor.value
      end = new Date(start.getFullYear(), start.getMonth(), start.getDate() + 6)
    }
    const res = await axios.get('/api/calendar/range', {
      params: { start: fmtDate(start), end: fmtDate(end) },
    })
    if (res.data.code === 200) {
      days.value = res.data.data?.days || []
      overdueTodos.value = res.data.data?.overdueTodos || []
      highlightTodoId.value = route.query.todo ? Number(route.query.todo) : null
    } else {
      loadError.value = true
      message.error(res.data.message || '加载失败')
    }
  } catch (e: any) {
    loadError.value = true
    message.error(e.response?.data?.message || e.message || '加载失败')
  } finally {
    loading.value = false
    if (highlightTodoId.value != null) {
      await nextTick()
      document.querySelector('.cal-todo-highlight')?.scrollIntoView({ block: 'center' })
    }
  }
}

const reload = () => fetchData()
watch(() => props.refresh, reload)
watch(viewMode, () => fetchData())
onMounted(() => {
  const qDate = route.query.date
  if (qDate) {
    const d = new Date(String(qDate) + 'T00:00:00')
    if (!Number.isNaN(d.getTime())) {
      monthCursor.value = new Date(d.getFullYear(), d.getMonth(), 1)
      weekCursor.value = startOfWeek(d)
      if (route.query.view === 'week') viewMode.value = 'week'
    }
  }
  fetchData()
})

/* ─── 月网格构建 ─── */
const shiftMonth = (delta: number) => {
  monthCursor.value = new Date(monthCursor.value.getFullYear(), monthCursor.value.getMonth() + delta, 1)
  fetchData()
}
const shiftWeek = (delta: number) => {
  weekCursor.value = new Date(weekCursor.value.getFullYear(), weekCursor.value.getMonth(), weekCursor.value.getDate() + delta * 7)
  fetchData()
}
const goToday = () => {
  const now = new Date()
  monthCursor.value = new Date(now.getFullYear(), now.getMonth(), 1)
  weekCursor.value = startOfWeek(now)
  fetchData()
}

const dayKey = (d: Date) => fmtDate(d)
const dayMap = computed(() => {
  const map = new Map<string, DayData>()
  for (const day of days.value) map.set(day.date, day)
  return map
})

const cells = computed<DayCell[]>(() => {
  const y = monthCursor.value.getFullYear()
  const m = monthCursor.value.getMonth()
  const first = new Date(y, m, 1)
  const startWeekday = (first.getDay() + 6) % 7
  const today = new Date()
  const result: DayCell[] = []
  const start = new Date(y, m, 1 - startWeekday)
  for (let i = 0; i < 42; i++) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)
    const inMonth = d.getMonth() === m
    const isToday = d.getFullYear() === today.getFullYear() && d.getMonth() === today.getMonth() && d.getDate() === today.getDate()
    const data = dayMap.value.get(dayKey(d))
    result.push({
      date: d,
      inMonth,
      isToday,
      todos: data?.todos || [],
      notes: data?.notes || [],
    })
  }
  return result
})

/* ─── 周视图数据 ─── */
const weekDays = computed(() => days.value)
const HOURS = Array.from({ length: 24 }, (_, i) => i)
const allDayTodos = (day: DayData) => day.todos.filter(t => !t.dueTime)
const timedTodos = (day: DayData) => day.todos
  .filter(t => t.dueTime)
  .sort((a, b) => (a.dueTime || '').localeCompare(b.dueTime || ''))
const timePos = (t: CalendarTodo) => {
  const s = String(t.dueTime || '00:00').slice(0, 5).split(':')
  return Number(s[0]) * 60 + Number(s[1])
}

/* ─── 待办交互 ─── */
const toggleTodo = async (t: CalendarTodo) => {
  const next = t.completed === 1 ? false : true
  try {
    const res = await axios.put(`/api/todos/${t.id}/complete`, { completed: next })
    if (res.data.code === 200) {
      t.completed = next ? 1 : 0
      if (t.recurrence && next) fetchData()
    } else message.error(res.data.message || '操作失败')
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '操作失败')
  }
}

let dragTodoId: number | null = null
const onTodoDragStart = (e: DragEvent, t: CalendarTodo) => {
  dragTodoId = t.id
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
const clearDrag = () => { dragTodoId = null }

const updateTodoDate = async (id: number, date: string, time?: string | null) => {
  try {
    const payload: Record<string, any> = { dueDate: date }
    if (time) payload.dueTime = time
    const res = await axios.put(`/api/todos/${id}`, payload)
    if (res.data.code === 200) {
      message.success('已调整到 ' + date + (time ? ` ${time.slice(0, 5)}` : ''))
      fetchData()
    } else message.error(res.data.message || '调整失败')
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '调整失败')
  }
  dragTodoId = null
}

const onDayDrop = (cell: DayCell) => {
  if (!dragTodoId) return
  if (!cell.inMonth) return
  updateTodoDate(dragTodoId, fmtDate(cell.date), null)
}
const onWeekAllDayDrop = (day: DayData) => {
  if (!dragTodoId) return
  updateTodoDate(dragTodoId, day.date, null)
}
const onWeekTimeDrop = (day: DayData, hour: number) => {
  if (!dragTodoId) return
  updateTodoDate(dragTodoId, day.date, `${String(hour).padStart(2, '0')}:00:00`)
}

/* ─── 快捷新建 ─── */
const quickDate = ref<string | null>(null)
const quickTime = ref<string | null>(null)
const quickTitle = ref('')
const quickPriority = ref(1)
const quickShow = ref(false)
const quickAdding = ref(false)

const openQuickAdd = (cell: DayCell) => {
  if (!cell.inMonth) return
  quickDate.value = fmtDate(cell.date)
  quickTime.value = null
  quickTitle.value = ''
  quickPriority.value = 1
  quickShow.value = true
}
const openWeekQuickAdd = (day: DayData, hour?: number) => {
  quickDate.value = day.date
  quickTime.value = hour != null ? `${String(hour).padStart(2, '0')}:00:00` : null
  quickTitle.value = ''
  quickPriority.value = 1
  quickShow.value = true
}

const submitQuick = async () => {
  if (!quickTitle.value.trim()) { message.warning('请输入待办内容'); return }
  quickAdding.value = true
  try {
    const parsed = parseTodoNlp(quickTitle.value)
    const title = parsed.title || quickTitle.value.trim()
    const payload: Record<string, any> = {
      title,
      priority: parsed.priority != null ? parsed.priority : quickPriority.value,
      dueDate: parsed.dueDate || quickDate.value,
    }
    if (parsed.dueTime || quickTime.value) payload.dueTime = parsed.dueTime || quickTime.value
    if (parsed.remindAt) payload.remindAt = parsed.remindAt
    if (parsed.recurrence) {
      payload.recurrence = parsed.recurrence
      payload.recurrenceInterval = 1
    }
    const res = await axios.post('/api/todos', payload)
    if (res.data.code === 200) {
      message.success('已添加')
      quickShow.value = false
      fetchData()
    } else message.error(res.data.message || '添加失败')
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '添加失败')
  } finally { quickAdding.value = false }
}

const removeTodo = async (t: CalendarTodo) => {
  try {
    const res = await axios.delete(`/api/todos/${t.id}`)
    if (res.data.code === 200) {
      message.success('已删除')
      fetchData()
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '删除失败')
  }
}

const openNote = (n: CalendarNote) => router.push(`/notes/${n.id}`)
const openTodo = (t: CalendarTodo) => router.push(`/todos?highlight=${t.id}`)

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
const WEEKDAY_SHORT = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const priorityClass = (p: number) => `prio-${p ?? 0}`
const recurrenceTag = (r?: string | null) => {
  if (!r) return ''
  return ({ DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月', YEARLY: '每年' } as Record<string, string>)[r] || r
}
</script>

<template>
  <div class="cal-wrap">
    <!-- 工具栏 -->
    <div class="cal-toolbar">
      <div class="cal-nav">
        <NButton quaternary circle size="small" @click="viewMode === 'month' ? shiftMonth(-1) : shiftWeek(-1)">
          <template #icon><NIcon :component="ChevronLeft" size="16" /></template>
        </NButton>
        <span class="cal-month">{{ viewMode === 'month' ? monthTitle : weekTitle }}</span>
        <NButton quaternary circle size="small" @click="viewMode === 'month' ? shiftMonth(1) : shiftWeek(1)">
          <template #icon><NIcon :component="ChevronRight" size="16" /></template>
        </NButton>
        <NButton size="small" secondary @click="goToday">今天</NButton>
      </div>
      <NRadioGroup v-model:value="viewMode" size="small">
        <NRadioButton value="month">月</NRadioButton>
        <NRadioButton value="week">周</NRadioButton>
      </NRadioGroup>
      <div class="cal-legend">
        <span class="legend-item"><span class="legend-dot legend-todo" />待办</span>
        <span class="legend-item"><span class="legend-dot legend-repeat" />重复</span>
        <span class="legend-item"><NIcon :component="StickyNote" size="13" class="legend-note-ic" />便签</span>
        <span class="legend-item"><span class="legend-dot legend-overdue" />逾期</span>
      </div>
    </div>

    <!-- 跨月/跨周逾期提示条 -->
    <div v-if="overdueTodos.length" class="cal-overdue">
      <span class="overdue-title">逾期 {{ overdueTodos.length }} 项：</span>
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

    <!-- 主体 -->
    <div class="cal-body" :class="{ loading }">
      <JSkeletonGrid v-if="loading" :count="7" />
      <JErrorState
        v-else-if="loadError"
        message="日历加载失败"
        hint="请检查网络后重试"
        class="cal-error"
        @retry="fetchData"
      />

      <!-- 月视图 -->
      <template v-else-if="viewMode === 'month'">
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
                v-for="t in cell.todos.slice(0, 3)" :key="`${t.id}-${t.dueDate}`"
                :class="['cell-todo', priorityClass(t.priority), { done: t.completed === 1, 'cal-todo-highlight': highlightTodoId === t.id }]"
                :title="t.title"
                draggable="true"
                @dragstart="onTodoDragStart($event, t)"
                @dragend="clearDrag"
                @click.stop
                @dblclick.stop="openTodo(t)"
              >
                <NCheckbox
                  :checked="t.completed === 1"
                  size="small"
                  @update:checked="() => toggleTodo(t)"
                  class="cell-todo-check"
                />
                <span class="cell-todo-title">{{ t.title }}</span>
                <span v-if="t.dueTime" class="cell-todo-time">
                  <NIcon :component="Clock" size="10" />
                  {{ String(t.dueTime).slice(0, 5) }}
                </span>
                <NIcon v-if="t.recurrence" :component="Repeat2" size="11" class="cell-todo-repeat" :title="recurrenceTag(t.recurrence)" />
                <span v-if="t.itemCount != null && t.itemCount > 0" class="cell-todo-progress">
                  {{ t.itemCompletedCount || 0 }}/{{ t.itemCount }}
                </span>
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

      <!-- 周视图 -->
      <template v-else>
        <div class="cal-week">
          <div class="cal-week-all-day-row">
            <div class="cal-week-time-label">全天</div>
            <div
              v-for="day in weekDays" :key="day.date"
              class="cal-week-all-day"
              :class="{ 'is-today': day.date === fmtDate(new Date()) }"
              @dragover.prevent="($event as any).dataTransfer!.dropEffect = 'move'"
              @drop.prevent="onWeekAllDayDrop(day)"
              @click="openWeekQuickAdd(day)"
            >
              <div class="cal-week-header">
                <span class="cal-week-day-name">{{ WEEKDAY_SHORT[new Date(day.date + 'T00:00:00').getDay() === 0 ? 6 : new Date(day.date + 'T00:00:00').getDay() - 1] }}</span>
                <span class="cal-week-day-num">{{ Number(day.date.slice(8, 10)) }}</span>
              </div>
              <div
                v-for="t in allDayTodos(day)" :key="`${t.id}-${t.dueDate}-ad`"
                :class="['cal-week-all-todo', priorityClass(t.priority), { done: t.completed === 1 }]"
                draggable="true"
                @dragstart="onTodoDragStart($event, t)"
                @dragend="clearDrag"
                @click.stop="openTodo(t)"
              >
                {{ t.title }}
              </div>
            </div>
          </div>
          <div class="cal-week-grid">
            <div class="cal-week-times">
              <div v-for="h in HOURS" :key="h" class="cal-week-hour-label">{{ String(h).padStart(2, '0') }}:00</div>
            </div>
            <div class="cal-week-columns">
              <div
                v-for="day in weekDays" :key="day.date"
                class="cal-week-col"
                @dragover.prevent="($event as any).dataTransfer!.dropEffect = 'move'"
              >
                <div
                  v-for="h in HOURS" :key="h"
                  class="cal-week-slot"
                  :class="{ 'is-today': day.date === fmtDate(new Date()) }"
                  @drop.prevent="onWeekTimeDrop(day, h)"
                  @click="openWeekQuickAdd(day, h)"
                >
                  <div
                    v-for="t in timedTodos(day).filter(x => timePos(x) >= h * 60 && timePos(x) < h * 60 + 60)"
                    :key="`${t.id}-${t.dueDate}-${t.dueTime}`"
                    :class="['cal-week-event', priorityClass(t.priority), { done: t.completed === 1, 'cal-todo-highlight': highlightTodoId === t.id }]"
                    :style="{ top: `${((timePos(t) - h * 60) / 60) * 100}%`, height: '58px' }"
                    :title="t.title"
                    draggable="true"
                    @dragstart="onTodoDragStart($event, t)"
                    @dragend="clearDrag"
                    @click.stop="openTodo(t)"
                  >
                    <span class="cal-week-event-time">{{ String(t.dueTime).slice(0, 5) }}</span>
                    <span class="cal-week-event-title">{{ t.title }}</span>
                    <NIcon v-if="t.recurrence" :component="Repeat2" size="11" class="cal-week-event-repeat" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 快捷新建待办 -->
    <NModal v-model:show="quickShow" preset="card" title="新建待办" style="width: 380px" :bordered="false">
      <div class="quick-form">
        <p class="quick-date">{{ quickDate }}{{ quickTime ? ' ' + String(quickTime).slice(0, 5) : '' }}</p>
        <NInput v-model:value="quickTitle" placeholder="如：周五 14:00 准备周报 / 每天 9:00 站会" autofocus @keyup.enter="submitQuick" />
        <NSelect
          v-model:value="quickPriority"
          :options="[
            { label: '低优先级', value: 0 },
            { label: '中优先级', value: 1 },
            { label: '高优先级', value: 2 },
          ]"
        />
        <NButton type="primary" block :loading="quickAdding" @click="submitQuick">添加</NButton>
        <p class="quick-hint">支持自然语言：时间、重复规则、提前提醒</p>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.cal-wrap { display: flex; flex-direction: column; gap: 14px; height: 100%; min-height: 0; }
.cal-toolbar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 2px 4px;
  flex-wrap: wrap;
}
.cal-nav { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.cal-month { font-size: 18px; font-weight: 800; color: var(--text-1); min-width: 120px; text-align: center; }
.cal-legend { display: flex; align-items: center; gap: 14px; font-size: var(--fs-xs); color: var(--text-3); flex-wrap: wrap; }
.legend-item { display: inline-flex; align-items: center; gap: 5px; }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; }
.legend-todo { background: var(--brand); }
.legend-repeat { background: var(--brand-suppl, var(--brand)); }
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

.cal-body { flex: 1; min-height: 0; display: flex; flex-direction: column; }
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
  min-height: 420px;
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
.cell-todo-time {
  flex-shrink: 0; display: inline-flex; align-items: center; gap: 2px;
  font-size: 10px; color: var(--text-2);
}
.cell-todo-repeat { flex-shrink: 0; color: var(--brand-suppl, var(--brand)); }
.cell-todo-progress { flex-shrink: 0; font-size: 10px; color: var(--text-3); }
.cell-todo-del { opacity: 0; flex-shrink: 0; }
.cell-todo:hover .cell-todo-del { opacity: 1; }
.cal-todo-highlight {
  outline: 2px solid var(--brand);
  outline-offset: 1px;
  animation: cal-highlight-pulse 1.2s ease 2;
}
@keyframes cal-highlight-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: .55; }
}
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

/* 周视图 */
.cal-week { display: flex; flex-direction: column; flex: 1; min-height: 0; gap: 8px; }
.cal-week-all-day-row {
  display: grid;
  grid-template-columns: 64px repeat(7, 1fr);
  gap: 4px;
}
.cal-week-time-label {
  display: flex; align-items: center; justify-content: center;
  font-size: var(--fs-xs); color: var(--text-3);
}
.cal-week-all-day {
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  padding: 4px 6px;
  min-height: 56px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cal-week-all-day.is-today { border-color: var(--brand); }
.cal-week-header {
  display: flex; align-items: center; justify-content: space-between;
  font-size: var(--fs-xs); font-weight: 600; color: var(--text-2);
}
.cal-week-day-num { color: var(--brand); }
.cal-week-all-todo {
  font-size: 11px;
  padding: 1px 5px;
  border-radius: var(--radius-xs);
  background: color-mix(in srgb, var(--module-bookmark) 10%, transparent);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  cursor: grab;
}
.cal-week-all-todo.prio-2 { background: color-mix(in srgb, var(--danger) 12%, transparent); }
.cal-week-all-todo.done { text-decoration: line-through; opacity: .55; }

.cal-week-grid {
  display: grid;
  grid-template-columns: 64px repeat(7, 1fr);
  gap: 4px;
  flex: 1;
  min-height: 480px;
  overflow-y: auto;
}
.cal-week-times { display: flex; flex-direction: column; }
.cal-week-hour-label {
  height: 48px;
  font-size: 10px;
  color: var(--text-3);
  text-align: right;
  padding-right: 6px;
  border-top: 1px solid var(--glass-border);
  transform: translateY(-6px);
}
.cal-week-columns { display: contents; }
.cal-week-col { position: relative; display: flex; flex-direction: column; }
.cal-week-slot {
  position: relative;
  height: 48px;
  border: 1px solid transparent;
  border-top: 1px solid var(--glass-border);
  border-radius: 0;
  cursor: pointer;
}
.cal-week-slot.is-today { background: color-mix(in srgb, var(--brand) 4%, transparent); }
.cal-week-slot:hover { background: var(--brand-soft); }
.cal-week-event {
  position: absolute;
  left: 2px;
  right: 2px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 2px 4px;
  font-size: 10px;
  border-radius: var(--radius-xs);
  background: color-mix(in srgb, var(--module-bookmark) 18%, transparent);
  border-left: 2px solid var(--brand);
  overflow: hidden;
  cursor: grab;
}
.cal-week-event.prio-2 { border-left-color: var(--danger); }
.cal-week-event.prio-1 { border-left-color: var(--warning-text); }
.cal-week-event.done { opacity: .55; text-decoration: line-through; }
.cal-week-event-time { flex-shrink: 0; font-weight: 600; color: var(--text-2); }
.cal-week-event-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cal-week-event-repeat { flex-shrink: 0; color: var(--brand-suppl, var(--brand)); }

.quick-form { display: flex; flex-direction: column; gap: 10px; }
.quick-date { font-size: var(--fs-sm); color: var(--brand); font-weight: 600; margin: 0; }
.quick-hint { font-size: var(--fs-xs); color: var(--text-3); margin: 0; }

.cal-error {
  height: 100%;
  min-height: 220px;
}

@media (max-width: 767px) {
  .cal-toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .cal-nav { justify-content: center; }
  .cal-legend { justify-content: space-between; gap: 8px; }
  .cal-grid { gap: 3px; min-height: 360px; }
  .cal-cell { padding: 4px 3px; gap: 2px; }
  .cell-date { font-size: var(--fs-xs); }
  .cell-todo-title { max-width: 56px; }
  .cell-note-chip { max-width: 48px; font-size: 9px; }
  .cell-todo-del { opacity: 1; }
  .cal-week-all-day-row { grid-template-columns: 44px repeat(7, 1fr); }
  .cal-week-grid { grid-template-columns: 44px repeat(7, 1fr); }
  .cal-week-time-label { font-size: 9px; }
}
</style>