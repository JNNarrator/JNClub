import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('../layout/MainLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('../../modules/bookmark/views/Home.vue'),
        },
      ],
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
  
  // 检查登录状态
  if (!userStore.isLoggedIn) {
    try {
      await userStore.fetchUserinfo()
    } catch (e) {
      // 未登录，跳转 SSO
      window.location.href = '/sso/login'
      return
    }
  }
  
  next()
})

export default router
