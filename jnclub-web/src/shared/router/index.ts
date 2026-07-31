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
      path: '/sso/login',
      name: 'sso-callback',
      component: () => import('../views/SsoCallback.vue'),
    },
  ],
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()

  // SSO 回调页面不需要登录
  if (to.name === 'sso-callback') {
    next()
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

  // 检查登录状态
  if (!userStore.isLoggedIn) {
    try {
      await userStore.fetchUserinfo()
    } catch (e) {
      // 关键修复：跳 JNClub 后端的 SSO 入口，不是全局 /sso/login
      window.location.href = import.meta.env.BASE_URL + 'sso/login'
      return
    }
  }

  next()
})

export default router
