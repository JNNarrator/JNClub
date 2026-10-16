<script setup lang="ts">
/**
 * AppHeader.vue — 主界面顶栏
 * 从 Home.vue 拆出：面包屑、搜索、多选、视图切换、刷新、主题、用户下拉。
 * 用户信息/退出/资料弹窗逻辑一并收口在此，Home 不再维护。
 */
import { ref, h, computed, onMounted, onUnmounted } from 'vue'
import {
  NButton, NIcon, NBadge, NPopover, NBreadcrumb, NBreadcrumbItem, NAvatar, NDropdown, NModal, useDialog,
  type DropdownOption,
} from 'naive-ui'
import { FolderOpen, Search, CheckSquare, RefreshCw, Sun, Moon, CircleUser, LogOut, Bell, CheckCheck, MoreVertical, LayoutGrid, Rows3 } from 'lucide-vue-next'
import axios from 'axios'
import { useUserStore } from '../stores/user'
import { openMenu, openMenuAt } from '../composables/useContextMenu'
import { useLongPress } from '../composables/useLongPress'
import ViewSwitcher, { type ViewMode } from '../components/ViewSwitcher.vue'
import JInlineEmpty from '../components/ui/JInlineEmpty.vue'
import type { ModuleKey } from '../types/nav'

const props = defineProps<{
  isDark: boolean
  activeModule: ModuleKey
  isMobile: boolean
  batchMode: boolean
  viewMode: ViewMode
  breadcrumbCurrent: string
}>()

const emit = defineEmits<{
  'toggle-theme': []
  'open-search': []
  'update:batchMode': [v: boolean]
  'update:viewMode': [v: ViewMode]
  refresh: []
  'open-dir-drawer': []
  'breadcrumb-root': []
}>()

const userStore = useUserStore()
const longPress = useLongPress()
const dialog = useDialog()

interface NotificationItem {
  id: string | number
  type?: string
  title: string
  content?: string
  refType?: string
  refId?: string | number
  readFlag: number
  createTime?: string
}

const notificationList = ref<NotificationItem[]>([])
const notificationLoading = ref(false)
const notificationUnread = ref(0)
let notificationTimer: number | undefined

const fetchNotificationUnread = async () => {
  try {
    const { data } = await axios.get('/api/notifications/unread-count')
    notificationUnread.value = Number(data?.data?.count ?? 0)
  } catch { /* 忽略 */ }
}

const fetchNotifications = async () => {
  notificationLoading.value = true
  try {
    const { data } = await axios.get('/api/notifications', { params: { limit: 30, unreadOnly: false } })
    notificationList.value = Array.isArray(data?.data) ? data.data : []
    await fetchNotificationUnread()
  } catch { /* 忽略 */ } finally {
    notificationLoading.value = false
  }
}

const openNotificationPanel = () => {
  void fetchNotifications()
}

const markNotificationRead = async (n: NotificationItem) => {
  if (n.readFlag === 1) return
  try {
    await axios.put(`/api/notifications/${n.id}/read`)
  } catch { /* 忽略 */ }
  n.readFlag = 1
  notificationUnread.value = Math.max(0, notificationUnread.value - 1)
}

const markAllNotificationsRead = async () => {
  try {
    await axios.put('/api/notifications/read-all')
  } catch { /* 忽略 */ }
  notificationList.value.forEach((n) => { n.readFlag = 1 })
  notificationUnread.value = 0
}

const notificationTypeLabel = (type?: string) => {
  const labels: Record<string, string> = {
    SYSTEM: '系统通知',
  }
  return (type && labels[type]) || type || '通知'
}

const formatNotificationTime = (value?: string) => {
  if (!value) return ''
  const raw = String(value).includes(' ') ? String(value).replace(' ', 'T') : String(value)
  const d = new Date(raw)
  if (!Number.isNaN(d.getTime())) {
    const p = (v: number) => String(v).padStart(2, '0')
    return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  }
  return String(value).slice(5, 16)
}

const handleNotificationClick = async (n: NotificationItem) => {
  await markNotificationRead(n)
}

const startNotificationPolling = () => {
  if (notificationTimer !== undefined) return
  notificationTimer = window.setInterval(() => {
    void fetchNotificationUnread()
  }, 60_000)
}

const stopNotificationPolling = () => {
  if (notificationTimer !== undefined) {
    window.clearInterval(notificationTimer)
    notificationTimer = undefined
  }
}

const handleVisibilityChange = () => {
  if (document.hidden) {
    stopNotificationPolling()
  } else {
    void fetchNotificationUnread()
    startNotificationPolling()
  }
}

onMounted(() => {
  void fetchNotificationUnread()
  startNotificationPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  stopNotificationPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

const userDropdownOptions = [
  { label: '用户信息', key: 'profile', icon: () => h(NIcon, null, { default: () => h(CircleUser) }) },
  { label: '退出登录', key: 'logout', icon: () => h(NIcon, null, { default: () => h(LogOut) }) },
]
const showProfileModal = ref(false)
const roleLabel = computed(() => userStore.userinfo?.role === 'admin' ? '管理员' : '用户')

const handleUserDropdown = (key: string) => {
  if (key === 'logout') {
    dialog.warning({
      title: '确认退出',
      content: '确定要退出登录吗？',
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const res = await axios.post('/api/auth/logout')
          const ssoLogoutUrl = res.data?.data?.ssoLogoutUrl
          const redirectUrl = res.data?.data?.redirectUrl
          if (ssoLogoutUrl) {
            try {
              await axios.post(ssoLogoutUrl, null, { params: { redirect: redirectUrl ?? '' }, timeout: 5000 })
            } catch { /* 忽略 */ }
            window.location.href = redirectUrl || '/sso/login'
            return
          }
        } catch { /* 忽略 */ }
        delete axios.defaults.headers.common['jn-token']
        localStorage.removeItem('jn-token')
        window.location.href = '/sso/login'
      },
    })
  } else if (key === 'profile') {
    showProfileModal.value = true
  }
}

const goSsoProfile = () => {
  const url = userStore.userinfo?.ssoProfileUrl
  if (url) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

/**
 * 移动端顶栏收纳菜单：窄屏单行放不下全部操作（402px 视口实测右侧需 332px，
 * 会把面包屑压成 0 宽并从左侧溢出，文字被按钮盖住）。
 * 低频操作（多选/刷新/主题）收进「更多」，高频操作（搜索/通知/视图切换/头像）常驻。
 */
const mobileMoreOptions = computed(() => {
  const options: DropdownOption[] = []
  if (props.activeModule === 'bookmarks' || props.activeModule === 'notes') {
    options.push({
      label: props.batchMode ? '退出多选' : '多选',
      key: 'batch',
      icon: () => h(NIcon, null, { default: () => h(CheckSquare) }),
    })
  }
  options.push(
    { label: '刷新', key: 'refresh', icon: () => h(NIcon, null, { default: () => h(RefreshCw) }) },
    {
      label: props.isDark ? '切换到白天模式' : '切换到暗黑模式',
      key: 'theme',
      icon: () => h(NIcon, null, { default: () => h(props.isDark ? Sun : Moon) }),
    },
  )
  return options
})

const handleMobileMore = (key: string) => {
  if (key === 'batch') emit('update:batchMode', !props.batchMode)
  else if (key === 'refresh') emit('refresh')
  else if (key === 'theme') emit('toggle-theme')
}

const moduleName = computed(() => {
  const m = props.activeModule
  return m === 'bookmarks' ? '收藏夹' : m === 'notes' ? '便签' : m === 'files' ? '云盘' : '密码库'
})

const handleBreadcrumbRoot = () => {
  emit('breadcrumb-root')
}
</script>

<template>
  <header class="home-header glass-header">
    <div class="header-left">
      <NButton
        v-if="isMobile"
        quaternary circle size="small"
        class="mobile-dir-btn jnclub-bouncy"
        title="目录"
        @click="emit('open-dir-drawer')"
      >
        <template #icon><NIcon :component="FolderOpen" size="18" /></template>
      </NButton>
      <NBreadcrumb class="jnclub-breadcrumb">
        <!-- 首项不设品牌根「JNClub」（报告 IA-2）：它与第二项绑同一个 handleBreadcrumbRoot、
             点击效果逐位相同（都是回到「全部」），属重复层级；品牌名在侧栏 logo 区已有。
             移动端此前已用 CSS 隐藏首项，现在两端统一为「模块 / 当前目录」两项。 -->
        <NBreadcrumbItem @click="handleBreadcrumbRoot">
          {{ moduleName }}
        </NBreadcrumbItem>
        <NBreadcrumbItem v-if="breadcrumbCurrent !== '全部'" class="breadcrumb-current">
          {{ breadcrumbCurrent }}
        </NBreadcrumbItem>
      </NBreadcrumb>
    </div>

    <div class="header-right">
      <NButton quaternary circle size="small" class="refresh-btn" @click="emit('open-search')" title="搜索 (Ctrl/⌘+K)">
        <template #icon><NIcon :component="Search" size="16" /></template>
      </NButton>

      <NPopover
        trigger="click"
        placement="bottom-end"
        :show-arrow="false"
        :width="360"
        class="notification-popover"
        @update:show="(show: boolean) => { if (show) openNotificationPanel() }"
      >
        <template #trigger>
          <NButton quaternary circle size="small" class="refresh-btn" title="通知">
            <template #icon>
              <NBadge :value="notificationUnread" :max="99" :show="notificationUnread > 0" dot>
                <NIcon :component="Bell" size="16" />
              </NBadge>
            </template>
          </NButton>
        </template>
        <div class="notification-panel">
          <div class="notification-header">
            <span class="notification-title">通知</span>
            <span class="notification-subtitle">
              {{ notificationUnread > 0 ? `${notificationUnread} 条未读` : '没有未读' }}
            </span>
            <NButton
              v-if="notificationUnread > 0"
              text size="tiny" type="primary"
              class="notification-mark-all"
              @click="markAllNotificationsRead"
            >
              <template #icon><NIcon :component="CheckCheck" size="14" /></template>
              全部已读
            </NButton>
          </div>
          <JInlineEmpty v-if="notificationLoading" message="加载中…" />
          <JInlineEmpty v-else-if="notificationList.length === 0" message="暂无通知" />
          <div v-else class="notification-list">
            <button
              v-for="n in notificationList" :key="n.id"
              type="button"
              :class="['notification-item', { unread: n.readFlag !== 1 }]"
              @click="handleNotificationClick(n)"
            >
              <span v-if="n.readFlag !== 1" class="notification-dot" />
              <span class="notification-item-main">
                <span class="notification-item-title">{{ n.title }}</span>
                <span v-if="n.content" class="notification-item-content">{{ n.content }}</span>
                <span class="notification-item-meta">
                  {{ notificationTypeLabel(n.type) }} · {{ formatNotificationTime(n.createTime) }}
                </span>
              </span>
            </button>
          </div>
        </div>
      </NPopover>

      <NButton
        v-if="activeModule === 'bookmarks' || activeModule === 'notes'"
        quaternary circle size="small"
        class="refresh-btn header-secondary"
        :type="batchMode ? 'primary' : 'default'"
        :title="batchMode ? '退出多选' : '多选'"
        @click="emit('update:batchMode', !batchMode)"
      >
        <template #icon><NIcon :component="CheckSquare" size="16" /></template>
      </NButton>
      <ViewSwitcher
        v-if="!isMobile && (activeModule === 'bookmarks' || activeModule === 'notes')"
        :model-value="viewMode"
        @update:model-value="(v: ViewMode) => emit('update:viewMode', v)"
      />
      <!-- 移动端：分段控件（98px）换成图标按钮，把宽度让给面包屑路径 -->
      <NButton
        v-else-if="isMobile && (activeModule === 'bookmarks' || activeModule === 'notes')"
        quaternary circle size="small"
        class="refresh-btn header-view-toggle"
        :title="viewMode === 'grid' ? '切换到极简视图' : '切换到卡片视图'"
        :aria-label="viewMode === 'grid' ? '切换到极简视图' : '切换到卡片视图'"
        @click="emit('update:viewMode', viewMode === 'grid' ? 'list' : 'grid')"
      >
        <template #icon><NIcon :component="viewMode === 'grid' ? LayoutGrid : Rows3" size="16" /></template>
      </NButton>
      <NButton quaternary circle size="small" @click="emit('refresh')" class="refresh-btn jnclub-bouncy header-secondary" title="刷新">
        <template #icon><NIcon :component="RefreshCw" size="16" /></template>
      </NButton>

      <button
        type="button"
        class="theme-toggle-btn jnclub-bouncy header-secondary"
        :title="isDark ? '切换到白天模式' : '切换到暗黑模式'"
        :aria-label="isDark ? '切换到白天模式' : '切换到暗黑模式'"
        @click="emit('toggle-theme')"
      >
        <NIcon :component="isDark ? Sun : Moon" size="16" />
      </button>

      <!-- 移动端：低频操作收纳，避免顶栏在窄屏溢出压住面包屑 -->
      <NDropdown
        v-if="isMobile"
        trigger="click"
        placement="bottom-end"
        :options="mobileMoreOptions"
        @select="handleMobileMore"
      >
        <NButton quaternary circle size="small" class="refresh-btn header-more-btn" title="更多">
          <template #icon><NIcon :component="MoreVertical" size="16" /></template>
        </NButton>
      </NDropdown>

      <NDropdown :options="userDropdownOptions" @select="handleUserDropdown" placement="bottom-end" trigger="click">
        <div class="user-row jnclub-bouncy jnclub-longpress" @contextmenu.prevent="openMenu($event, userDropdownOptions, handleUserDropdown)" v-on="longPress((ev) => openMenuAt(ev.clientX, ev.clientY, userDropdownOptions, handleUserDropdown))">
          <NAvatar round size="small" :src="userStore.userinfo?.avatar" class="user-avatar">
            <template v-if="!userStore.userinfo?.avatar">
              {{ userStore.userinfo?.nickname?.charAt(0) || 'U' }}
            </template>
          </NAvatar>
          <span class="user-name">{{ userStore.userinfo?.nickname || '用户' }}</span>
          <span class="user-role">{{ roleLabel }}</span>
        </div>
      </NDropdown>
    </div>
  </header>

  <!-- 用户信息弹窗 -->
  <NModal v-model:show="showProfileModal" preset="dialog" title="用户信息">
    <div class="profile-content">
      <div class="profile-avatar">
        <NAvatar round :size="64" :src="userStore.userinfo?.avatar" class="profile-avatar-large">
          <template v-if="!userStore.userinfo?.avatar">
            {{ userStore.userinfo?.nickname?.charAt(0) || 'U' }}
          </template>
        </NAvatar>
        <div class="profile-name">{{ userStore.userinfo?.nickname || '用户' }}</div>
      </div>
      <div class="profile-detail">
        <div class="detail-row">邮箱：{{ userStore.userinfo?.email || userStore.userinfo?.username || '--' }}</div>
        <div class="detail-row" v-if="userStore.userinfo?.ssoProfileUrl">
          <NButton type="primary" block @click="goSsoProfile">
            <template #icon><NIcon :component="CircleUser" /></template>
            前往 SSO 修改资料
          </NButton>
        </div>
      </div>
    </div>
    <template #action>
      <NButton type="error" @click="handleUserDropdown('logout')">
        <template #icon><NIcon :component="LogOut" /></template>
        退出登录
      </NButton>
    </template>
  </NModal>
</template>

<style scoped>
.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--layout-page-gutter, 24px);
  height: var(--header-height, 60px);
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
  gap: 16px;
}
.header-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  min-width: 0;
}

.jnclub-breadcrumb :deep(.n-breadcrumb-item__link) {
  cursor: pointer;
  font-size: var(--fs-md);
}
.breadcrumb-current :deep(.n-breadcrumb-item__link) {
  font-weight: 600;
  color: var(--text-1);
}

.refresh-btn {
  color: var(--text-2);
  flex-shrink: 0;
}
.refresh-btn:hover {
  color: var(--text-1);
  background: var(--hover-bg);
}

.theme-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  flex-shrink: 0;
}
.theme-toggle-btn:hover {
  color: var(--text-1);
  background: var(--hover-bg);
}

.user-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.user-row:hover {
  background: var(--hover-bg);
}
.user-avatar {
  flex-shrink: 0;
}
.user-name {
  font-size: var(--fs-md);
  font-weight: 500;
  color: var(--text-1);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-role {
  font-size: var(--fs-2xs);
  color: var(--text-3);
  background: var(--glass-chip-bg);
  padding: 2px 6px;
  border-radius: var(--radius-pill);
}

.notification-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 440px;
  min-height: 120px;
}
.notification-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 2px 6px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.notification-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
}
.notification-subtitle {
  font-size: var(--fs-xs);
  color: var(--text-3);
  margin-right: auto;
}
.notification-mark-all {
  flex-shrink: 0;
}
.notification-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  max-height: 360px;
}
.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-1);
  text-align: left;
  cursor: pointer;
  width: 100%;
  transition: background-color var(--dur) var(--ease);
}
.notification-item:hover {
  background: var(--hover-bg);
}
.notification-item.unread {
  background: var(--brand-soft);
}
.notification-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand);
  margin-top: 6px;
  flex-shrink: 0;
}
.notification-item-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}
.notification-item-title {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-1);
  word-break: break-word;
}
.notification-item.unread .notification-item-title {
  font-weight: 700;
}
.notification-item-content {
  font-size: var(--fs-sm);
  color: var(--text-2);
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notification-item-meta {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.mobile-dir-btn {
  flex-shrink: 0;
}

.profile-content {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0;
}
.profile-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.profile-avatar-large {
  flex-shrink: 0;
}
.profile-name {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1);
}
.profile-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  min-width: 0;
}
.detail-row {
  font-size: var(--fs-sm);
  color: var(--text-2);
  word-break: break-all;
}

/* 面包屑末级不跟随分隔符，避免出现悬空 "/" */
.jnclub-breadcrumb :deep(.n-breadcrumb-item:last-child .n-breadcrumb-item__separator) {
  display: none;
}

@media (max-width: 767px) {
  .home-header {
    padding: 0 12px;
    gap: 8px;
  }
  /*
   * 窄屏遮挡根因：header-right 不收缩（内容约 244px），header-left 为 flex:1 + min-width:0
   * 会被压到只剩目录按钮宽，面包屑从 0 宽容器里溢出绘制，被右侧按钮压住。
   * 这里让左侧真正裁剪，并给末级面包屑省略号。
   */
  .header-left {
    gap: 4px;
    overflow: hidden;
  }
  .jnclub-breadcrumb {
    min-width: 0;
    overflow: hidden;
  }
  /* 注：原此处有 `:first-child { display: none }`，用于在窄屏隐藏品牌根「JNClub」。
     报告 IA-2 已把品牌根从模板删除，首项变为模块名（不应隐藏），故该规则移除；
     窄屏的宽度取舍改由下方 400px 断点按「有无目录项」决定。 */
  .jnclub-breadcrumb :deep(ul) {
    display: flex;
    flex-wrap: nowrap;
    min-width: 0;
  }
  .jnclub-breadcrumb :deep(.n-breadcrumb-item) {
    flex: 0 0 auto;
    min-width: 0;
    white-space: nowrap;
  }
  .jnclub-breadcrumb :deep(.n-breadcrumb-item:last-child) {
    flex: 0 1 auto;
    overflow: hidden;
  }
  .jnclub-breadcrumb :deep(.n-breadcrumb-item:last-child .n-breadcrumb-item__link) {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .user-name,
  .user-role {
    display: none;
  }
  .header-right {
    gap: 2px;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  .header-right::-webkit-scrollbar {
    display: none;
  }
  .user-row {
    flex-shrink: 0;
    padding: 4px;
  }
  /* 低频操作（多选/刷新/主题）收进「更多」，避免窄屏顶栏溢出 */
  .header-secondary {
    display: none;
  }
}

/*
 * 超窄屏（320–400px）：底部 TabBar 已标明当前模块，把宽度让给路径。
 * 用 :has 限定「确实存在路径项」时才隐藏模块名，避免在根目录下把面包屑清空。
 * 注：IA-2 删除品牌根后项序前移一位 —— 模块名由第 2 项变第 1 项、目录由第 3 项变第 2 项，
 * 故选择器同步由 (2):has(~(3)) 改为 (1):has(~(2))。
 */
@media (max-width: 400px) {
  .jnclub-breadcrumb :deep(.n-breadcrumb-item:nth-child(1):has(~ .n-breadcrumb-item:nth-child(2))) {
    display: none;
  }
}
</style>
