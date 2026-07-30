import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export interface UserInfo {
  id: string
  username: string
  nickname: string
  avatar: string
  email?: string
}

export const useUserStore = defineStore('user', () => {
  const userinfo = ref<UserInfo | null>(null)
  const isLoggedIn = ref(false)
  const token = ref<string | null>(null)

  const initToken = () => {
    const urlParams = new URLSearchParams(window.location.search)
    const urlToken = urlParams.get('token')
    if (urlToken) {
      token.value = urlToken
      localStorage.setItem('jn-token', urlToken)
      window.history.replaceState({}, '', window.location.pathname)
    } else {
      token.value = localStorage.getItem('jn-token')
    }
    if (token.value) {
      axios.defaults.headers.common['jn-token'] = token.value
    }
  }

  const fetchUserinfo = async () => {
    initToken()
    if (!token.value) throw new Error('未登录')
    try {
      const res = await axios.get('/api/auth/userinfo')
      if (res.data.code === 200) {
        userinfo.value = res.data.data
        isLoggedIn.value = true
      } else {
        isLoggedIn.value = false
        token.value = null
        localStorage.removeItem('jn-token')
        throw new Error(res.data.message || '获取用户信息失败')
      }
    } catch (e) {
      isLoggedIn.value = false
      token.value = null
      localStorage.removeItem('jn-token')
      delete axios.defaults.headers.common['jn-token']
      throw e
    }
  }

  const logout = async () => {
    let ssoLogoutUrl: string | null = null
    try {
      const res = await axios.post('/api/auth/logout')
      if (res.data.code === 200 && res.data.data?.ssoLogoutUrl) {
        ssoLogoutUrl = res.data.data.ssoLogoutUrl
      }
    } catch { /* 忽略 */ }

    userinfo.value = null
    isLoggedIn.value = false
    token.value = null
    localStorage.removeItem('jn-token')
    delete axios.defaults.headers.common['jn-token']

    if (ssoLogoutUrl) {
      window.location.href = ssoLogoutUrl
    }
  }

  return { userinfo, isLoggedIn, token, initToken, fetchUserinfo, logout }
})
