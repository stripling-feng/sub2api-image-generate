<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div class="page-actions">
        <el-button v-permission="'system:post:add'" type="primary" @click="openCreate">新增岗位</el-button>
      </div>

      <div class="filter-card">
        <el-form :inline="true" :model="filters" class="filter-form" label-position="top">
          <el-form-item label="岗位编码" class="field-post-code">
            <el-input v-model="filters.postCode" clearable placeholder="岗位编码" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="岗位名称" class="field-post-name">
            <el-input v-model="filters.postName" clearable placeholder="岗位名称" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <el-table :data="list" border>
      <el-table-column prop="postCode" label="岗位编码" min-width="160" />
      <el-table-column prop="postName" label="岗位名称" min-width="180" />
      <el-table-column prop="postSort" label="排序" width="100" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:post:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'system:post:remove'" link type="danger" @click="handleDelete(row.id)">删除</el-button>
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

    <CrudDialog v-model="visible" :title="form.id ? '编辑岗位' : '新增岗位'" @submit="handleSubmit">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="岗位编码" prop="postCode"><el-input v-model="form.postCode" /></el-form-item>
        <el-form-item label="岗位名称" prop="postName"><el-input v-model="form.postName" /></el-form-item>
        <el-form-item label="排序" prop="postSort"><el-input-number v-model="form.postSort" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
    </CrudDialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudDialog from '../../components/CrudDialog.vue'
import { postApi } from '../../api/system'

const visible = ref(false)
const formRef = ref(null)
const list = ref([])

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})

const filters = reactive({
  postCode: '',
  postName: '',
})

const emptyForm = () => ({ id: null, postCode: '', postName: '', postSort: 1, remark: '' })
const form = reactive(emptyForm())
const formRules = {
  postCode: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
  postName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  postSort: [{ required: true, type: 'number', message: '请输入排序', trigger: 'change' }],
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
  filters.postCode = ''
  filters.postName = ''
  pagination.pageNum = 1
  loadData()
}

async function loadData() {
  const data = await postApi.list({
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    postCode: filters.postCode || undefined,
    postName: filters.postName || undefined,
  })
  list.value = data.records
  pagination.total = data.total
  pagination.pageNum = data.pageNum
  pagination.pageSize = data.pageSize
}

function openCreate() {
  resetForm()
  visible.value = true
}

function openEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    postCode: row.postCode,
    postName: row.postName,
    postSort: row.postSort,
    remark: row.remark || '',
  })
  visible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (form.id) {
    await postApi.update(form.id, form)
  } else {
    await postApi.add(form)
  }
  ElMessage.success('保存成功')
  visible.value = false
  await loadData()
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确认删除该岗位吗？', '提示')
  await postApi.remove(id)
  ElMessage.success('删除成功')
  await loadData()
}

onMounted(loadData)
</script>

<style scoped>
.field-post-code,
.field-post-name {
  min-width: 260px;
  flex: 1 1 260px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
