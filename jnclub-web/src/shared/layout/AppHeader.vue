<script setup lang="ts">
/**
 * AppHeader.vue — 主界面顶栏
 * 从 Home.vue 拆出：面包屑、搜索、多选、视图切换、刷新、主题、用户下拉。
 * 用户信息/退出/资料弹窗逻辑一并收口在此，Home 不再维护。
 */
import { ref, h, computed, onMounted, onUnmounted } from 'vue'
import {
  NButton, NIcon, NBadge, NPopover, NBreadcrumb, NBreadcrumbItem, NAvatar, NDropdown, NModal, useDialog,
} from 'naive-ui'
import { FolderOpen, Search, CheckSquare, RefreshCw, Sun, Moon, CircleUser, LogOut, Bell, CheckCheck } from 'lucide-vue-next'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { openMenu } from '../composables/useContextMenu'
import ViewSwitcher, { type ViewMode } from '../../modules/bookmark/components/ViewSwitcher.vue'

const props = defineProps<{
  isDark: boolean
  activeModule: 'bookmarks' | 'notes' | 'files' | 'vault'
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
const dialog = useDialog()
const router = useRouter()

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
    TODO_REMIND: '待办提醒',
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
  if (n.refType === 'todo' && n.refId) {
    router.push('/todos')
  }
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
        <NBreadcrumbItem @click="handleBreadcrumbRoot">JNClub</NBreadcrumbItem>
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
          <div v-if="notificationLoading" class="notification-status">加载中…</div>
          <div v-else-if="notificationList.length === 0" class="notification-status">暂无通知</div>
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
        class="refresh-btn"
        :type="batchMode ? 'primary' : 'default'"
        :title="batchMode ? '退出多选' : '多选'"
        @click="emit('update:batchMode', !batchMode)"
      >
        <template #icon><NIcon :component="CheckSquare" size="16" /></template>
      </NButton>
      <ViewSwitcher
        v-if="activeModule === 'bookmarks' || activeModule === 'notes'"
        :model-value="viewMode"
        @update:model-value="(v: ViewMode) => emit('update:viewMode', v)"
      />
      <NButton quaternary circle size="small" @click="emit('refresh')" class="refresh-btn jnclub-bouncy" title="刷新">
        <template #icon><NIcon :component="RefreshCw" size="16" /></template>
      </NButton>

      <button type="button" class="theme-toggle-btn jnclub-bouncy" @click="emit('toggle-theme')" title="切换暗色模式">
        <NIcon :component="isDark ? Sun : Moon" size="16" />
      </button>

      <NDropdown :options="userDropdownOptions" @select="handleUserDropdown" placement="bottom-end" trigger="click">
        <div class="user-row jnclub-bouncy" @contextmenu.prevent="openMenu($event, userDropdownOptions, handleUserDropdown)">
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
  font-size: 10px;
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
.notification-status {
  padding: 28px 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--text-3);
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
  transition: background-color 0.2s;
}
.notification-item:hover {
  background: var(--hover-bg);
}
.notification-item.unread {
  background: var(--primary-bg, rgba(24, 160, 88, 0.06));
}
.notification-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary-color, #18a058);
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

@media (max-width: 767px) {
  .home-header {
    padding: 0 12px;
  }
  .user-name,
  .user-role {
    display: none;
  }
  .header-right {
    gap: 4px;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  .header-right::-webkit-scrollbar {
    display: none;
  }
  .user-row {
    flex-shrink: 0;
  }
}
</style>
