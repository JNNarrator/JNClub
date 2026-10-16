<script setup lang="ts">
/**
 * RecycleLayout.vue — 回收站主壳（M4-1：由 shared/layout/ 移入模块内）
 * 只有 /recycle 一个消费点，且与 RecycleView 的 props/emit 深度绑定，
 * 属回收站功能自身的壳而非跨模块复用的布局原语，故归入本模块。
 * 套用与主界面一致的壳（MainLayout 左侧导航/移动端 TabBar）+ 统一 JPageShell 页面壳。
 */
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import MainLayout from '../../../shared/layout/MainLayout.vue'
import JPageShell from '../../../shared/layout/JPageShell.vue'
import RecycleView from '../views/RecycleView.vue'
import HelpDrawer from '../../../shared/components/HelpDrawer.vue'
import JPageHeader from '../../../shared/components/ui/JPageHeader.vue'
import { useAppShortcuts } from '../../../shared/composables/useAppShortcuts'
import { useSmartBack } from '../../../shared/composables/useSmartBack'
import { useVaultStore } from '../stores/vault'
import type { ModuleKey } from '../../../shared/types/nav'

const { isDark } = defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const router = useRouter()

/** 顶栏「返回」：回到真实来路；直接打开回收站 URL 时退回收藏夹（报告 IA-5） */
const { goBack } = useSmartBack()

/**
 * 注：模板里 <MainLayout :active-module="'bookmarks'"> 的 active-module 只是占位 ——
 * 回收站是路由页，激活态与侧栏副标题按 route.name 判定（见 SideNav.vue 的 moduleLabel）；
 * 类型上只能是 ModuleKey，故传默认模块。
 *
 * ⚠ 这类说明不要写成 <template> 根层级的 HTML 注释：那会让本组件从「单根」变成
 * 「多根 Fragment」（编译产物出现 DEV_ROOT_FRAGMENT），而 App.vue 的
 * <Transition name="page-fade" mode="out-in"> 只能作用于单根 —— 一旦多根，
 * out-in 的「先离场再进场」状态机卡死：旧组件已卸载、新组件永不挂载，
 * 表现为离开本页后整页白屏。
 */

/** 顶栏刷新计数：递增触发 RecycleView 重新拉取 */
const refreshTick = ref(0)
const handleRefresh = () => { refreshTick.value++ }

/** 回收站内点左侧模块：跳回主界面并切换到对应模块（AppWrapper 从 URL query.module 定位） */
const handleModuleChange = (module: ModuleKey) => {
  router.push({ path: '/', query: { module } })
}

/** 快捷键帮助面板 */
const showHelp = ref(false)

/** 全局快捷键（回收站壳同样生效：模块切换/主题/锁定密码库/帮助） */
useAppShortcuts({
  onToggleTheme: () => emit('toggle-theme'),
  onModuleChange: handleModuleChange,
  onOpenHelp: () => { showHelp.value = true },
  onLockVault: () => {
    const vault = useVaultStore()
    if (vault.masterStatus?.unlocked) vault.lock()
  },
})
</script>

<template>
  <MainLayout
    :is-dark="isDark"
    :active-module="'bookmarks'"
    @module-change="handleModuleChange"
  >
    <JPageShell>
      <template #header>
        <JPageHeader
          title="回收站"
          subtitle="软删除条目统一管理"
          refresh
          theme
          back
          :is-dark="isDark"
          @refresh="handleRefresh"
          @toggle-theme="emit('toggle-theme')"
          @back="goBack"
        />
      </template>

      <RecycleView :refresh="refreshTick" />
    </JPageShell>

    <HelpDrawer :show="showHelp" @close="showHelp = false" />
  </MainLayout>
</template>
