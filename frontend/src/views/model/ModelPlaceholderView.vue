<template>
  <div class="page-card model-page">
    <div>
      <h2>{{ route.meta.title }}</h2>
      <p>模型管理功能待配置。</p>
    </div>
    <el-empty description="暂无模型数据" />
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<style scoped>
.model-page {
  min-height: 420px;
  padding: 24px;
}

h2 {
  margin: 0 0 8px;
}

p {
  margin: 0;
  color: var(--el-text-color-secondary);
}
</style>
