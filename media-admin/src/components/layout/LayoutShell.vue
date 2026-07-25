<template>
  <main class="main">
    <div class="main-shell">
      <header class="topbar" :class="{ compact: !state.showBreadcrumb }">
        <div class="topbar-row">
          <div
            v-if="state.showBreadcrumb || $slots.topNav"
            class="topbar-main"
            :class="{ 'with-top-nav': !state.showBreadcrumb && $slots.topNav }"
          >
            <el-breadcrumb v-if="state.showBreadcrumb" separator="/">
              <el-breadcrumb-item>首页</el-breadcrumb-item>
              <el-breadcrumb-item v-if="state.currentParent">{{
                state.currentParent
              }}</el-breadcrumb-item>
              <el-breadcrumb-item>{{
                state.workspaceTitle
              }}</el-breadcrumb-item>
            </el-breadcrumb>
            <slot v-else-if="$slots.topNav" name="topNav" />
          </div>

          <div class="topbar-right">
            <el-tooltip content="切换全屏" placement="bottom">
              <button
                class="tool-button"
                type="button"
                @click="actions.toggleFullscreen"
              >
                <el-icon><FullScreen /></el-icon>
              </button>
            </el-tooltip>
            <el-tooltip content="主题设置" placement="bottom">
              <button
                class="tool-button"
                type="button"
                @click="themeDrawerVisible = true"
              >
                <el-icon><Setting /></el-icon>
              </button>
            </el-tooltip>
            <el-dropdown trigger="click">
              <div class="user-entry">
                <div class="user-chip">
                  <div class="user-avatar">
                    {{
                      (state.authStore.userInfo?.nickname || "A").slice(0, 1)
                    }}
                  </div>
                  <div class="user-info">
                    <div class="user-name">
                      {{
                        state.authStore.userInfo?.nickname ||
                        state.authStore.userInfo?.username ||
                        "用户"
                      }}
                    </div>
                    <div class="user-role">系统用户</div>
                  </div>
                </div>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="actions.openProfileDialog"
                    >用户信息</el-dropdown-item
                  >
                  <el-dropdown-item @click="actions.openChangePasswordDialog"
                    >修改密码</el-dropdown-item
                  >
                  <el-dropdown-item @click="actions.logout"
                    >退出登录</el-dropdown-item
                  >
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
        <div v-if="$slots.topNav && state.showBreadcrumb" class="top-nav-row">
          <slot name="topNav" />
        </div>
      </header>

      <section v-if="state.themeStore.showTabs" class="tabs-panel">
        <div class="tabs-panel-head">
          <div
            class="route-tabs"
            @contextmenu.prevent
            @wheel.prevent="actions.handleTabsWheel"
          >
            <button
              v-for="item in state.tabsStore.items"
              :key="item.path"
              type="button"
              class="route-tab"
              :class="{ active: item.path === state.tabsStore.activePath }"
              @click="actions.handleTabClick(item.path)"
              @contextmenu.prevent="actions.openContextMenu($event, item.path)"
            >
              <span class="route-tab-accent"></span>
              <span class="tab-label-text">{{ item.title }}</span>
              <span
                v-if="item.closable"
                class="route-tab-close"
                @click.stop="actions.handleTabRemove(item.path)"
              >
                <el-icon><Close /></el-icon>
              </span>
            </button>
          </div>

          <el-dropdown @command="actions.handleGlobalTabCommand">
            <button class="tabs-tool-button" type="button">
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="refresh">刷新当前</el-dropdown-item>
                <el-dropdown-item
                  :disabled="state.isCurrentTabFixed"
                  command="closeCurrent"
                  >关闭当前</el-dropdown-item
                >
                <el-dropdown-item
                  :disabled="!state.hasClosableOtherTabs"
                  command="closeOthers"
                  >关闭其他</el-dropdown-item
                >
                <el-dropdown-item
                  :disabled="!state.hasClosableLeftTabs"
                  command="closeLeft"
                  >关闭左侧</el-dropdown-item
                >
                <el-dropdown-item
                  :disabled="!state.hasClosableRightTabs"
                  command="closeRight"
                  >关闭右侧</el-dropdown-item
                >
                <el-dropdown-item
                  :disabled="state.tabsStore.items.length <= 1"
                  command="closeAll"
                  >关闭全部</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </section>

      <section class="content-shell">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="state.viewRenderKey" />
        </router-view>
      </section>

      <footer v-if="state.themeStore.showFooter" class="layout-footer">
        <span>{{ state.footerText }}</span>
      </footer>
    </div>
  </main>

  <div
    v-if="state.contextMenu.visible"
    class="tab-context-menu"
    :style="{
      left: `${state.contextMenu.x}px`,
      top: `${state.contextMenu.y}px`,
    }"
    @click.stop
  >
    <button
      class="context-item"
      type="button"
      @click="actions.runTabAction('refresh', state.contextMenu.path)"
    >
      刷新当前
    </button>
    <button
      class="context-item"
      type="button"
      :disabled="actions.isFixedTab(state.contextMenu.path)"
      @click="actions.runTabAction('closeCurrent', state.contextMenu.path)"
    >
      关闭当前
    </button>
    <button
      class="context-item"
      type="button"
      :disabled="!actions.canCloseOthers(state.contextMenu.path)"
      @click="actions.runTabAction('closeOthers', state.contextMenu.path)"
    >
      关闭其他
    </button>
    <button
      class="context-item"
      type="button"
      :disabled="!actions.canCloseLeft(state.contextMenu.path)"
      @click="actions.runTabAction('closeLeft', state.contextMenu.path)"
    >
      关闭左侧
    </button>
    <button
      class="context-item"
      type="button"
      :disabled="!actions.canCloseRight(state.contextMenu.path)"
      @click="actions.runTabAction('closeRight', state.contextMenu.path)"
    >
      关闭右侧
    </button>
    <button
      class="context-item"
      type="button"
      :disabled="state.tabsStore.items.length <= 1"
      @click="actions.runTabAction('closeAll', state.contextMenu.path)"
    >
      关闭全部
    </button>
  </div>

  <el-drawer v-model="themeDrawerVisible" title="主题设置" size="380px">
    <div class="theme-panel">
      <section class="theme-block">
        <div class="theme-label">布局模式</div>
        <div class="layout-preset-list">
          <button
            v-for="option in state.layoutOptions"
            :key="option.value"
            type="button"
            class="layout-preset"
            :class="{ active: state.themeStore.layoutMode === option.value }"
            @click="state.themeStore.updateTheme({ layoutMode: option.value })"
          >
            <div class="layout-preset-title">{{ option.title }}</div>
            <div class="layout-preset-desc">{{ option.desc }}</div>
          </button>
        </div>
      </section>

      <section class="theme-block">
        <div class="theme-label">主题色调</div>
        <div class="preset-list">
          <button
            v-for="preset in state.themePresets"
            :key="preset.color"
            class="preset-item"
            :class="{ active: state.themeStore.primaryColor === preset.color }"
            type="button"
            @click="
              state.themeStore.updateTheme({ primaryColor: preset.color })
            "
          >
            <span
              class="preset-color"
              :style="{ background: preset.color }"
            ></span>
            <span>{{ preset.name }}</span>
          </button>
        </div>
        <el-input
          :model-value="state.themeStore.primaryColor"
          placeholder="#2563eb"
          @change="actions.handleColorChange"
        >
          <template #append>
            <input
              class="native-color"
              type="color"
              :value="state.themeStore.primaryColor"
              @input="
                state.themeStore.updateTheme({
                  primaryColor: $event.target.value,
                })
              "
            />
          </template>
        </el-input>
      </section>

      <section class="theme-block">
        <div class="theme-label">字体风格</div>
        <el-select
          :model-value="state.themeStore.fontFamily"
          style="width: 100%"
          @change="state.themeStore.updateTheme({ fontFamily: $event })"
        >
          <el-option
            v-for="font in state.fontOptions"
            :key="font.value"
            :label="font.label"
            :value="font.value"
          />
        </el-select>
      </section>

      <section class="theme-block">
        <div class="theme-label">组件尺寸</div>
        <el-segmented
          :model-value="state.themeStore.componentSize"
          :options="state.sizeOptions"
          @change="state.themeStore.updateTheme({ componentSize: $event })"
        />
      </section>

      <section class="theme-block">
        <div class="theme-label">界面显示</div>
        <div class="theme-switch-list">
          <div class="theme-switch-item">
            <div>
              <div class="theme-switch-title">显示面包屑</div>
              <div class="theme-switch-desc">控制顶部路径导航是否显示</div>
            </div>
            <el-switch
              :model-value="state.showBreadcrumb"
              :disabled="state.breadcrumbLocked"
              @change="state.themeStore.updateTheme({ showBreadcrumb: $event })"
            />
          </div>
          <div class="theme-switch-item">
            <div>
              <div class="theme-switch-title">显示标签栏</div>
              <div class="theme-switch-desc">控制页面标签切换区是否显示</div>
            </div>
            <el-switch
              :model-value="state.themeStore.showTabs"
              @change="state.themeStore.updateTheme({ showTabs: $event })"
            />
          </div>
          <div class="theme-switch-item">
            <div>
              <div class="theme-switch-title">显示底部栏</div>
              <div class="theme-switch-desc">控制页面底部版权区是否显示</div>
            </div>
            <el-switch
              :model-value="state.themeStore.showFooter"
              @change="state.themeStore.updateTheme({ showFooter: $event })"
            />
          </div>
        </div>
      </section>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from "vue";
import { ArrowDown, Close, FullScreen, Setting } from "@element-plus/icons-vue";

const props = defineProps({
  state: { type: Object, required: true },
  actions: { type: Object, required: true },
  themeDrawerVisible: { type: Boolean, required: true },
});

const emit = defineEmits(["update:themeDrawerVisible"]);

const themeDrawerVisible = computed({
  get: () => props.themeDrawerVisible,
  set: (value) => emit("update:themeDrawerVisible", value),
});
</script>

<style scoped>
.main {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
.main-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 16px;
  gap: 12px;
  overflow: hidden;
}
.topbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(18px);
}
.topbar.compact {
  gap: 0;
  padding-top: 10px;
  padding-bottom: 10px;
}
.topbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 40px;
}
.topbar.compact .topbar-row {
  min-height: 48px;
}
.topbar-main {
  min-width: 0;
}
.topbar-main.with-top-nav {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  min-height: 48px;
}
.topbar-main.with-top-nav :deep(.el-menu) {
  flex: 1;
  min-width: 0;
}
.topbar-main :deep(.el-breadcrumb__inner),
.topbar-main :deep(.el-breadcrumb__inner a) {
  color: #475569;
  font-weight: 600;
}
.top-nav-row {
  margin: -2px -4px 0;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}
.tool-button,
.tabs-tool-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.88);
  color: #475569;
  cursor: pointer;
  transition:
    border-color 0.22s ease,
    background-color 0.22s ease,
    color 0.22s ease,
    box-shadow 0.22s ease;
}
.tool-button:hover,
.tabs-tool-button:hover {
  border-color: rgba(37, 99, 235, 0.24);
  background: var(--primary-soft);
  color: var(--primary);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.12);
}
.user-entry {
  cursor: pointer;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 5px 7px 5px 5px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
}
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    var(--primary) 0%,
    var(--primary-deep) 100%
  );
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}
.user-info {
  display: grid;
  gap: 2px;
  padding-right: 4px;
}
.user-name {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.user-role {
  font-size: 11px;
  color: #64748b;
}
.tabs-panel {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.05);
  backdrop-filter: blur(16px);
}
.tabs-panel-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
}
.route-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  overflow-x: clip;
  overflow-y: hidden;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.55) transparent;
}
.route-tabs::-webkit-scrollbar {
  height: 6px;
}
.route-tabs::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.45);
}
.route-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  height: 36px;
  padding: 0 10px 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.92);
  color: #475569;
  cursor: pointer;
  white-space: nowrap;
  transition:
    border-color 0.22s ease,
    background-color 0.22s ease,
    color 0.22s ease,
    box-shadow 0.22s ease;
}
.route-tab::after {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
  pointer-events: none;
}
.route-tab:hover {
  border-color: rgba(37, 99, 235, 0.2);
  background: rgba(255, 255, 255, 0.98);
  color: #1e293b;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.08);
}
.route-tab.active {
  border-color: rgba(37, 99, 235, 0.24);
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.98) 0%,
    rgba(241, 245, 249, 0.98) 100%
  );
  color: #0f172a;
  box-shadow: 0 14px 28px rgba(37, 99, 235, 0.12);
  animation: tab-active-in 0.22s ease;
}
.route-tab-accent {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(148, 163, 184, 0.56);
  transition:
    background-color 0.22s ease,
    box-shadow 0.22s ease,
    transform 0.22s ease;
}
.route-tab.active .route-tab-accent {
  background: var(--primary);
  box-shadow: 0 0 0 5px rgba(37, 99, 235, 0.14);
  animation: tab-accent-pulse 0.3s ease;
}
.tab-label-text {
  max-width: 168px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
}
.route-tab-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #94a3b8;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}
.route-tab-close:hover {
  background: rgba(148, 163, 184, 0.16);
  color: #334155;
}
@keyframes tab-active-in {
  0% {
    box-shadow: 0 0 0 rgba(37, 99, 235, 0);
  }
  100% {
    box-shadow: 0 14px 28px rgba(37, 99, 235, 0.12);
  }
}
@keyframes tab-accent-pulse {
  0% {
    transform: scale(0.8);
  }
  100% {
    transform: scale(1);
  }
}
.content-shell {
  flex: 1;
  display: flex;
  min-width: 0;
  min-height: 0;
  padding: 0;
  overflow: auto;
}
.layout-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.7);
  color: #64748b;
  font-size: 12px;
}
.tab-context-menu {
  position: fixed;
  z-index: 60;
  display: grid;
  gap: 4px;
  min-width: 156px;
  padding: 8px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(16px);
}
.context-item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}
.context-item:hover:not(:disabled) {
  background: var(--primary-soft);
  color: var(--primary);
}
.context-item:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.theme-panel {
  display: grid;
  gap: 18px;
}
.theme-block {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: #f8fafc;
}
.theme-label {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.layout-preset-list {
  display: grid;
  gap: 10px;
}
.layout-preset {
  display: grid;
  gap: 4px;
  padding: 14px 15px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: #fff;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.22s ease,
    background-color 0.22s ease,
    box-shadow 0.22s ease;
}
.layout-preset:hover,
.layout-preset.active {
  border-color: rgba(37, 99, 235, 0.24);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.08);
}
.layout-preset-title {
  font-size: 13px;
  font-weight: 700;
}
.layout-preset-desc {
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
}
.theme-switch-list {
  display: grid;
  gap: 12px;
}
.theme-switch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
}
.theme-switch-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}
.theme-switch-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}
.theme-switch-desc {
  margin-top: 3px;
  font-size: 12px;
  color: #64748b;
}
.preset-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.preset-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 12px;
  background: #fff;
  color: #334155;
  cursor: pointer;
  transition:
    border-color 0.22s ease,
    background-color 0.22s ease;
}
.preset-item:hover,
.preset-item.active {
  border-color: rgba(37, 99, 235, 0.24);
  background: rgba(255, 255, 255, 0.98);
}
.preset-color {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}
.native-color {
  width: 40px;
  height: 32px;
  border: 0;
  background: transparent;
  cursor: pointer;
}
@media (max-width: 1280px) {
  .tab-label-text {
    max-width: 132px;
  }
}
@media (max-width: 960px) {
  .main-shell {
    height: auto;
    min-height: auto;
    padding: 12px;
    overflow: visible;
  }
  .content-shell {
    overflow: visible;
  }
  .topbar-row,
  .tabs-panel-head,
  .theme-switch-item {
    flex-wrap: wrap;
  }
  .route-tabs {
    width: 100%;
  }
  .preset-list {
    grid-template-columns: 1fr;
  }
}
</style>
