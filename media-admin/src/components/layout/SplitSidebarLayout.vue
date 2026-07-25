<template>
  <div class="layout">
    <aside class="sidebar sidebar-rail">
      <div class="sidebar-brand compact">
        <div class="brand-mark">{{ state.brandMark }}</div>
      </div>
      <div class="rail-menu">
        <button v-for="item in state.topMenuNodes" :key="item.id" type="button" class="rail-item" :class="{ active: actions.isTopMenuActive(item) }" @click="actions.navigateMenuRoot(item)">
          <MenuIcon v-if="item.icon" :name="item.icon" />
          <MenuIcon v-else-if="item.path === 'dashboard'" name="svg:dashboard" />
          <span>{{ item.menuName.slice(0, 2) }}</span>
        </button>
      </div>
    </aside>

    <aside v-if="state.hasSecondaryMenus" class="sidebar sidebar-panel">
      <div class="sidebar-brand panel">
        <div class="brand-mark">{{ state.brandMark }}</div>
        <div class="brand-copy">
          <div class="brand-title">{{ state.siteName }}</div>
          <div class="brand-subtitle">{{ state.activeTopMenu?.menuName || '工作台' }}</div>
        </div>
      </div>

      <el-scrollbar class="sidebar-scroll">
        <el-menu :default-active="state.route.path" :default-openeds="state.secondaryDefaultOpeneds" router class="menu">
          <SidebarMenuItem v-for="item in state.secondaryMenus" :key="item.id" :item="item" />
        </el-menu>
      </el-scrollbar>
    </aside>

    <LayoutShell :state="state" :actions="actions" :theme-drawer-visible="themeDrawerVisible" @update:theme-drawer-visible="$emit('update:themeDrawerVisible', $event)" />
  </div>
</template>

<script setup>
import LayoutShell from './LayoutShell.vue'
import MenuIcon from '../MenuIcon.vue'
import SidebarMenuItem from './SidebarMenuItem.vue'

defineProps({
  state: { type: Object, required: true },
  actions: { type: Object, required: true },
  themeDrawerVisible: { type: Boolean, required: true },
})

defineEmits(['update:themeDrawerVisible'])
</script>

<style scoped>
.layout { display: flex; height: 100vh; height: 100dvh; overflow: hidden; background: radial-gradient(circle at top left, rgba(37, 99, 235, 0.08), transparent 26%), linear-gradient(180deg, #f7f9fc 0%, #eef3f9 100%); color: #0f172a; }
.sidebar { position: relative; z-index: 2; display: flex; flex-direction: column; flex-shrink: 0; height: calc(100% - 32px); min-height: 0; margin-top: 16px; margin-bottom: 16px; border: 1px solid rgba(148, 163, 184, 0.18); border-radius: 24px; background: rgba(255, 255, 255, 0.88); color: #0f172a; box-shadow: 0 20px 48px rgba(15, 23, 42, 0.06); backdrop-filter: blur(18px); overflow: hidden; }
.sidebar-rail { width: 78px; margin-left: 16px; margin-right: 12px; align-items: center; }
.sidebar-panel { width: 248px; }
.sidebar-brand { display: flex; align-items: center; gap: 12px; min-height: 76px; padding: 18px 20px; border-bottom: 1px solid rgba(148, 163, 184, 0.16); }
.sidebar-brand.compact { justify-content: center; padding: 18px 0; }
.sidebar-brand.panel { padding: 18px 20px; }
.brand-mark { display: inline-flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 14px; background: linear-gradient(135deg, var(--primary) 0%, var(--primary-deep) 100%); color: #fff; font-size: 15px; font-weight: 700; letter-spacing: .08em; box-shadow: 0 14px 32px rgba(37, 99, 235, 0.24); }
.brand-copy { display: grid; gap: 4px; }
.brand-title { font-size: 15px; font-weight: 700; line-height: 1.2; }
.brand-subtitle { font-size: 12px; color: #64748b; }
.rail-menu { display: grid; gap: 10px; width: 100%; padding: 18px 14px; }
.rail-item { display: inline-flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; width: 100%; height: 48px; border: 1px solid transparent; border-radius: 14px; background: rgba(148, 163, 184, 0.08); color: #64748b; font-size: 11px; font-weight: 600; cursor: pointer; transition: background-color .22s ease, border-color .22s ease, color .22s ease, box-shadow .22s ease; }
.rail-item :deep(.menu-icon) { width: 14px; height: 14px; }
.rail-item:hover, .rail-item.active { border-color: rgba(37, 99, 235, 0.18); background: var(--primary-soft); color: var(--primary); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5); }
.sidebar-scroll { flex: 1; min-height: 0; }
.menu { border-right: 0; background: transparent; }
.sidebar :deep(.el-menu) { border-right: 0; background: transparent; }
.sidebar-panel :deep(.el-sub-menu__title), .sidebar-panel :deep(.el-menu-item) { height: 42px; margin: 4px 12px; border-radius: 12px; color: #334155; line-height: 42px; transition: background-color .22s ease, color .22s ease, box-shadow .22s ease; }
.sidebar-panel :deep(.el-menu-item:hover), .sidebar-panel :deep(.el-sub-menu__title:hover) { background: rgba(148, 163, 184, 0.12); }
.sidebar-panel :deep(.el-menu-item.is-active) { background: var(--primary-soft); color: var(--primary); box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.16); }
.sidebar-panel :deep(.el-menu-item.is-active .el-icon) { color: var(--primary); }
@media (max-width: 1280px) { .sidebar-panel { width: 228px; } }
@media (max-width: 960px) {
  .layout { flex-direction: column; height: auto; min-height: 100vh; min-height: 100dvh; overflow: visible; }
  .sidebar, .sidebar-rail, .sidebar-panel { width: calc(100% - 24px); height: auto; min-height: auto; margin: 12px 12px 0; }
  .rail-menu { grid-template-columns: repeat(auto-fit, minmax(56px, 1fr)); }
}
</style>
