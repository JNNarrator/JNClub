<script setup lang="ts">
/**
 * AppHeader.vue — 主界面顶栏
 * 从 Home.vue 拆出：面包屑、搜索、多选、视图切换、刷新、主题、用户下拉。
 * 用户信息/退出/资料弹窗逻辑一并收口在此，Home 不再维护。
 */
import { ref, h, computed } from 'vue'
import {
  NButton, NIcon, NBreadcrumb, NBreadcrumbItem, NAvatar, NDropdown, NModal, useDialog,
} from 'naive-ui'
import { FolderOpen, Search, CheckSquare, RefreshCw, Sun, Moon, CircleUser, LogOut } from 'lucide-vue-next'
import axios from 'axios'
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
