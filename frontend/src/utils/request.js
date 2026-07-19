import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from './auth'
import router from '../router'

const pendingSubmitMap = new Map()
const recentSubmitMap = new Map()
const submitWindowMs = 1500
const loginExpiredCodes = new Set([401, 401001, 401002])

function shouldGuardSubmit(config) {
  const method = (config.method || 'get').toLowerCase()
  return ['post', 'put', 'delete'].includes(method)
}

function buildSubmitKey(config) {
  const method = (config.method || 'get').toLowerCase()
  const url = config.url || ''
  const params = config.params ? JSON.stringify(config.params) : ''
  const data = config.data ? JSON.stringify(config.data) : ''
  return `${method}:${url}:${params}:${data}`
}

function markRecentSubmit(key) {
  const expiresAt = Date.now() + submitWindowMs
  recentSubmitMap.set(key, expiresAt)
  window.setTimeout(() => {
    if (recentSubmitMap.get(key) === expiresAt) {
      recentSubmitMap.delete(key)
    }
  }, submitWindowMs)
}

function clearSubmitState(config, keepRecent = true) {
  const submitKey = config?.__submitKey
  if (!submitKey) {
    return
  }
  pendingSubmitMap.delete(submitKey)
  if (keepRecent) {
    markRecentSubmit(submitKey)
  }
}

function redirectToLogin() {
  removeToken()
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

const service = axios.create({
  baseURL: '/',
  timeout: 10000,
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  if (shouldGuardSubmit(config)) {
    const submitKey = buildSubmitKey(config)
    const now = Date.now()
    const recentExpiresAt = recentSubmitMap.get(submitKey)
    if (pendingSubmitMap.has(submitKey) || (recentExpiresAt && recentExpiresAt > now)) {
      ElMessage.warning('请勿重复提交')
      return Promise.reject(new axios.Cancel('duplicate submit blocked'))
    }
    pendingSubmitMap.set(submitKey, now)
    config.__submitKey = submitKey
  }

  return config
})

service.interceptors.response.use(
  (response) => {
    clearSubmitState(response.config)
    const res = response.data
    if (res.code !== 200) {
      if (loginExpiredCodes.has(res.code)) {
        redirectToLogin()
        return Promise.reject(new Error(res.message || 'Unauthorized'))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res.data
  },
  (error) => {
    if (axios.isCancel(error)) {
      return Promise.reject(error)
    }

    clearSubmitState(error.config, false)

    if (error.response?.status === 403 || error.response?.status === 401) {
      redirectToLogin()
    }
    ElMessage.error(error.response?.data?.message || error.message || '服务异常')
    return Promise.reject(error)
  },
)

export default service
