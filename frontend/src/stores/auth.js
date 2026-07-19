import { defineStore } from 'pinia'
import { getToken, setToken, removeToken } from '../utils/auth'
import { currentApi, loginApi, logoutApi } from '../api/auth'
import { useTabsStore } from './tabs'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken() || '',
    userInfo: null,
    permissions: [],
    menuTree: [],
    siteConfig: null,
    initialized: false,
  }),
  actions: {
    applySession(data) {
      this.userInfo = data.userInfo
      this.permissions = data.permissions || []
      this.menuTree = (data.menuTree || []).filter((item) => item.menuType !== 2)
      this.siteConfig = data.siteConfig || null
      if (this.siteConfig?.siteName) {
        document.title = this.siteConfig.siteName
      }
      this.initialized = true
    },
    setSiteConfig(config) {
      this.siteConfig = config
      if (config?.siteName) {
        document.title = config.siteName
      }
    },
    async login(form) {
      const data = await loginApi(form)
      this.token = data.token
      setToken(data.token)
      this.applySession(data)
    },
    async bootstrap() {
      if (!this.token) {
        return
      }
      const data = await currentApi()
      this.applySession(data)
    },
    async logout() {
      try { await logoutApi() } catch (e) { /* ignore */ }
      const tabsStore = useTabsStore()
      this.token = ''
      this.userInfo = null
      this.permissions = []
      this.menuTree = []
      this.siteConfig = null
      this.initialized = false
      tabsStore.reset()
      removeToken()
    },
  },
})
