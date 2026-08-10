import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = createRouter({
  history: createWebHistory('/jnclub/'),
  routes: [
    {
      path: '/',
      name: 'app',
      component: () => import('../views/AppWrapper.vue'),
    },
    {
      path: '/welcome',
      name: 'welcome',
      component: () => import('../views/Welcome.vue'),
    },
    {
      path: '/sso/login',
      name: 'sso-callback',
      component: () => import('../views/SsoCallback.vue'),
    },
    // 便签独立页面（新标签页打开）：新建 / 查看（编辑+预览一体，页内切换）
    {
      path: '/notes/new',
      name: 'note-create',
      component: () => import('../../modules/bookmark/views/NoteEditorPage.vue'),
    },
    {
      path: '/notes/:id',
      name: 'note-view',
      component: () => import('../../modules/bookmark/views/NoteEditorPage.vue'),
    },
    // 回收站独立页面
    {
      path: '/recycle',
      name: 'recycle',
      component: () => import('../../modules/bookmark/views/RecycleView.vue'),
    },
  ],
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()

  // SSO 回调页不需要登录
  if (to.name === 'sso-callback') {
    next()
    return
  }

  // 欢迎页：已登录直达首页；未登录可停留（引导去登录）
  if (to.name === 'welcome') {
    if (userStore.isLoggedIn) {
      next({ path: '/' })
      return
    }
    try {
      await userStore.fetchUserinfo()
      next({ path: '/' })
    } catch {
      next()
    }
    return
  }

  // SSO 登录失败
  if (to.query.error) {
    next({ name: 'sso-callback', query: { error: to.query.error } })
    return
  }

  // SSO 登录成功后 URL 带有 token 参数
  if (to.query.token) {
    userStore.initToken()
    next({ path: '/', query: {}, replace: true })
    return
  }

  // 检查登录状态：未登录先到欢迎页，其他操作引导去登录
  if (!userStore.isLoggedIn) {
    try {
      await userStore.fetchUserinfo()
    } catch (e) {
      next({ name: 'welcome' })
      return
    }
  }

  next()
})

export default router
