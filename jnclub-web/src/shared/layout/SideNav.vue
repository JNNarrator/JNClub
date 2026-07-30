<script setup lang="ts">
import { ref, h } from 'vue'
import { NLayoutSider, NIcon, NSwitch, NAvatar, NDropdown, NModal, NButton, useDialog } from 'naive-ui'
import {
  BookmarkOutline,
  DocumentTextOutline,
  MoonOutline,
  SunnyOutline,
  LogOutOutline,
  PersonCircleOutline,
} from '@vicons/ionicons5'
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
  { key: 'bookmarks' as const, icon: BookmarkOutline, label: '收藏夹' },
  { key: 'notes' as const, icon: DocumentTextOutline, label: '便签' },
]

const userDropdownOptions = [
  { label: '用户信息', key: 'profile', icon: () => h(NIcon, null, { default: () => h(PersonCircleOutline) }) },
  { label: '退出登录', key: 'logout', icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }) },
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
</script>

<template>
  <NLayoutSider
    bordered collapse-mode="width" :collapsed-width="64" :width="240"
    :collapsed="collapsed" show-trigger="bar"
    :on-update:collapsed="(v: boolean) => collapsed = v"
    class="side-nav"
  >
    <div :class="['logo-section', { collapsed }]">
      <span v-if="collapsed" class="logo-mark">J</span>
      <template v-else>
        <span class="logo-brand-text">JNClub</span>
        <span class="logo-sub">收藏夹</span>
      </template>
    </div>

    <nav class="nav-list">
      <NavItem v-for="item in navItems" :key="item.key"
        :icon="item.icon" :label="item.label"
        :active="props.activeModule === item.key" :collapsed="collapsed"
        @click="emit('module-change', item.key)"
      />
    </nav>

    <div :class="['sider-footer', { collapsed }]">
      <template v-if="!collapsed">
        <div class="theme-row">
          <NIcon :component="props.isDark ? MoonOutline : SunnyOutline" size="16" />
          <span>暗色模式</span>
          <NSwitch :value="props.isDark" size="small" @update:value="() => emit('toggle-theme')" />
        </div>

        <NDropdown :options="userDropdownOptions" @select="handleUserDropdown" placement="top" trigger="click">
          <div class="user-row">
            <NAvatar round size="small" :src="userStore.userinfo?.avatar"
              :style="{ backgroundColor: 'var(--brand-soft)', color: 'var(--brand)' }">
              {{ userStore.userinfo?.nickname?.charAt(0) || 'U' }}
            </NAvatar>
            <span class="user-name">{{ userStore.userinfo?.nickname || '用户' }}</span>
          </div>
        </NDropdown>
      </template>
      <template v-else>
        <div class="footer-compact">
          <button class="compact-btn" @click="emit('toggle-theme')">
            <NIcon :component="props.isDark ? MoonOutline : SunnyOutline" size="18" />
          </button>
          <NDropdown :options="userDropdownOptions" @select="handleUserDropdown" placement="right" trigger="click">
            <NAvatar round size="small" :src="userStore.userinfo?.avatar"
              :style="{ backgroundColor: 'var(--brand-soft)', color: 'var(--brand)', cursor: 'pointer' }">
              {{ userStore.userinfo?.nickname?.charAt(0) || 'U' }}
            </NAvatar>
          </NDropdown>
        </div>
      </template>
    </div>
  </NLayoutSider>

  <NModal v-model:show="showProfileModal" preset="dialog" title="用户信息">
    <div class="profile-content">
      <div class="profile-avatar">
        <NAvatar round :size="64" :src="userStore.userinfo?.avatar"
          :style="{ backgroundColor: 'var(--brand-soft)', color: 'var(--brand)', fontSize: '28px', fontWeight: 700 }">
          {{ userStore.userinfo?.nickname?.charAt(0) || 'U' }}
        </NAvatar>
        <div class="profile-name">{{ userStore.userinfo?.nickname || '用户' }}</div>
      </div>
      <div class="profile-detail">
        <div class="detail-row">邮箱：{{ userStore.userinfo?.email || userStore.userinfo?.username || '--' }}</div>
      </div>
    </div>
    <template #action>
      <NButton type="error" @click="handleUserDropdown('logout')">
        <template #icon><NIcon :component="LogOutOutline" /></template>
        退出登录
      </NButton>
    </template>
  </NModal>
</template>

<style scoped>
.side-nav { display: flex; flex-direction: column; transition: width var(--dur) var(--ease); overflow: hidden; }
.logo-section { padding: 24px 20px 20px; border-bottom: 1px solid var(--border); transition: padding var(--dur) var(--ease); }
.logo-section.collapsed { padding: 24px 0 20px; text-align: center; }
.logo-mark { font-size: 24px; font-weight: 800; color: var(--brand); display: inline-block; width: 40px; height: 40px; line-height: 40px; border-radius: var(--radius-sm); background: var(--brand-soft); }
.logo-brand-text { font-size: 22px; font-weight: 800; color: var(--brand); letter-spacing: 1px; display: block; }
.logo-sub { font-size: 12px; color: var(--text-3); margin-top: 2px; display: block; }
.nav-list { padding: 12px 12px; flex-shrink: 0; }
.sider-footer { margin-top: auto; padding: 16px 16px 20px; border-top: 1px solid var(--border); display: flex; flex-direction: column; gap: 16px; transition: padding var(--dur) var(--ease); }
.sider-footer.collapsed { padding: 16px 0 20px; align-items: center; }
.theme-row { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-2); padding: 6px 0; }
.theme-row > span { flex: 1; }
.user-row { display: flex; align-items: center; gap: 10px; cursor: pointer; padding: 6px 0; border-radius: var(--radius-sm); transition: background var(--dur) var(--ease); }
.user-row:hover { background: var(--hover-bg); }
.user-name { font-size: 13px; font-weight: 500; color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.footer-compact { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.compact-btn { background: none; border: none; cursor: pointer; color: var(--text-2); padding: 6px; border-radius: var(--radius-sm); transition: background var(--dur) var(--ease); }
.compact-btn:hover { background: var(--hover-bg); color: var(--text-1); }
.profile-content { display: flex; flex-direction: column; align-items: center; gap: 20px; padding: 16px 0; }
.profile-avatar { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.profile-name { font-size: 18px; font-weight: 600; color: var(--text-1); }
.profile-detail { width: 100%; }
.detail-row { color: var(--text-2); font-size: 14px; line-height: 2; }
</style>
