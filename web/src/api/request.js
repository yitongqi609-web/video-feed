import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const http = axios.create({ baseURL: '/api', timeout: 10000 })

// 所有请求自动携带 Access Token
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) config.headers.Authorization = 'Bearer ' + auth.accessToken
  return config
})

// 业务响应统一拆包：code=0 返回 data，否则 reject
http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body.code !== 0) return Promise.reject(new Error(body.message || '请求失败'))
    return body.data
  },
  async (error) => {
    const { response, config } = error

    // Access Token 过期（401）：静默用 Refresh Token 续期后重放原请求
    if (response && response.status === 401 && config && !config._retried && !config.url.includes('/auth/')) {
      try {
        if (!refreshing) {
          refreshing = doRefresh().finally(() => { refreshing = null })
        }
        await refreshing // 并发 401 共享同一次刷新（前端 single-flight）
        config._retried = true
        return http(config)
      } catch (e) {
        const auth = useAuthStore()
        auth.clear()
        router.push('/login')
        return Promise.reject(new Error('登录已过期，请重新登录'))
      }
    }

    const msg = (response && response.data && response.data.message) || error.message || '网络异常'
    if (window.ElementPlus) {
      // 轻提示，不打断操作流
    }
    return Promise.reject(new Error(msg))
  }
)

let refreshing = null

async function doRefresh() {
  const auth = useAuthStore()
  if (!auth.refreshToken) throw new Error('无刷新令牌')
  const { data: body } = await axios.post('/api/auth/refresh', { refreshToken: auth.refreshToken })
  if (body.code !== 0) throw new Error(body.message || '刷新失败')
  auth.setTokens(body.data)
  return true
}

export default http
