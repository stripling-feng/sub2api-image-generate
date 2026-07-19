<template>
  <div class="layout">
    <LayoutShell
      :state="state"
      :actions="actions"
      :theme-drawer-visible="themeDrawerVisible"
      @update:theme-drawer-visible="$emit('update:themeDrawerVisible', $event)"
    >
      <template #topNav>
        <div class="top-nav-shell">
          <el-menu
            :default-active="state.route.path"
            mode="horizontal"
            router
            menu-trigger="hover"
            class="top-menu"
            style="height: 38px"
          >
            <el-menu-item index="/dashboard">
              <MenuIcon name="svg:dashboard" />
              <span>工作台</span>
            </el-menu-item>
            <SidebarMenuItem
              v-for="item in state.menus"
              :key="item.id"
              :item="item"
            />
          </el-menu>
        </div>
      </template>
    </LayoutShell>
  </div>
</template>

<script setup>
import LayoutShell from "./LayoutShell.vue";
import MenuIcon from "../MenuIcon.vue";
import SidebarMenuItem from "./SidebarMenuItem.vue";

defineProps({
  state: { type: Object, required: true },
  actions: { type: Object, required: true },
  themeDrawerVisible: { type: Boolean, required: true },
});

defineEmits(["update:themeDrawerVisible"]);
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background:
    radial-gradient(
      circle at top left,
      rgba(37, 99, 235, 0.08),
      transparent 26%
    ),
    linear-gradient(180deg, #f7f9fc 0%, #eef3f9 100%);
  color: #0f172a;
}
.top-nav-shell {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  padding: 5px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  background: linear-gradient(
    180deg,
    rgba(248, 250, 252, 0.98) 0%,
    rgba(241, 245, 249, 0.94) 100%
  );
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.86),
    0 10px 24px rgba(15, 23, 42, 0.04);
}
.top-menu {
  flex: 1;
  min-width: 0;
  border-bottom: 0;
  background: transparent;
}
.top-menu :deep(.el-menu--horizontal) {
  border-bottom: 0;
}
.top-menu :deep(.el-menu-item),
.top-menu :deep(.el-sub-menu__title) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  margin: 0 4px 0 0;
  padding: 0 14px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  color: #475569;
  line-height: 38px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
  transition:
    background-color 0.22s ease,
    border-color 0.22s ease,
    color 0.22s ease,
    box-shadow 0.22s ease,
    transform 0.22s ease;
}
.top-menu :deep(.el-menu-item .menu-icon),
.top-menu :deep(.el-sub-menu__title .menu-icon) {
  opacity: 0.82;
}
.top-menu :deep(.el-sub-menu__icon-arrow) {
  position: static;
  margin-left: 2px;
  color: #94a3b8;
  font-size: 12px;
  transform: rotate(0deg);
  transition:
    transform 0.22s ease,
    color 0.22s ease;
}
.top-menu
  :deep(.el-sub-menu.is-opened > .el-sub-menu__title .el-sub-menu__icon-arrow) {
  transform: rotate(180deg);
  color: var(--primary);
}
.top-menu :deep(.el-menu-item:hover),
.top-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.96);
  border-color: rgba(148, 163, 184, 0.22);
  color: #0f172a;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
  transform: translateY(-1px);
}
.top-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(
    135deg,
    rgba(37, 99, 235, 0.16) 0%,
    rgba(37, 99, 235, 0.08) 100%
  );
  border-color: rgba(37, 99, 235, 0.22);
  color: var(--primary);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 10px 20px rgba(37, 99, 235, 0.08);
}
.top-menu :deep(.el-menu-item.is-active .el-icon) {
  color: var(--primary);
}
.top-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title),
.top-menu :deep(.el-sub-menu.is-opened > .el-sub-menu__title) {
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.98) 0%,
    rgba(248, 250, 252, 0.96) 100%
  );
  border-color: rgba(148, 163, 184, 0.2);
  color: #0f172a;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.05);
}
@media (max-width: 960px) {
  .layout {
    height: auto;
    min-height: 100vh;
    min-height: 100dvh;
    overflow: visible;
  }
}
</style>
