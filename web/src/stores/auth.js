import { defineStore } from 'pinia'

/**
 * 登录态：Access / Refresh 双令牌 + 用户信息，持久化到 localStorage
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem('vf.accessToken') || '',
    refreshToken: localStorage.getItem('vf.refreshToken') || '',
    user: JSON.parse(localStorage.getItem('vf.user') || 'null')
  }),
  getters: {
    isLoggedIn: (s) => !!s.accessToken
  },
  actions: {
    setTokens(pair) {
      this.accessToken = pair.accessToken
      this.refreshToken = pair.refreshToken
      if (pair.user) this.user = pair.user
      localStorage.setItem('vf.accessToken', this.accessToken)
      localStorage.setItem('vf.refreshToken', this.refreshToken)
      localStorage.setItem('vf.user', JSON.stringify(this.user))
    },
    clear() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem('vf.accessToken')
      localStorage.removeItem('vf.refreshToken')
      localStorage.removeItem('vf.user')
    }
  }
})
