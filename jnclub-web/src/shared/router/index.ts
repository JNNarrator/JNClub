import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = createRouter({
  history: createWebHistory(),
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

  // SSO 登录失败，跳到回调页显示错误
  if (to.query.error) {
    next({ name: 'sso-callback', query: { error: to.query.error } })
    return
  }

  // SSO 登录成功后，URL 带有 token 参数
  // 先调用 initToken() 存入 localStorage 和 axios header，再继续
  if (to.query.token) {
    userStore.initToken()
    // 清除 URL 中的 token，替换为干净 URL
    next({ path: to.path, query: {}, replace: true })
    return
  }

  // 检查登录状态
  if (!userStore.isLoggedIn) {
    try {
      await userStore.fetchUserinfo()
    } catch (e) {
      // 未登录，跳转后端 SSO 入口
      window.location.href = '/sso/login'
      return
    }
  }

  next()
})

export default router
