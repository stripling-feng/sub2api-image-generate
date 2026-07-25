import { defineStore } from 'pinia'

const TABS_STORAGE_KEY = 'feng_admin_tabs'

const createDashboardTab = () => ({
  path: '/dashboard',
  title: '工作台',
  closable: false,
})

function normalizeTab(item) {
  return {
    path: item.path,
    title: item.title,
    closable: item.path !== '/dashboard',
  }
}

function getDefaultState() {
  return {
    activePath: '/dashboard',
    items: [createDashboardTab()],
  }
}

function loadState() {
  const fallback = getDefaultState()
  try {
    const raw = localStorage.getItem(TABS_STORAGE_KEY)
    if (!raw) {
      return fallback
    }
    const parsed = JSON.parse(raw)
    const items = Array.isArray(parsed?.items)
      ? parsed.items
          .filter((item) => item?.path && item?.title)
          .map((item) => normalizeTab(item))
      : []
    const dashboard = items.find((item) => item.path === '/dashboard') || createDashboardTab()
    const deduped = [dashboard]
    for (const item of items) {
      if (item.path !== '/dashboard' && !deduped.find((tab) => tab.path === item.path)) {
        deduped.push(item)
      }
    }
    return {
      activePath: deduped.find((item) => item.path === parsed?.activePath)?.path || '/dashboard',
      items: deduped,
    }
  } catch {
    return fallback
  }
}

function persistState(state) {
  localStorage.setItem(
    TABS_STORAGE_KEY,
    JSON.stringify({
      activePath: state.activePath,
      items: state.items.map((item) => normalizeTab(item)),
    }),
  )
}

export const useTabsStore = defineStore('tabs', {
  state: () => loadState(),
  actions: {
    ensureTab(route) {
      const path = route.path
      const title = route.meta?.title || '未命名页'
      const existing = this.items.find((item) => item.path === path)
      if (!existing) {
        this.items.push(normalizeTab({ path, title }))
      } else if (existing.title !== title) {
        existing.title = title
      }
      this.activePath = path
      persistState(this)
    },
    setActive(path) {
      this.activePath = path
      persistState(this)
      return path
    },
    removeTab(path) {
      if (path === '/dashboard') {
        this.activePath = '/dashboard'
        persistState(this)
        return '/dashboard'
      }
      const currentIndex = this.items.findIndex((item) => item.path === path)
      if (currentIndex < 0) {
        return this.activePath
      }
      this.items.splice(currentIndex, 1)
      const fallback = this.items[currentIndex - 1] || this.items[currentIndex] || createDashboardTab()
      this.activePath = fallback.path
      persistState(this)
      return fallback.path
    },
    removeLeft(path) {
      const currentIndex = this.items.findIndex((item) => item.path === path)
      if (currentIndex <= 0) {
        this.activePath = path
        persistState(this)
        return path
      }
      this.items = this.items.filter((item, index) => index >= currentIndex || item.path === '/dashboard')
      this.activePath = path
      persistState(this)
      return path
    },
    removeRight(path) {
      const currentIndex = this.items.findIndex((item) => item.path === path)
      if (currentIndex < 0) {
        return this.activePath
      }
      this.items = this.items.filter((item, index) => index <= currentIndex || item.path === '/dashboard')
      this.activePath = path
      persistState(this)
      return path
    },
    removeOthers(path) {
      const target = this.items.find((item) => item.path === path) || createDashboardTab()
      const dashboard = this.items.find((item) => item.path === '/dashboard') || createDashboardTab()
      this.items = path === '/dashboard' ? [dashboard] : [dashboard, normalizeTab(target)]
      this.activePath = path
      persistState(this)
      return path
    },
    removeAll() {
      this.items = [createDashboardTab()]
      this.activePath = '/dashboard'
      persistState(this)
      return '/dashboard'
    },
    reset() {
      this.items = [createDashboardTab()]
      this.activePath = '/dashboard'
      localStorage.removeItem(TABS_STORAGE_KEY)
    },
  },
})
