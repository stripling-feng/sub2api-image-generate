<template>
  <div class="page-card data-page">
    <div class="data-page-header">
      <div class="data-page-heading">
        <h1>模型服务商</h1>
        <p>集中维护上游地址和图片、视频调用凭证。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增服务商</el-button>
    </div>

    <div class="data-filter-bar">
      <el-input v-model="filters.name" clearable placeholder="搜索服务商名称" @keyup.enter="handleSearch" />
      <el-button type="primary" plain @click="handleSearch">查询</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <div class="data-table-shell">
      <el-table :data="list" table-layout="fixed">
        <el-table-column label="服务商" min-width="145"><template #default="{ row }"><span class="table-primary">{{ row.name }}</span></template></el-table-column>
        <el-table-column label="Base URL" min-width="220"><template #default="{ row }"><span class="table-code">{{ row.baseUrl }}</span></template></el-table-column>
        <el-table-column label="图片 Key" width="105"><template #default="{ row }"><span :class="['status-dot', { 'is-active': Boolean(row.imageApiKey) }]">{{ row.imageApiKey ? '已配置' : '未配置' }}</span></template></el-table-column>
        <el-table-column label="视频 Key" width="105"><template #default="{ row }"><span :class="['status-dot', { 'is-active': Boolean(row.videoApiKey) }]">{{ row.videoApiKey ? '已配置' : '未配置' }}</span></template></el-table-column>
        <el-table-column label="状态" width="82"><template #default="{ row }"><span :class="['status-dot', { 'is-active': row.enabled === 1 }]">{{ row.enabled === 1 ? '启用' : '停用' }}</span></template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{ row }"><div class="row-actions"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row.id)">删除</el-button></div></template></el-table-column>
      </el-table>
      <div class="table-footer">
        <span>共 {{ page.total }} 个服务商</span>
        <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" background layout="sizes, prev, pager, next" :total="page.total" @size-change="loadData" @current-change="loadData" />
      </div>
    </div>

    <CrudDialog v-model="visible" :title="form.id ? '编辑模型服务商' : '新增模型服务商'" @submit="submit">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="服务商名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="Base URL" prop="baseUrl"><el-input v-model="form.baseUrl" placeholder="https://ai.cangyuansuanli.cn" /></el-form-item>
        <el-form-item label="图片 API Key"><el-input v-model="form.imageApiKey" type="password" show-password /></el-form-item>
        <el-form-item label="视频 API Key"><el-input v-model="form.videoApiKey" type="password" show-password /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.providerSort" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
    </CrudDialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudDialog from '../../components/CrudDialog.vue'
import { modelProviderApi } from '../../api/system'

const list = ref([]), visible = ref(false), formRef = ref(null)
const filters = reactive({ name: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const empty = () => ({ id: null, name: '', baseUrl: 'https://ai.cangyuansuanli.cn', imageApiKey: '', videoApiKey: '', providerSort: 1, enabled: 1 })
const form = reactive(empty())
const rules = { name: [{ required: true, message: '请输入服务商名称' }], baseUrl: [{ required: true, message: '请输入 Base URL' }] }

async function loadData() { const data = await modelProviderApi.list({ ...page, name: filters.name || undefined }); list.value = data.records; Object.assign(page, { total: data.total, pageNum: data.pageNum, pageSize: data.pageSize }) }
function handleSearch() { page.pageNum = 1; loadData() }
function resetSearch() { filters.name = ''; page.pageNum = 1; loadData() }
function openCreate() { Object.assign(form, empty()); visible.value = true }
function openEdit(row) { Object.assign(form, row); visible.value = true }
async function submit() { if (!await formRef.value?.validate().catch(() => false)) return; form.id ? await modelProviderApi.update(form.id, form) : await modelProviderApi.add(form); visible.value = false; ElMessage.success('保存成功'); await loadData() }
async function remove(id) { await ElMessageBox.confirm('确认删除该服务商吗？', '提示'); await modelProviderApi.remove(id); ElMessage.success('删除成功'); await loadData() }
onMounted(loadData)
</script>
