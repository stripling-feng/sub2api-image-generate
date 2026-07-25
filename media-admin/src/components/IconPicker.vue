<template>
  <el-dialog
    :model-value="visible"
    title="选择图标"
    width="920px"
    append-to-body
    @close="$emit('update:visible', false)"
  >
    <div class="icon-picker">
      <div class="icon-toolbar">
        <el-input v-model="keyword" clearable placeholder="搜索图标名称" />
      </div>

      <el-tabs v-model="activeTab" class="icon-tabs">
        <el-tab-pane label="Element 图标" name="element">
          <div class="icon-grid">
            <button
              v-for="icon in filteredElementIcons"
              :key="icon.value"
              type="button"
              class="icon-card"
              :class="{ active: modelValue === icon.value }"
              @click="selectIcon(icon.value)"
            >
              <MenuIcon :name="icon.value" :size="20" />
              <span>{{ icon.label }}</span>
            </button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="项目 SVG" name="svg">
          <div class="icon-grid">
            <button
              v-for="icon in filteredSvgIcons"
              :key="icon.value"
              type="button"
              class="icon-card"
              :class="{ active: modelValue === icon.value }"
              @click="selectIcon(icon.value)"
            >
              <MenuIcon :name="icon.value" :size="20" />
              <span>{{ icon.label }}</span>
            </button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <div class="picker-footer">
        <div class="current-icon">
          <MenuIcon :name="modelValue" :size="18" />
          <span>{{ modelValue || '未选择' }}</span>
        </div>
        <div class="picker-actions">
          <el-button @click="$emit('update:modelValue', '')">清空</el-button>
          <el-button @click="$emit('update:visible', false)">关闭</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import * as ElementIcons from '@element-plus/icons-vue'
import MenuIcon from './MenuIcon.vue'

const svgModules = import.meta.glob('../assets/menu-icons/*.svg', {
  eager: true,
  import: 'default',
})

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  modelValue: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:visible', 'update:modelValue'])

const keyword = ref('')
const activeTab = ref('element')

function toWords(value) {
  return value
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[-_]/g, ' ')
    .trim()
}

const elementIcons = Object.keys(ElementIcons)
  .filter((key) => /^[A-Z]/.test(key))
  .sort((a, b) => a.localeCompare(b))
  .map((key) => ({
    label: toWords(key),
    value: `el:${key}`,
  }))

const svgIcons = Object.keys(svgModules)
  .map((path) => path.match(/\/([^/]+)\.svg$/)?.[1] || '')
  .filter(Boolean)
  .sort((a, b) => a.localeCompare(b))
  .map((name) => ({
    label: toWords(name),
    value: `svg:${name}`,
  }))

const filteredElementIcons = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) {
    return elementIcons
  }
  return elementIcons.filter((item) => item.label.toLowerCase().includes(search) || item.value.toLowerCase().includes(search))
})

const filteredSvgIcons = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) {
    return svgIcons
  }
  return svgIcons.filter((item) => item.label.toLowerCase().includes(search) || item.value.toLowerCase().includes(search))
})

function selectIcon(value) {
  emit('update:modelValue', value)
  emit('update:visible', false)
}
</script>

<style scoped>
.icon-picker {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.icon-toolbar {
  display: flex;
}

.icon-grid {
  max-height: 420px;
  overflow: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 12px;
}

.icon-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 92px;
  padding: 12px 10px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: #fff;
  cursor: pointer;
  transition: 0.2s ease;
}

.icon-card:hover {
  border-color: color-mix(in srgb, var(--primary) 40%, var(--border));
  transform: translateY(-1px);
}

.icon-card.active {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 7%, #fff);
  box-shadow: 0 10px 24px color-mix(in srgb, var(--primary) 12%, transparent);
}

.icon-card span {
  font-size: 12px;
  line-height: 1.3;
  text-align: center;
  word-break: break-word;
}

.picker-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.current-icon {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
}

.picker-actions {
  display: flex;
  gap: 10px;
}
</style>
