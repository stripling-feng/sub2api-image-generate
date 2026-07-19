<template>
  <el-icon v-if="elementIconComponent" :size="size" :class="iconClass">
    <component :is="elementIconComponent" />
  </el-icon>
  <span
    v-else-if="svgContent"
    :class="['menu-svg-icon', iconClass]"
    :style="iconStyle"
    v-html="svgContent"
  />
  <el-icon v-else :size="size" :class="iconClass">
    <Menu />
  </el-icon>
</template>

<script setup>
import { computed } from "vue";
import { Menu } from "@element-plus/icons-vue";
import * as ElementIcons from "@element-plus/icons-vue";

const svgModules = import.meta.glob("../assets/menu-icons/*.svg", {
  eager: true,
  import: "default",
  query: "?raw",
});

const props = defineProps({
  name: {
    type: String,
    default: "",
  },
  size: {
    type: Number,
    default: 18,
  },
  className: {
    type: String,
    default: "",
  },
});

function normalizeKey(value) {
  return String(value || "")
    .replace(/^el:/i, "")
    .replace(/^svg:/i, "")
    .replace(/[^a-zA-Z0-9]/g, "")
    .toLowerCase();
}

const elementIconMap = Object.entries(ElementIcons).reduce(
  (result, [key, component]) => {
    result[normalizeKey(key)] = component;
    return result;
  },
  {},
);

const svgIconMap = Object.entries(svgModules).reduce((result, [path, content]) => {
  const match = path.match(/\/([^/]+)\.svg(\?.*)?$/);
  if (match) {
    result[normalizeKey(match[1])] = content;
  }
  return result;
}, {});

const iconClass = computed(() => props.className);
const iconStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
}));

const elementIconComponent = computed(() => {
  const value = props.name || "";
  if (value.startsWith("svg:")) {
    return null;
  }
  return elementIconMap[normalizeKey(value)] || null;
});

const svgContent = computed(() => {
  const value = props.name || "";
  if (!value.startsWith("svg:")) {
    return null;
  }
  return svgIconMap[normalizeKey(value)] || null;
});
</script>

<style scoped>
.menu-svg-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
  margin-right: 2px;
}
.menu-svg-icon :deep(svg) {
  width: 100%;
  height: 100%;
  fill: currentColor;
}
.menu-svg-icon :deep(svg [stroke]) {
  stroke: currentColor;
  fill: none;
}
.menu-svg-icon :deep(svg [fill]:not([fill="none"])) {
  fill: currentColor;
}
</style>
