<template>
  <el-config-provider :size="themeStore.componentSize">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { onMounted } from 'vue'
import { useThemeStore } from './stores/theme'

const themeStore = useThemeStore()

onMounted(() => {
  themeStore.initTheme()
})
</script>
