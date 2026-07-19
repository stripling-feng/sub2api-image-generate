const THEME_KEY = 'feng_ai_admin_theme'

export const themePresets = [
  { name: '科技蓝', color: '#2563eb' },
  { name: '松石绿', color: '#0f9f8f' },
  { name: '琥珀金', color: '#d97706' },
  { name: '玫瑰粉', color: '#d9467a' },
  { name: '石墨灰', color: '#334155' },
]

export const fontOptions = [
  { label: '默认中文', value: '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif' },
  { label: '优雅衬线', value: '"Noto Serif SC", "PingFang SC", serif' },
  { label: '现代几何', value: '"Trebuchet MS", "PingFang SC", sans-serif' },
  { label: '商务稳重', value: '"Verdana", "Microsoft YaHei", sans-serif' },
]

export const sizeOptions = [
  { label: '大', value: 'large' },
  { label: '默认', value: 'default' },
  { label: '小', value: 'small' },
]

export const layoutOptions = [
  { label: '布局一', value: 'layout-1', title: '经典侧栏', desc: '当前布局，左侧导航 + 内容区' },
  { label: '布局二', value: 'layout-2', title: '分栏导航', desc: '一级导航与二级导航分开显示' },
  { label: '布局三', value: 'layout-3', title: '头部菜单', desc: '顶部横向菜单，无左侧导航' },
  { label: '布局四', value: 'layout-4', title: '头部+侧栏', desc: '顶部一级菜单 + 左侧二级菜单' },
]

export const defaultTheme = {
  primaryColor: '#2563eb',
  fontFamily: '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  componentSize: 'default',
  showBreadcrumb: true,
  showFooter: true,
  showTabs: true,
  layoutMode: 'layout-1',
}

export function loadTheme() {
  const raw = localStorage.getItem(THEME_KEY)
  return raw ? { ...defaultTheme, ...JSON.parse(raw) } : { ...defaultTheme }
}

export function saveTheme(theme) {
  localStorage.setItem(THEME_KEY, JSON.stringify(theme))
}

export function applyTheme(theme) {
  const root = document.documentElement
  const primary = normalizeHex(theme.primaryColor)
  root.style.setProperty('--primary', primary)
  root.style.setProperty('--primary-deep', darken(primary, 0.18))
  root.style.setProperty('--primary-soft', hexToRgba(primary, 0.12))
  root.style.setProperty('--font-family', theme.fontFamily)
  root.style.setProperty('--el-color-primary', primary)
  root.style.setProperty('--el-color-primary-dark-2', darken(primary, 0.12))
  root.style.setProperty('--el-color-primary-light-3', hexToRgba(primary, 0.72))
  root.style.setProperty('--el-color-primary-light-5', hexToRgba(primary, 0.56))
  root.style.setProperty('--el-color-primary-light-7', hexToRgba(primary, 0.32))
  root.style.setProperty('--el-color-primary-light-8', hexToRgba(primary, 0.22))
  root.style.setProperty('--el-color-primary-light-9', hexToRgba(primary, 0.12))
}

function normalizeHex(hex) {
  if (!hex) return '#2563eb'
  return hex.startsWith('#') ? hex : `#${hex}`
}

function hexToRgb(hex) {
  const normalized = normalizeHex(hex).replace('#', '')
  const value =
    normalized.length === 3
      ? normalized.split('').map((char) => char + char).join('')
      : normalized
  const int = Number.parseInt(value, 16)
  return {
    r: (int >> 16) & 255,
    g: (int >> 8) & 255,
    b: int & 255,
  }
}

function hexToRgba(hex, alpha) {
  const { r, g, b } = hexToRgb(hex)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function darken(hex, amount) {
  const { r, g, b } = hexToRgb(hex)
  const next = (value) => Math.max(0, Math.round(value * (1 - amount)))
  return `#${[next(r), next(g), next(b)]
    .map((value) => value.toString(16).padStart(2, '0'))
    .join('')}`
}
