<script setup lang="ts">
import { ref, h } from 'vue'
import { NLayoutSider, NIcon, NAvatar, NDropdown, NModal, NButton, useDialog } from 'naive-ui'
import { Bookmark, StickyNote, Moon, Sun, LogOut, CircleUser, Heart } from 'lucide-vue-next'
import { useUserStore } from '../stores/user'
import NavItem from '../../modules/bookmark/components/NavItem.vue'
import axios from 'axios'

const props = defineProps<{
  isDark: boolean
  activeModule: 'bookmarks' | 'notes'
}>()

const emit = defineEmits<{
  'toggle-theme': []
  'module-change': [module: 'bookmarks' | 'notes']
}>()

const userStore = useUserStore()
const dialog = useDialog()
const collapsed = ref(false)

const navItems = [
  { key: 'bookmarks' as const, icon: Bookmark, label: '收藏夹' },
  { key: 'notes' as const, icon: StickyNote, label: '便签' },
]

const userDropdownOptions = [
  { label: '用户信息', key: 'profile', icon: () => h(NIcon, null, { default: () => h(CircleUser) }) },
  { label: '退出登录', key: 'logout', icon: () => h(NIcon, null, { default: () => h(LogOut) }) },
]

const showProfileModal = ref(false)

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
          if (ssoLogoutUrl) { window.location.href = ssoLogoutUrl; return }
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

/** 新标签页打开 SSO 个人中心,修改资料 */
const goSsoProfile = () => {
  const url = userStore.userinfo?.ssoProfileUrl
  if (url) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}
</script>

<template>
  <NLayoutSider
    bordered collapse-mode="width" :collapsed-width="64" :width="240"
    :collapsed="collapsed" show-trigger="bar"
    :on-update:collapsed="(v: boolean) => collapsed = v"
    class="side-nav sidebar-glow"
  >
    <!-- Logo 区：渐变粉底 + heart -->
    <div :class="['logo-bar', { collapsed }]">
      <div :class="['logo-icon-wrap', { collapsed }]">
        <NIcon :component="Heart" :size="collapsed ? 20 : 18" color="#fff" />
      </div>
      <template v-if="!collapsed">
        <span class="logo-text">JNClub</span>
        <span class="logo-sub">{{ activeModule === 'bookmarks' ? '收藏夹' : '便签' }}</span>
      </template>
    </div>

    <nav class="nav-list">
      <NavItem v-for="item in navItems" :key="item.key"
        :icon="item.icon" :label="item.label"
        :active="activeModule === item.key" :collapsed="collapsed"
        @click="emit('module-change', item.key)"
      />
    </nav>

    <!-- 底部 -->
    <div :class="['sider-footer', { collapsed }]">
      <template v-if="!collapsed">
        <!-- 暗色模式 pill toggle -->
        <button type="button" class="theme-toggle-btn jnclub-bouncy" @click="emit('toggle-theme')">
          <span class="theme-toggle-label">
            <NIcon :component="props.isDark ? Moon : Sun" size="16" />
            暗色模式
          </span>
          <span :class="['theme-pill', { on: props.isDark }]">
            <span class="pill-knob" />
          </span>
        </button>

        <NDropdown :options="userDropdownOptions" @select="handleUserDropdown" placement="top" trigger="click">
          <div class="user-row jnclub-bouncy">
            <NAvatar round size="small" :src="userStore.userinfo?.avatar"
              class="user-avatar">
              <template v-if="!userStore.userinfo?.avatar">
                {{ userStore.userinfo?.nickname?.charAt(0) || 'U' }}
              </template>
            </NAvatar>
            <div class="user-info">
              <span class="user-name">{{ userStore.userinfo?.nickname || '用户' }}</span>
              <span class="user-role">管理员</span>
            </div>
          </div>
        </NDropdown>
      </template>
      <template v-else>
        <div class="footer-compact">
          <button class="compact-btn" @click="emit('toggle-theme')">
            <NIcon :component="props.isDark ? Moon : Sun" size="18" />
          </button>
          <NDropdown :options="userDropdownOptions" @select="handleUserDropdown" placement="right" trigger="click">
            <NAvatar round size="small" :src="userStore.userinfo?.avatar"
              class="user-avatar-compact">
              <template v-if="!userStore.userinfo?.avatar">
                {{ userStore.userinfo?.nickname?.charAt(0) || 'U' }}
              </template>
            </NAvatar>
          </NDropdown>
        </div>
      </template>
    </div>
  </NLayoutSider>

  <!-- 用户信息弹窗 -->
  <NModal v-model:show="showProfileModal" preset="dialog" title="用户信息">
    <div class="profile-content">
      <div class="profile-avatar">
        <NAvatar round :size="64" :src="userStore.userinfo?.avatar"
          class="profile-avatar-large">
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
.side-nav {
  display: flex;
  flex-direction: column;
  transition: width var(--dur) var(--ease);
  overflow: hidden;
  position: relative;
}

/* === Logo 区 === */
.logo-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 20px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  transition: padding var(--dur) var(--ease), justify-content var(--dur) var(--ease);
}
.logo-bar.collapsed {
  padding: 20px 0;
  justify-content: center;
}
.logo-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-1);
  flex-shrink: 0;
}
.logo-icon-wrap.collapsed {
  width: 40px;
  height: 40px;
}
.logo-text {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-1);
  letter-spacing: 1px;
  line-height: 1.2;
}
.logo-sub {
  font-size: 11px;
  color: var(--text-3);
  margin-left: auto;
}

/* === 导航 === */
.nav-list {
  padding: 12px 12px;
  flex-shrink: 0;
}

/* === 底部 === */
.sider-footer {
  margin-top: auto;
  padding: 16px 16px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
  z-index: 1;
  transition: padding var(--dur) var(--ease);
}
.sider-footer.collapsed {
  padding: 16px 0 20px;
  align-items: center;
}

/* 暗色模式 toggle — pill */
.theme-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 12px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-2);
  font-size: 13px;
}
.theme-toggle-btn:hover {
  background: var(--hover-bg);
  color: var(--text-1);
}
.theme-toggle-label {
  display: flex;
  align-items: center;
  gap: 8px;
}
.theme-pill {
  position: relative;
  width: 36px;
  height: 20px;
  border-radius: var(--radius-pill);
  background: var(--hover-bg);
  border: 1px solid var(--border);
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease);
}
.theme-pill.on {
  background: var(--brand);
  border-color: var(--brand);
}
.pill-knob {
  position: absolute;
  left: 2px;
  top: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--bg-card);
  box-shadow: var(--shadow-1);
  border: 1px solid var(--border);
  transition: transform var(--dur) var(--ease-bouncy);
}
.theme-pill.on .pill-knob {
  transform: translateX(16px);
}

/* 用户行 */
.user-row {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
}
.user-row:hover {
  background: var(--hover-bg);
}
.user-avatar {
  background: var(--pink-cherry) !important;
  color: var(--brand) !important;
  flex-shrink: 0;
}
.user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-role {
  font-size: 11px;
  color: var(--text-3);
}

/* 折叠态 */
.footer-compact {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.compact-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-2);
  padding: 6px;
  border-radius: var(--radius-sm);
  transition: background var(--dur) var(--ease);
}
.compact-btn:hover {
  background: var(--hover-bg);
  color: var(--text-1);
}
.user-avatar-compact {
  background: var(--pink-cherry) !important;
  color: var(--brand) !important;
  cursor: pointer;
}

/* 用户信息弹窗 */
.profile-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 16px 0;
}
.profile-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.profile-avatar-large {
  background: var(--pink-cherry) !important;
  color: var(--brand) !important;
  font-size: 28px;
  font-weight: 700;
}
.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
}
.profile-detail {
  width: 100%;
}
.detail-row {
  color: var(--text-2);
  font-size: 14px;
  line-height: 2;
}
</style>
