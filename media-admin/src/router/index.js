import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../utils/auth'
import { useAuthStore } from '../stores/auth'

const viewModules = import.meta.glob('../views/**/*.vue')
const ROOT_ROUTE_NAME = 'app-root'
const DASHBOARD_ROUTE_NAME = 'dashboard'
const dynamicRouteNames = new Set()

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/auth/LoginView.vue'),
  },
  {
    path: '/',
    name: ROOT_ROUTE_NAME,
    component: () => import('../layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: DASHBOARD_ROUTE_NAME,
        meta: { title: '控制台' },
        component: () => import('../views/dashboard/DashboardView.vue'),
      },
      {
        path: 'system/docs-config',
        name: 'system-docs-config',
        meta: { title: '前台文档配置' },
        component: () => import('../views/system/PublicDocsConfigView.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to, from, next) => {
  const token = getToken()
  const authStore = useAuthStore()

  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }

  if (to.path === '/login' && token) {
    next('/')
    return
  }

  if (token && !authStore.initialized) {
    try {
      await authStore.bootstrap()
    } catch (error) {
      await authStore.logout()
      next('/login')
      return
    }
  }

  if (token) {
    ensureMenuRoutes(authStore.menuTree || [])
  }

  if (to.path !== '/login') {
    const allowedPaths = new Set(['/dashboard', ...collectAllowedPaths(authStore.menuTree || [])])
    const menus = flattenMenus(authStore.menuTree || [])

    if (to.path !== '/dashboard' && menus.length && !allowedPaths.has(to.path)) {
      next('/dashboard')
      return
    }

    if (to.matched.length === 0 && allowedPaths.has(to.path)) {
      next({ ...to, replace: true })
      return
    }
  }

  next()
})

function ensureMenuRoutes(menuTree) {
  const menuRoutes = collectMenuRoutes(menuTree)

  for (const route of menuRoutes) {
    if (router.hasRoute(route.name)) continue
    router.addRoute(ROOT_ROUTE_NAME, route)
    dynamicRouteNames.add(route.name)
  }
}

function collectMenuRoutes(nodes, result = [], parentPath = '') {
  for (const node of nodes || []) {
    const fullPath = resolveMenuPath(parentPath, node.path)

    if (node.menuType !== 2 && fullPath && node.component && node.component !== 'Layout') {
      const routePath = normalizeChildPath(fullPath)
      const routeName = buildRouteName(node)

      if (routePath && routeName && routePath !== 'dashboard') {
        result.push({
          path: routePath,
          name: routeName,
          meta: { title: node.menuName || '未命名菜单', menuId: node.id },
          component: resolveViewComponent(node.component),
        })
      }
    }

    if (node.children?.length) {
      collectMenuRoutes(node.children, result, fullPath)
    }
  }

  return result
}

function resolveViewComponent(componentPath) {
  const normalizedPath = normalizeComponentPath(componentPath)
  const module = viewModules[normalizedPath]

  if (module) return module

  console.warn(`[router] missing view component: ${componentPath}`)
  return viewModules['../views/error/MissingView.vue'] || (() => import('../views/dashboard/DashboardView.vue'))
}

function normalizeComponentPath(componentPath) {
  const trimmed = String(componentPath || '').trim().replace(/^\/+/, '')
  const withExtension = trimmed.endsWith('.vue') ? trimmed : `${trimmed}.vue`
  return withExtension.startsWith('views/') ? `../${withExtension}` : `../views/${withExtension}`
}

function normalizeRoutePath(path) {
  const normalized = String(path || '').trim().replace(/^\/+/, '')
  return normalized ? `/${normalized}` : '/dashboard'
}

function normalizeChildPath(path) {
  return normalizeRoutePath(path).replace(/^\//, '')
}

function buildRouteName(node) {
  if (node?.id != null) return `menu-${node.id}`
  const path = normalizeChildPath(node?.path)
  return path ? `menu-${path.replace(/\//g, '-')}` : ''
}

function resolveMenuPath(parentPath, currentPath) {
  const parent = String(parentPath || '').replace(/\/+/g, '/').replace(/\/$/, '')
  const current = String(currentPath || '').trim().replace(/^\/+|\/+$/g, '')

  if (!current) return parent
  if (!parent) return `/${current}`
  if (current.includes('/')) return `/${current}`
  return `${parent}/${current}`.replace(/\/+/g, '/')
}

function flattenMenus(nodes, result = []) {
  for (const node of nodes || []) {
    if (node.menuType !== 2) {
      result.push(node)
    }
    flattenMenus(node.children || [], result)
  }
  return result
}

function collectAllowedPaths(nodes, result = [], parentPath = '') {
  for (const node of nodes || []) {
    if (node.menuType === 2) continue

    const fullPath = resolveMenuPath(parentPath, node.path)
    if (fullPath) {
      result.push(fullPath)
    }

    if (node.children?.length) {
      collectAllowedPaths(node.children, result, fullPath)
    }
  }

  return result
}

export function resetDynamicMenuRoutes() {
  for (const routeName of dynamicRouteNames) {
    if (router.hasRoute(routeName)) {
      router.removeRoute(routeName)
    }
  }
  dynamicRouteNames.clear()
}

export default router
