<template>
  <div class="page-card district-page">
    <div class="district-toolbar">
      <div class="toolbar-actions">
        <el-button @click="expandAll">展开全部</el-button>
        <el-button @click="collapseAll">收起全部</el-button>
      </div>
      <div class="toolbar-right">
        <el-input v-model="filterText" placeholder="搜索行政区名称或编码" clearable class="search-input" />
        <el-button v-permission="'system:district:sync'" type="primary" :loading="syncing" @click="handleSync">同步最新数据</el-button>
      </div>
    </div>

    <div class="district-shell">
      <div class="district-tree-panel">
        <div class="panel-title">行政区划</div>
        <el-tree
          ref="treeRef"
          :data="list"
          node-key="id"
          :default-expanded-keys="expandedKeys"
          :expand-on-click-node="false"
          :props="{ label: 'menuName', children: 'children' }"
          :filter-node-method="filterNode"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="tree-card" :class="{ active: activeNode?.id === data.id }">
              <div class="tree-main">
                <div class="tree-title-row">
                  <span class="tree-title">{{ data.menuName }}</span>
                  <span class="level-badge" :class="levelClass(data)">{{ levelLabel(data) }}</span>
                </div>
                <div class="tree-meta">
                  <span>编码 {{ data.menuSort || '-' }}</span>
                  <span>子级 {{ data.children?.length || 0 }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="district-detail-panel">
        <div class="panel-title">行政区详情</div>
        <template v-if="activeNode">
          <div class="detail-hero">
            <div>
              <div class="detail-title">{{ activeNode.menuName }}</div>
              <div class="detail-subtitle">行政区划编码：{{ activeNode.menuSort }}</div>
            </div>
            <span class="level-badge large" :class="levelClass(activeNode)">{{ levelLabel(activeNode) }}</span>
          </div>

          <div class="detail-grid">
            <div class="detail-item">
              <label>行政区划编码</label>
              <span>{{ activeNode.menuSort }}</span>
            </div>
            <div class="detail-item">
              <label>层级</label>
              <span>{{ levelLabel(activeNode) }}</span>
            </div>
            <div class="detail-item">
              <label>子级数量</label>
              <span>{{ activeNode.children?.length || 0 }}</span>
            </div>
            <div class="detail-item">
              <label>节点 ID</label>
              <span>{{ activeNode.id }}</span>
            </div>
          </div>
        </template>
        <div v-else class="empty-detail">
          <div>
            <div class="empty-title">选择左侧行政区查看详情</div>
            <div class="empty-subtitle">这里会展示行政区名称、编码和层级信息。</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { districtApi } from '../../api/system'

const list = ref([])
const activeNode = ref(null)
const expandedKeys = ref([])
const treeRef = ref(null)
const filterText = ref('')
const syncing = ref(false)

const LEVEL_MAP = { 1: '省', 2: '市', 3: '区' }

function levelLabel(data) {
  return LEVEL_MAP[data.level] || ''
}

function levelClass(data) {
  const map = { 1: 'level-province', 2: 'level-city', 3: 'level-district' }
  return map[data.level] || ''
}

function flattenTree(nodes, result = []) {
  for (const node of nodes) {
    result.push(node)
    if (node.children?.length) flattenTree(node.children, result)
  }
  return result
}

function filterNode(value, data) {
  if (!value) return true
  const keyword = value.toLowerCase()
  const name = data.menuName?.toLowerCase() || ''
  const code = String(data.menuSort || '').toLowerCase()
  return name.includes(keyword) || code.includes(keyword)
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

async function loadData() {
  list.value = await districtApi.tree()
  expandedKeys.value = list.value.map((item) => item.id)
  if (!activeNode.value && list.value.length) {
    activeNode.value = list.value[0]
  }
}

function handleNodeClick(data) {
  activeNode.value = data
}

async function handleSync() {
  syncing.value = true
  try {
    await districtApi.sync()
    ElMessage.success('同步成功')
    await loadData()
  } finally {
    syncing.value = false
  }
}

function expandAll() {
  expandedKeys.value = flattenTree(list.value).map((item) => item.id)
  nextTick(() => {
    flattenTree(list.value).forEach((item) => {
      treeRef.value?.store.nodesMap[item.id]?.expand()
    })
  })
}

async function collapseAll() {
  expandedKeys.value = []
  await nextTick()
  flattenTree(list.value).forEach((item) => {
    treeRef.value?.store.nodesMap[item.id]?.collapse()
  })
}

onMounted(loadData)
</script>

<style scoped>
.district-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.district-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 240px;
}

.district-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 18px;
  min-height: 0;
  flex: 1;
}

.district-tree-panel,
.district-detail-panel {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 250, 255, 0.96));
}

.panel-title {
  margin-bottom: 14px;
  font-size: 16px;
  font-weight: 800;
}

.tree-card {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 16px;
  border: 1px solid transparent;
  background: rgba(255, 255, 255, 0.64);
  transition: 0.22s ease;
}

.tree-card:hover {
  border-color: var(--border);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 28px rgba(28, 48, 84, 0.06);
}

.tree-card.active {
  border-color: color-mix(in srgb, var(--primary) 35%, transparent);
  background: linear-gradient(180deg, var(--primary-soft), rgba(255, 255, 255, 0.98));
  box-shadow: 0 14px 30px color-mix(in srgb, var(--primary) 12%, transparent);
}

.tree-main {
  min-width: 0;
}

.tree-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-title {
  font-size: 14px;
  font-weight: 700;
}

.tree-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
  color: var(--muted);
  font-size: 11px;
}

.level-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.level-province {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.1);
}

.level-city {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
}

.level-district {
  color: #059669;
  background: rgba(5, 150, 105, 0.1);
}

.level-badge.large {
  padding: 8px 14px;
  font-size: 13px;
}

.detail-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(239, 246, 255, 0.96));
  border: 1px solid var(--border);
}

.detail-title {
  font-size: 22px;
  font-weight: 800;
}

.detail-subtitle {
  margin-top: 8px;
  color: var(--muted);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.detail-item {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid var(--border);
}

.detail-item label {
  display: block;
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 12px;
}

.detail-item span {
  display: block;
  font-weight: 700;
  word-break: break-all;
}

.empty-detail {
  min-height: 280px;
  display: grid;
  place-items: center;
  text-align: center;
  color: var(--muted);
}

.empty-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--text);
}

.empty-subtitle {
  margin-top: 8px;
}

:deep(.el-tree) {
  background: transparent;
}

:deep(.el-tree-node__content) {
  height: auto;
  min-height: 0;
  padding: 3px 0;
}

:deep(.el-tree-node:focus > .el-tree-node__content) {
  background: transparent;
}

@media (max-width: 1100px) {
  .district-shell {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .district-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-right {
    flex-direction: column;
  }

  .search-input {
    width: 100%;
  }

  .tree-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .detail-hero {
    flex-direction: column;
  }
}
</style>
