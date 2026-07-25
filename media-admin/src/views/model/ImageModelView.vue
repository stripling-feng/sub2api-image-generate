<template>
  <div class="page-card data-page">
    <div class="data-page-header">
      <div class="data-page-heading">
        <h1>图片模型</h1>
        <p>管理工作台可用模型、上游标识和生成参数。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增模型</el-button>
    </div>

    <div class="data-filter-bar">
      <el-input v-model="filters.name" clearable placeholder="搜索显示名称或模型标识" @keyup.enter="handleSearch" />
      <el-button type="primary" plain @click="handleSearch">查询</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <div class="data-table-shell">
      <el-table :data="list" table-layout="fixed">
        <el-table-column label="显示名称" min-width="150"><template #default="{ row }"><span class="table-primary">{{ row.displayName }}</span></template></el-table-column>
        <el-table-column label="模型标识" min-width="150"><template #default="{ row }"><span class="table-code">{{ row.modelKey }}</span></template></el-table-column>
        <el-table-column label="上游模型" min-width="160"><template #default="{ row }"><span class="table-code">{{ row.upstreamModel }}</span></template></el-table-column>
        <el-table-column label="服务商" min-width="105"><template #default="{ row }">{{ providerName(row.providerId) }}</template></el-table-column>
        <el-table-column label="单价" width="105"><template #default="{ row }"><span class="table-primary">${{ Number(row.unitPriceUsd ?? 0.5).toFixed(2) }}</span><span class="price-unit"> / 张</span></template></el-table-column>
        <el-table-column label="状态" width="88"><template #default="{ row }"><span :class="['status-dot', { 'is-active': row.enabled === 1 }]">{{ row.enabled === 1 ? '启用' : '停用' }}</span></template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{ row }"><div class="row-actions"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row.id)">删除</el-button></div></template></el-table-column>
      </el-table>
      <div class="table-footer">
        <span>共 {{ page.total }} 个图片模型</span>
        <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" background layout="sizes, prev, pager, next" :total="page.total" @size-change="loadData" @current-change="loadData" />
      </div>
    </div>

    <CrudDialog v-model="visible" :title="form.id ? '编辑图片模型' : '新增图片模型'" @submit="submit">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="模型服务商" prop="providerId"><el-select v-model="form.providerId" style="width:100%"><el-option v-for="item in providers" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="模型标识" prop="modelKey"><el-input v-model="form.modelKey" placeholder="gpt-image-2-1k" /></el-form-item>
        <el-form-item label="显示名称" prop="displayName"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="上游模型"><el-input v-model="form.upstreamModel" /></el-form-item>
        <el-form-item label="生成路径"><el-input v-model="form.generationPath" /></el-form-item>
        <el-form-item label="编辑路径"><el-input v-model="form.editPath" /></el-form-item>
        <el-form-item label="单张价格（USD）" prop="unitPriceUsd"><el-input-number v-model="form.unitPriceUsd" :min="0" :precision="4" :step="0.01" style="width:100%" /></el-form-item>
        <el-form-item label="异步模式"><el-switch v-model="form.asyncMode" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="支持蒙版"><el-switch v-model="form.supportsMask" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.modelSort" :min="0" /></el-form-item>
      </el-form>
    </CrudDialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudDialog from '../../components/CrudDialog.vue'
import { imageModelApi, modelProviderApi } from '../../api/system'

const list = ref([]), providers = ref([]), visible = ref(false), formRef = ref(null)
const filters = reactive({ name: '' }), page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const empty = () => ({ id: null, providerId: null, modelKey: '', displayName: '', upstreamModel: '', generationPath: '/v1/images/generations', editPath: '/v1/images/edits', asyncMode: 1, supportsMask: 1, unitPriceUsd: 0.5, enabled: 1, modelSort: 1 })
const form = reactive(empty())
const rules = { providerId: [{ required: true, message: '请选择服务商' }], modelKey: [{ required: true, message: '请输入模型标识' }], displayName: [{ required: true, message: '请输入显示名称' }], unitPriceUsd: [{ required: true, message: '请输入单张价格' }] }
const providerName = (id) => providers.value.find(item => item.id === id)?.name || '-'

async function loadData() { const data = await imageModelApi.list({ ...page, name: filters.name || undefined }); list.value = data.records; Object.assign(page, { total: data.total, pageNum: data.pageNum, pageSize: data.pageSize }) }
function handleSearch() { page.pageNum = 1; loadData() }
function resetSearch() { filters.name = ''; page.pageNum = 1; loadData() }
function openCreate() { Object.assign(form, empty()); form.providerId = providers.value[0]?.id ?? null; visible.value = true }
function openEdit(row) { Object.assign(form, row); visible.value = true }
async function submit() { if (!await formRef.value?.validate().catch(() => false)) return; form.id ? await imageModelApi.update(form.id, form) : await imageModelApi.add(form); visible.value = false; ElMessage.success('保存成功'); await loadData() }
async function remove(id) { await ElMessageBox.confirm('确认删除该图片模型吗？', '提示'); await imageModelApi.remove(id); ElMessage.success('删除成功'); await loadData() }
onMounted(async () => { providers.value = await modelProviderApi.options(); await loadData() })
</script>
