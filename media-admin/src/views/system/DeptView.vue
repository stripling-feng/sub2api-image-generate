<template>
  <div class="page-card dept-page">
    <div class="dept-toolbar">
      <div class="toolbar-actions">
        <el-button @click="expandAll">展开全部</el-button>
        <el-button @click="collapseAll">收起全部</el-button>
      </div>
      <el-button v-permission="'system:dept:add'" type="primary" @click="openCreate()">新增部门</el-button>
    </div>

    <div class="dept-shell">
      <div class="dept-tree-panel">
        <div class="panel-title">组织结构</div>
        <el-tree
          ref="treeRef"
          :data="list"
          node-key="id"
          :default-expanded-keys="expandedKeys"
          :expand-on-click-node="false"
          :props="{ label: 'menuName', children: 'children' }"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="tree-card" :class="{ active: activeNode?.id === data.id }">
              <div class="tree-main">
                <div class="tree-title-row">
                  <span class="tree-title">{{ data.menuName }}</span>
                  <span class="dept-badge">部门</span>
                </div>
                <div class="tree-meta">
                  <span>排序 {{ data.menuSort || 0 }}</span>
                  <span>上级 {{ getParentName(data.parentId) }}</span>
                </div>
              </div>
              <div class="tree-actions" @click.stop>
                <el-button v-permission="'system:dept:add'" link type="primary" @click="openCreate(data)">新增</el-button>
                <el-button v-permission="'system:dept:edit'" link type="primary" @click="openEdit(data)">编辑</el-button>
                <el-button v-permission="'system:dept:remove'" link type="danger" @click="handleDelete(data.id)">删除</el-button>
              </div>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="dept-detail-panel">
        <div class="panel-title">部门详情</div>
        <template v-if="activeNode">
          <div class="detail-hero">
            <div>
              <div class="detail-title">{{ activeNode.menuName }}</div>
              <div class="detail-subtitle">查看当前部门信息，也可以快速新增下级部门或编辑当前部门。</div>
            </div>
            <span class="dept-badge large">部门</span>
          </div>

          <div class="detail-grid">
            <div class="detail-item">
              <label>上级名称</label>
              <span>{{ getParentName(activeNode.parentId) }}</span>
            </div>
            <div class="detail-item">
              <label>排序</label>
              <span>{{ activeNode.menuSort || 0 }}</span>
            </div>
            <div class="detail-item">
              <label>节点 ID</label>
              <span>{{ activeNode.id }}</span>
            </div>
            <div class="detail-item">
              <label>子部门数量</label>
              <span>{{ activeNode.children?.length || 0 }}</span>
            </div>
          </div>

          <div class="detail-actions">
            <el-button v-permission="'system:dept:add'" @click="openCreate(activeNode)">新增下级</el-button>
            <el-button v-permission="'system:dept:edit'" type="primary" @click="openEdit(activeNode)">编辑当前</el-button>
          </div>
        </template>
        <div v-else class="empty-detail">
          <div>
            <div class="empty-title">选择左侧部门查看详情</div>
            <div class="empty-subtitle">这里会展示部门层级和基础信息。</div>
          </div>
        </div>
      </div>
    </div>

    <CrudDialog v-model="visible" :title="form.id ? '编辑部门' : '新增部门'" @submit="handleSubmit">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            check-strictly
            node-key="id"
            style="width: 100%"
            :data="parentTree"
            :props="{ label: 'menuName', children: 'children' }"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName"><el-input v-model="form.deptName" /></el-form-item>
        <el-form-item label="排序" prop="deptSort"><el-input-number v-model="form.deptSort" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.leader" /></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" /></el-form-item>
      </el-form>
    </CrudDialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudDialog from '../../components/CrudDialog.vue'
import { deptApi } from '../../api/system'

const visible = ref(false)
const formRef = ref(null)
const list = ref([])
const activeNode = ref(null)
const expandedKeys = ref([])
const treeRef = ref(null)

const emptyForm = () => ({
  id: null,
  parentId: 0,
  deptName: '',
  deptSort: 1,
  leader: '',
  phone: '',
  email: '',
})

const form = reactive(emptyForm())
const parentTree = computed(() => [{ id: 0, menuName: '顶级部门', children: list.value }])
const formRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  deptSort: [{ required: true, type: 'number', message: '请输入排序', trigger: 'change' }],
  phone: [{ pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

function flattenTree(nodes, result = []) {
  for (const node of nodes) {
    result.push(node)
    if (node.children?.length) flattenTree(node.children, result)
  }
  return result
}

function getParentName(parentId) {
  if (!parentId || parentId === 0) return '顶级部门'
  return flattenTree(list.value).find((item) => item.id === parentId)?.menuName || '-'
}

function resetForm() {
  Object.assign(form, emptyForm())
  formRef.value?.clearValidate()
}

async function loadData() {
  list.value = await deptApi.tree()
  expandedKeys.value = list.value.map((item) => item.id)
  if (!activeNode.value && list.value.length) {
    activeNode.value = list.value[0]
  } else if (activeNode.value) {
    const next = flattenTree(list.value).find((item) => item.id === activeNode.value.id)
    activeNode.value = next || list.value[0] || null
  }
}

function handleNodeClick(data) {
  activeNode.value = data
}

function openCreate(parent = null) {
  resetForm()
  form.parentId = parent?.id ?? 0
  visible.value = true
}

function openEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId ?? 0,
    deptName: row.deptName || row.menuName || '',
    deptSort: row.deptSort || row.menuSort || 1,
    leader: row.leader || '',
    phone: row.phone || '',
    email: row.email || '',
  })
  visible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (form.id) {
    await deptApi.update(form.id, form)
  } else {
    await deptApi.add(form)
  }
  ElMessage.success('保存成功')
  visible.value = false
  await loadData()
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确认删除该部门吗？', '提示')
  await deptApi.remove(id)
  ElMessage.success('删除成功')
  await loadData()
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
.dept-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.dept-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.dept-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 18px;
  min-height: 0;
  flex: 1;
}

.dept-tree-panel,
.dept-detail-panel {
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

.tree-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: none;
}

.dept-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  color: var(--primary-deep);
  background: var(--primary-soft);
}

.dept-badge.large {
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

.detail-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
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
  .dept-shell {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .dept-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .tree-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .tree-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .detail-hero {
    flex-direction: column;
  }
}
</style>
