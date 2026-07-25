<template>
  <component :is="currentLayoutComponent" :state="layoutState" :actions="layoutActions" :theme-drawer-visible="themeDrawerVisible" @update:theme-drawer-visible="themeDrawerVisible = $event" />

  <el-dialog v-model="profileDialogVisible" title="用户信息" width="560px">
    <el-form label-width="80px">
      <el-form-item label="用户名">
        <el-input v-model="profileForm.username" disabled />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="profileForm.nickname" disabled />
      </el-form-item>
      <el-form-item label="部门">
        <el-input :model-value="getDeptName(profileForm.deptId)" disabled />
      </el-form-item>
      <el-form-item label="岗位">
        <el-input :model-value="getPostName(profileForm.postId)" disabled />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="profileForm.phone" disabled />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="profileForm.email" disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="profileDialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="passwordDialogVisible" title="修改密码" width="460px">
    <el-form label-width="100px" @submit.prevent="handleChangePassword">
      <el-form-item label="旧密码">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" @keyup.enter="handleChangePassword" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" @keyup.enter="handleChangePassword" />
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" @keyup.enter="handleChangePassword" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleChangePassword">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changePasswordApi } from '../api/auth'
import { deptApi, postApi } from '../api/system'
import ClassicSidebarLayout from '../components/layout/ClassicSidebarLayout.vue'
import SplitSidebarLayout from '../components/layout/SplitSidebarLayout.vue'
import TopMenuLayout from '../components/layout/TopMenuLayout.vue'
import TopSidebarLayout from '../components/layout/TopSidebarLayout.vue'
import { resetDynamicMenuRoutes } from '../router'
import { useAuthStore } from '../stores/auth'
import { useTabsStore } from '../stores/tabs'
import { useThemeStore } from '../stores/theme'
import { fontOptions, layoutOptions, sizeOptions, themePresets } from '../utils/theme'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const tabsStore = useTabsStore()
const themeStore = useThemeStore()

const themeDrawerVisible = ref(false)
const profileDialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const viewRefreshSeed = ref(0)
const posts = ref([])
const deptFlat = ref([])
const profileOptionsLoaded = ref(false)
const profileOptionsLoading = ref(false)
const selectedTopMenuId = ref(null)

const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const profileForm = reactive({ id: null, username: '', nickname: '', deptId: null, postId: null, phone: '', email: '' })
const contextMenu = reactive({ visible: false, x: 0, y: 0, path: '/dashboard' })

const menus = computed(() => normalizeMenuTree(authStore.menuTree || []))
const dashboardMenu = computed(() => ({ id: 'dashboard', menuName: '工作台', path: 'dashboard', fullPath: '/dashboard', children: [] }))
const topMenuNodes = computed(() => [dashboardMenu.value, ...menus.value])
const activeTopMenu = computed(() => {
  if (selectedTopMenuId.value) {
    const selected = findTopLevelMenuById(topMenuNodes.value, selectedTopMenuId.value)
    if (selected) return selected
  }
  if (route.path === '/dashboard') return dashboardMenu.value
  return findTopLevelMenuByPath(menus.value, route.path)
})
const hasSecondaryMenus = computed(() => Boolean(activeTopMenu.value?.children?.length))
const secondaryMenus = computed(() => (hasSecondaryMenus.value ? activeTopMenu.value.children : []))
const currentParent = computed(() => {
  const parents = findMenuAncestors(menus.value, route.path)
  return parents[0]?.menuName || ''
})
const defaultOpeneds = computed(() => findMenuAncestors(menus.value, route.path).map((item) => String(item.id)))
const secondaryDefaultOpeneds = computed(() => findMenuAncestors(secondaryMenus.value, route.path).map((item) => String(item.id)))
const workspaceTitle = computed(() => route.meta.title || '工作台')
const siteName = computed(() => authStore.siteConfig?.siteName || 'Feng AI Admin')
const brandMark = computed(() => siteName.value.slice(0, 2).toUpperCase())
const footerText = computed(() => authStore.siteConfig?.copyright || 'Copyright © Feng AI Admin')
const isCurrentTabFixed = computed(() => isFixedTab(tabsStore.activePath))
const hasClosableOtherTabs = computed(() => canCloseOthers(tabsStore.activePath))
const hasClosableLeftTabs = computed(() => canCloseLeft(tabsStore.activePath))
const hasClosableRightTabs = computed(() => canCloseRight(tabsStore.activePath))
const viewRenderKey = computed(() => `${route.fullPath}:${viewRefreshSeed.value}`)
const breadcrumbLocked = computed(() => ['layout-3', 'layout-4'].includes(themeStore.layoutMode))
const showBreadcrumb = computed(() => !breadcrumbLocked.value && themeStore.showBreadcrumb)

const layoutMap = {
  'layout-1': ClassicSidebarLayout,
  'layout-2': SplitSidebarLayout,
  'layout-3': TopMenuLayout,
  'layout-4': TopSidebarLayout,
}

const currentLayoutComponent = computed(() => layoutMap[themeStore.layoutMode] || ClassicSidebarLayout)

const layoutState = computed(() => ({
  route,
  authStore,
  tabsStore,
  themeStore,
  menus: menus.value,
  topMenuNodes: topMenuNodes.value,
  activeTopMenu: activeTopMenu.value,
  hasSecondaryMenus: hasSecondaryMenus.value,
  secondaryMenus: secondaryMenus.value,
  currentParent: currentParent.value,
  defaultOpeneds: defaultOpeneds.value,
  secondaryDefaultOpeneds: secondaryDefaultOpeneds.value,
  workspaceTitle: workspaceTitle.value,
  siteName: siteName.value,
  brandMark: brandMark.value,
  footerText: footerText.value,
  showBreadcrumb: showBreadcrumb.value,
  breadcrumbLocked: breadcrumbLocked.value,
  isCurrentTabFixed: isCurrentTabFixed.value,
  hasClosableOtherTabs: hasClosableOtherTabs.value,
  hasClosableLeftTabs: hasClosableLeftTabs.value,
  hasClosableRightTabs: hasClosableRightTabs.value,
  viewRenderKey: viewRenderKey.value,
  contextMenu,
  layoutOptions,
  themePresets,
  fontOptions,
  sizeOptions,
}))

const layoutActions = {
  toggleFullscreen,
  openProfileDialog,
  openChangePasswordDialog,
  logout,
  handleTabsWheel,
  handleTabClick,
  handleTabRemove,
  handleGlobalTabCommand,
  openContextMenu,
  runTabAction,
  isFixedTab,
  canCloseOthers,
  canCloseLeft,
  canCloseRight,
  handleColorChange,
  navigateMenuRoot,
  isTopMenuActive,
}

watch(
  () => route.fullPath,
  () => {
    if (route.path !== '/login') tabsStore.ensureTab(route)
    hideContextMenu()
  },
  { immediate: true },
)

watch(
  [() => route.path, menus],
  () => {
    if (route.path === '/dashboard') {
      selectedTopMenuId.value = 'dashboard'
      return
    }
    const matchedTopMenu = findTopLevelMenuByPath(menus.value, route.path)
    if (matchedTopMenu) selectedTopMenuId.value = matchedTopMenu.id
  },
  { immediate: true },
)

async function logout() {
  try {
    await ElMessageBox.confirm('确认退出登录吗？', '提示', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  resetDynamicMenuRoutes()
  await authStore.logout()
  router.push('/login')
}

function normalizeMenuTree(nodes, parentPath = '') {
  return nodes
    .filter((item) => item.menuType !== 2)
    .map((item) => {
      const fullPath = resolveMenuPath(parentPath, item.path)
      return {
        ...item,
        fullPath,
        children: normalizeMenuTree(item.children || [], fullPath),
      }
    })
}

function normalizeMenuPath(item) {
  return item?.fullPath || (item?.path ? `/${item.path}` : '')
}

function resolveMenuPath(parentPath, currentPath) {
  const parent = String(parentPath || '').replace(/\/+/g, '/').replace(/\/$/, '')
  const current = String(currentPath || '').trim().replace(/^\/+|\/+$/g, '')

  if (!current) return parent
  if (!parent) return `/${current}`
  if (current.includes('/')) return `/${current}`
  return `${parent}/${current}`.replace(/\/+/g, '/')
}

function findTopLevelMenuByPath(nodes, path) {
  for (const node of nodes) {
    if (normalizeMenuPath(node) === path) return node
    if (findMenuNodeByPath(node.children || [], path)) return node
  }
  return null
}

function findTopLevelMenuById(nodes, id) {
  return nodes.find((node) => node.id === id) || null
}

function findMenuNodeByPath(nodes, path) {
  for (const node of nodes) {
    if (normalizeMenuPath(node) === path) return node
    const result = findMenuNodeByPath(node.children || [], path)
    if (result) return result
  }
  return null
}

function findMenuAncestors(nodes, path, parents = []) {
  for (const node of nodes) {
    const nextParents = node.path ? [...parents, node] : parents
    if (node.path && normalizeMenuPath(node) === path) return parents
    const result = findMenuAncestors(node.children || [], path, nextParents)
    if (result.length) return result
  }
  return []
}

function flattenDeptTree(nodes, result = []) {
  for (const node of nodes || []) {
    result.push({ id: node.id, menuName: node.menuName })
    if (node.children?.length) flattenDeptTree(node.children, result)
  }
  return result
}

function getDeptName(deptId) {
  if (!deptId) return '-'
  return deptFlat.value.find((item) => item.id === deptId)?.menuName || '-'
}

function getPostName(postId) {
  if (!postId) return '-'
  return posts.value.find((item) => item.id === postId)?.postName || '-'
}

function resolveFirstMenuPath(item) {
  if (!item) return '/dashboard'
  if (normalizeMenuPath(item)) return normalizeMenuPath(item)
  for (const child of item.children || []) {
    const target = resolveFirstMenuPath(child)
    if (target) return target
  }
  return '/dashboard'
}

function isTopMenuActive(item) {
  if (item.path === 'dashboard') return route.path === '/dashboard' || selectedTopMenuId.value === 'dashboard'
  return activeTopMenu.value?.id === item.id
}

function navigateMenuRoot(item) {
  selectedTopMenuId.value = item.id
  if (item.children?.length) return
  const target = resolveFirstMenuPath(item)
  if (target && target !== route.path) router.push(target)
}

async function loadProfileOptions() {
  if (profileOptionsLoaded.value || profileOptionsLoading.value) return
  profileOptionsLoading.value = true
  const [postList, deptTree] = await Promise.all([postApi.options(), deptApi.tree()])
  posts.value = postList || []
  deptFlat.value = flattenDeptTree(deptTree || [])
  profileOptionsLoaded.value = true
  profileOptionsLoading.value = false
}

async function openProfileDialog() {
  await loadProfileOptions()
  const info = authStore.userInfo || {}
  profileForm.id = info.id || null
  profileForm.username = info.username || ''
  profileForm.nickname = info.nickname || ''
  profileForm.deptId = info.deptId || null
  profileForm.postId = info.postId || null
  profileForm.phone = info.phone || ''
  profileForm.email = info.email || ''
  profileDialogVisible.value = true
}

function openChangePasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

async function handleChangePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.warning('请完整填写密码信息')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  await changePasswordApi(passwordForm)
  ElMessage.success('密码修改成功，请重新登录')
  passwordDialogVisible.value = false
  resetDynamicMenuRoutes()
  await authStore.logout()
  router.push('/login')
}

function handleTabClick(path) {
  if (path && path !== route.path) router.push(path)
}

function handleTabRemove(path) {
  const nextPath = tabsStore.removeTab(path)
  if (nextPath !== route.path) router.push(nextPath)
}

function handleGlobalTabCommand(command) {
  runTabAction(command, tabsStore.activePath)
}

function handleTabsWheel(event) {
  const target = event.currentTarget
  if (!target) return
  const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
  target.scrollLeft += delta
}

function isFixedTab(path) {
  return path === '/dashboard'
}

function canCloseLeft(path) {
  const currentIndex = tabsStore.items.findIndex((item) => item.path === path)
  return tabsStore.items.some((item, index) => index < currentIndex && item.closable)
}

function canCloseRight(path) {
  const currentIndex = tabsStore.items.findIndex((item) => item.path === path)
  return tabsStore.items.some((item, index) => index > currentIndex && item.closable)
}

function canCloseOthers(path) {
  return tabsStore.items.some((item) => item.path !== path && item.closable)
}

async function refreshTab(path) {
  hideContextMenu()
  if (route.path !== path) await router.push(path)
  await nextTick()
  viewRefreshSeed.value += 1
}

async function runTabAction(action, path) {
  hideContextMenu()
  switch (action) {
    case 'refresh':
      await refreshTab(path)
      break
    case 'closeCurrent': {
      if (isFixedTab(path)) return
      const nextPath = tabsStore.removeTab(path)
      if (nextPath !== route.path) await router.push(nextPath)
      break
    }
    case 'closeLeft': {
      if (!canCloseLeft(path)) return
      const nextPath = tabsStore.removeLeft(path)
      if (nextPath !== route.path) await router.push(nextPath)
      break
    }
    case 'closeOthers': {
      if (!canCloseOthers(path)) return
      const nextPath = tabsStore.removeOthers(path)
      if (nextPath !== route.path) await router.push(nextPath)
      break
    }
    case 'closeRight': {
      if (!canCloseRight(path)) return
      const nextPath = tabsStore.removeRight(path)
      if (nextPath !== route.path) await router.push(nextPath)
      break
    }
    case 'closeAll': {
      const nextPath = tabsStore.removeAll()
      if (nextPath !== route.path) await router.push(nextPath)
      break
    }
    default:
      break
  }
}

function openContextMenu(event, path) {
  contextMenu.visible = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.path = path
}

function hideContextMenu() {
  contextMenu.visible = false
}

function handleColorChange(value) {
  if (!value) return
  themeStore.updateTheme({ primaryColor: value })
}

async function toggleFullscreen() {
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen()
    return
  }
  await document.exitFullscreen()
}

onMounted(() => {
  window.addEventListener('click', hideContextMenu)
  window.addEventListener('scroll', hideContextMenu, true)
  window.addEventListener('resize', hideContextMenu)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', hideContextMenu)
  window.removeEventListener('scroll', hideContextMenu, true)
  window.removeEventListener('resize', hideContextMenu)
})
</script>
