<script setup lang="ts">
import { ref, h } from 'vue'
import { NLayout, NLayoutSider, NLayoutContent, NMenu, NButton, NIcon, NSpace, NAvatar, NDropdown } from 'naive-ui'
import { SunnyOutline, MoonOutline, LogOutOutline, BookmarkOutline, DocumentTextOutline } from '@vicons/ionicons5'
import { useUserStore } from '../stores/user'
import type { MenuOption } from 'naive-ui'

defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const userStore = useUserStore()
const collapsed = ref(false)

const menuOptions: MenuOption[] = [
  {
    label: '收藏夹',
    key: 'bookmarks',
    icon: () => h(NIcon, null, { default: () => h(BookmarkOutline) }),
  },
  {
    label: '便签',
    key: 'notes',
    icon: () => h(NIcon, null, { default: () => h(DocumentTextOutline) }),
  },
]

const handleLogout = () => {
  // 清除本地 localStorage
  localStorage.removeItem('jn-token')
  delete axios.defaults.headers.common['jn-token']
  
  // 直接跳转到后端 /sso/logout
  // 后端会：1. 清除 JNClub session  2. 302 到 SSO signout  3. SSO 退出后跳回前端首页
  window.location.href = '/sso/logout'
}

const userDropdownOptions = [
  {
    label: '退出登录',
    key: 'logout',
    icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }),
  },
]

const handleUserDropdown = (key: string) => {
  if (key === 'logout') {
    handleLogout()
  }
}
</script>

<template>
  <NLayout has-sider style="height: 100vh">
    <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="240"
      :collapsed="collapsed"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
    >
      <div class="sider-header">
        <h2 v-if="!collapsed">JNClub</h2>
        <h2 v-else>JN</h2>
      </div>
      <NMenu
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :options="menuOptions"
        default-value="bookmarks"
      />
    </NLayoutSider>
    <NLayout>
      <NLayoutHeader bordered style="height: 64px; padding: 0 24px; display: flex; align-items: center; justify-content: space-between;">
        <div></div>
        <NSpace align="center">
          <NButton quaternary circle @click="emit('toggle-theme')">
            <template #icon>
              <NIcon :component="isDark ? SunnyOutline : MoonOutline" />
            </template>
          </NButton>
          <NDropdown :options="userDropdownOptions" @select="handleUserDropdown">
            <NSpace align="center" style="cursor: pointer;">
              <NAvatar
                round
                size="small"
                :src="userStore.userinfo?.avatar"
              >
                {{ userStore.userinfo?.nickname?.charAt(0) || 'U' }}
              </NAvatar>
              <span>{{ userStore.userinfo?.nickname || '用户' }}</span>
            </NSpace>
          </NDropdown>
        </NSpace>
      </NLayoutHeader>
      <NLayoutContent style="height: calc(100vh - 64px); padding: 24px;">
        <router-view />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.sider-header {
  padding: 16px;
  text-align: center;
  border-bottom: 1px solid var(--border-color);
}

.sider-header h2 {
  margin: 0;
  color: var(--primary-color);
  font-size: 24px;
}
</style>
