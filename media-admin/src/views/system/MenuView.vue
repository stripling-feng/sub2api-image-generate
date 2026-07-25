<template>
  <div class="page-card menu-page">
    <div class="menu-toolbar">
      <div class="toolbar-actions">
        <el-button @click="expandAll">展开全部</el-button>
        <el-button @click="collapseAll">收起全部</el-button>
      </div>
      <el-button v-permission="'system:menu:add'" type="primary" @click="openCreate()">新增菜单</el-button>
    </div>

    <div class="menu-shell">
      <div class="menu-tree-panel">
        <div class="panel-title">菜单结构</div>
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
                  <span class="type-badge" :class="typeClassMap[data.menuType]">
                    {{ typeMap[data.menuType] }}
                  </span>
                </div>
                <div class="tree-meta">
                  <span v-if="data.path">/{{ data.path }}</span>
                  <span v-if="data.permission">{{ data.permission }}</span>
                </div>
              </div>
              <div class="tree-actions" @click.stop>
                <el-button v-permission="'system:menu:add'" link type="primary" @click="openCreate(data)">新增</el-button>
                <el-button v-permission="'system:menu:edit'" link type="primary" @click="openEdit(data)">编辑</el-button>
                <el-button v-permission="'system:menu:remove'" link type="danger" @click="handleDelete(data.id)">删除</el-button>
              </div>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="menu-detail-panel">
        <div class="panel-title">菜单详情</div>
        <template v-if="activeNode">
          <div class="detail-hero">
            <div>
              <div class="detail-title">{{ activeNode.menuName }}</div>
              <div class="detail-subtitle">当前为 {{ typeMap[activeNode.menuType] }}，可继续编辑或新增下级。</div>
            </div>
            <span class="type-badge large" :class="typeClassMap[activeNode.menuType]">
              {{ typeMap[activeNode.menuType] }}
            </span>
          </div>

          <div class="detail-grid">
            <div class="detail-item">
              <label>上级名称</label>
              <span>{{ getParentName(activeNode.parentId) }}</span>
            </div>
            <div class="detail-item">
              <label>排序</label>
              <span>{{ activeNode.menuSort }}</span>
            </div>
            <div class="detail-item">
              <label>路由路径</label>
              <span>{{ activeNode.path || '-' }}</span>
            </div>
            <div class="detail-item">
              <label>组件路径</label>
              <span>{{ activeNode.component || '-' }}</span>
            </div>
            <div class="detail-item">
              <label>权限标识</label>
              <span>{{ activeNode.permission || '-' }}</span>
            </div>
            <div class="detail-item">
              <label>图标</label>
              <span class="detail-icon-value">
                <MenuIcon v-if="activeNode.icon" :name="activeNode.icon" :size="18" />
                <span>{{ activeNode.icon || '-' }}</span>
              </span>
            </div>
            <div class="detail-item">
              <label>显示状态</label>
              <span>{{ activeNode.visible === 1 ? '显示' : '隐藏' }}</span>
            </div>
          </div>

          <div class="detail-actions">
            <el-button v-permission="'system:menu:add'" @click="openCreate(activeNode)">新增下级</el-button>
            <el-button v-permission="'system:menu:edit'" type="primary" @click="openEdit(activeNode)">编辑当前</el-button>
          </div>
        </template>
        <div v-else class="empty-detail">
          <div>
            <div class="empty-title">选择左侧节点查看详情</div>
            <div class="empty-subtitle">这里会展示目录、菜单或按钮的配置信息。</div>
          </div>
        </div>
      </div>
    </div>

    <CrudDialog v-model="visible" :title="form.id ? '编辑菜单' : '新增菜单'" @submit="handleSubmit">
      <el-form :model="form" label-width="90px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            check-strictly
            node-key="id"
            style="width: 100%"
            :data="parentTree"
            :props="{ label: 'menuName', children: 'children' }"
          />
        </el-form-item>
        <el-form-item label="菜单名称"><el-input v-model="form.menuName" /></el-form-item>
        <el-form-item label="菜单类型">
          <el-segmented v-model="form.menuType" :options="menuTypeOptions" />
        </el-form-item>
        <el-form-item label="路由路径"><el-input v-model="form.path" placeholder="如：system/menu" /></el-form-item>
        <el-form-item label="组件路径"><el-input v-model="form.component" placeholder="如：views/system/MenuView.vue" /></el-form-item>
        <el-form-item label="权限标识"><el-input v-model="form.permission" placeholder="如：system:menu:list" /></el-form-item>
                <el-form-item label="图标">
          <div class="icon-field">
            <button type="button" class="icon-trigger" @click="iconPickerVisible = true">
              <MenuIcon :name="form.icon" :size="18" />
              <span>{{ form.icon || '请选择图标' }}</span>
            </button>
            <el-button link type="primary" @click="iconPickerVisible = true">选择图标</el-button>
          </div>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.menuSort" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="显示"><el-switch v-model="form.visible" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
    </CrudDialog>
    <IconPicker v-model:visible="iconPickerVisible" v-model="form.icon" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudDialog from '../../components/CrudDialog.vue'
import IconPicker from '../../components/IconPicker.vue'
import MenuIcon from '../../components/MenuIcon.vue'
import { menuApi } from '../../api/system'

const visible = ref(false)
const iconPickerVisible = ref(false)
const list = ref([])
const activeNode = ref(null)
const expandedKeys = ref([])
const treeRef = ref(null)

const typeMap = { 0: '目录', 1: '菜单', 2: '按钮' }
const typeClassMap = { 0: 'directory', 1: 'menu', 2: 'button' }
const menuTypeOptions = [
  { label: '目录', value: 0 },
  { label: '菜单', value: 1 },
  { label: '按钮', value: 2 },
]

const emptyForm = () => ({
  id: null,
  parentId: 0,
  menuName: '',
  menuType: 1,
  path: '',
  component: '',
  permission: '',
  icon: '',
  menuSort: 1,
  visible: 1,
})

const form = reactive(emptyForm())
const parentTree = computed(() => [{ id: 0, menuName: '顶级菜单', children: list.value }])

function flattenMenus(nodes, result = []) {
  for (const node of nodes) {
    result.push(node)
    if (node.children?.length) flattenMenus(node.children, result)
  }
  return result
}

function getParentName(parentId) {
  if (!parentId || parentId === 0) {
    return '顶级菜单'
  }
  return flattenMenus(list.value).find((item) => item.id === parentId)?.menuName || '-'
}

function resetForm() {
  Object.assign(form, emptyForm())
}

async function loadData() {
  list.value = await menuApi.tree()
  expandedKeys.value = list.value.map((item) => item.id)
  if (!activeNode.value && list.value.length) {
    activeNode.value = list.value[0]
  } else if (activeNode.value) {
    const next = flattenMenus(list.value).find((item) => item.id === activeNode.value.id)
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
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId ?? 0,
    menuName: row.menuName || '',
    menuType: row.menuType ?? 1,
    path: row.path || '',
    component: row.component || '',
    permission: row.permission || '',
    icon: row.icon || '',
    menuSort: row.menuSort ?? 1,
    visible: row.visible ?? 1,
  })
  visible.value = true
}

async function handleSubmit() {
  if (form.id) {
    await menuApi.update(form.id, form)
  } else {
    await menuApi.add(form)
  }
  ElMessage.success('保存成功')
  visible.value = false
  await loadData()
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确认删除该菜单吗？', '提示')
  await menuApi.remove(id)
  ElMessage.success('删除成功')
  await loadData()
}

function expandAll() {
  expandedKeys.value = flattenMenus(list.value).map((item) => item.id)
  nextTick(() => {
    flattenMenus(list.value).forEach((item) => {
      treeRef.value?.store.nodesMap[item.id]?.expand()
    })
  })
}

async function collapseAll() {
  expandedKeys.value = []
  await nextTick()
  flattenMenus(list.value).forEach((item) => {
    treeRef.value?.store.nodesMap[item.id]?.collapse()
  })
}

onMounted(loadData)
</script>

<style scoped>
.menu-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.menu-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.menu-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 18px;
  min-height: 0;
  flex: 1;
}

.menu-tree-panel,
.menu-detail-panel {
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

.type-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.type-badge.large {
  padding: 8px 14px;
  font-size: 13px;
}

.type-badge.directory {
  color: #a16207;
  background: rgba(245, 158, 11, 0.16);
}

.type-badge.menu {
  color: var(--primary-deep);
  background: var(--primary-soft);
}

.type-badge.button {
  color: #047857;
  background: rgba(16, 185, 129, 0.16);
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

.detail-icon-value {
  display: inline-flex !important;
  align-items: center;
  gap: 8px;
}

.icon-field {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-trigger {
  flex: 1;
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: #fff;
  color: var(--text);
  cursor: pointer;
}

.icon-trigger:hover {
  border-color: color-mix(in srgb, var(--primary) 45%, var(--border));
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
  .menu-shell {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .menu-toolbar {
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









