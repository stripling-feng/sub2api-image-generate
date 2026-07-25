<template>
  <div class="page-card doc-page">
    <div class="doc-toolbar">
      <el-button type="primary" @click="openInNewPage">新页面打开</el-button>
    </div>
    <div class="doc-shell">
      <iframe class="doc-frame" :src="docUrl" title="Knife4j 接口文档"></iframe>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const docUrl = computed(() => '/doc.html')

function openInNewPage() {
  window.open('/doc.html', '_blank')
}
</script>

<style scoped>
.doc-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.doc-toolbar {
  display: flex;
  justify-content: flex-end;
}

.doc-shell {
  flex: 1;
  min-height: 0;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.92);
}

.doc-frame {
  width: 100%;
  height: 100%;
  min-height: 760px;
  border: none;
  background: #fff;
}
</style>
