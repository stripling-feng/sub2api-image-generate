<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div class="page-actions">
        <el-button v-permission="'system:role:add'" type="primary" @click="openCreate">新增角色</el-button>
      </div>

      <div class="filter-card">
        <el-form :inline="true" :model="filters" class="filter-form" label-position="top">
          <el-form-item label="角色名称" class="field-role-name">
            <el-input v-model="filters.roleName" clearable placeholder="角色名称" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="角色标识" class="field-role-key">
            <el-input v-model="filters.roleKey" clearable placeholder="角色标识" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <el-table :data="list" border>
      <el-table-column prop="roleName" label="角色名称" min-width="160" />
      <el-table-column prop="roleKey" label="角色标识" min-width="160" />
      <el-table-column prop="roleSort" label="排序" width="100" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:role:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'system:role:remove'" link type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="pagination.total"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <CrudDialog v-model="visible" :title="form.id ? '编辑角色' : '新增角色'" @submit="handleSubmit">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="角色标识" prop="roleKey"><el-input v-model="form.roleKey" /></el-form-item>
        <el-form-item label="排序" prop="roleSort"><el-input-number v-model="form.roleSort" :min="0" /></el-form-item>
        <el-form-item label="菜单权限">
          <div class="menu-tree-box">
            <el-tree
              ref="menuTreeRef"
              class="menu-tree"
              :data="menuTree"
              node-key="id"
              show-checkbox
              default-expand-all
              :expand-on-click-node="false"
              :props="{ label: 'menuName', children: 'children' }"
            />
          </div>
        </el-form-item>
      </el-form>
    </CrudDialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudDialog from '../../components/CrudDialog.vue'
import { menuApi, roleApi } from '../../api/system'

const visible = ref(false)
const formRef = ref(null)
const list = ref([])
const menuTree = ref([])
const menuTreeRef = ref(null)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})

const filters = reactive({
  roleName: '',
  roleKey: '',
})

const emptyForm = () => ({
  id: null,
  roleName: '',
  roleKey: '',
  roleSort: 1,
  remark: '',
  menuIds: [],
})

const form = reactive(emptyForm())
const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [
    { required: true, message: '请输入角色标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]+$/, message: '角色标识只能包含字母、数字、:、_、-', trigger: 'blur' },
  ],
  roleSort: [{ required: true, type: 'number', message: '请输入排序', trigger: 'change' }],
}

function resetForm() {
  Object.assign(form, emptyForm())
  formRef.value?.clearValidate()
}

function handleSearch() {
  pagination.pageNum = 1
  loadData()
}

function resetFilters() {
  filters.roleName = ''
  filters.roleKey = ''
  pagination.pageNum = 1
  loadData()
}

async function loadData() {
  const data = await roleApi.list({
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    roleName: filters.roleName || undefined,
    roleKey: filters.roleKey || undefined,
  })
  list.value = data.records
  pagination.total = data.total
  pagination.pageNum = data.pageNum
  pagination.pageSize = data.pageSize
}

async function loadMenuTree() {
  menuTree.value = await menuApi.tree()
}

function openCreate() {
  resetForm()
  visible.value = true
  nextTick(() => {
    formRef.value?.clearValidate()
    menuTreeRef.value?.setCheckedKeys([])
  })
}

async function openEdit(row) {
  resetForm()
  Object.assign(form, row)
  form.menuIds = await roleApi.menuIds(row.id)
  visible.value = true
  await nextTick()
  formRef.value?.clearValidate()
  const leafIds = getLeafIds(menuTree.value, new Set(form.menuIds))
  menuTreeRef.value?.setCheckedKeys([...leafIds])
}

function getLeafIds(nodes, idSet, result = new Set()) {
  for (const node of nodes) {
    if (!node.children?.length) {
      if (idSet.has(node.id)) result.add(node.id)
    } else {
      getLeafIds(node.children, idSet, result)
    }
  }
  return result
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const checkedKeys = menuTreeRef.value?.getCheckedKeys(false) || []
  const halfCheckedKeys = menuTreeRef.value?.getHalfCheckedKeys() || []
  form.menuIds = [...new Set([...checkedKeys, ...halfCheckedKeys])]

  if (form.id) {
    await roleApi.update(form.id, form)
  } else {
    await roleApi.add(form)
  }
  ElMessage.success('保存成功')
  visible.value = false
  await loadData()
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确认删除该角色吗？', '提示')
  await roleApi.remove(id)
  ElMessage.success('删除成功')
  await loadData()
}

onMounted(async () => {
  await Promise.all([loadData(), loadMenuTree()])
})
</script>

<style scoped>
.field-role-name,
.field-role-key {
  min-width: 260px;
  flex: 1 1 260px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.menu-tree-box {
  width: 100%;
  max-height: 320px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid rgba(143, 168, 204, 0.22);
  border-radius: 12px;
  background: #fff;
}

.menu-tree {
  width: 100%;
}
</style>
