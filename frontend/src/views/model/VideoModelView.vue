<template>
  <div class="page-card data-page">
    <div class="data-page-header">
      <div class="data-page-heading">
        <h1>视频模型</h1>
        <p>配置视频生成服务、上游模型标识与计费规则。</p>
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
        <el-table-column label="模型" min-width="170">
          <template #default="{ row }"><span class="table-primary">{{ row.displayName }}</span><span class="model-key">{{ row.modelKey }}</span></template>
        </el-table-column>
        <el-table-column label="上游模型" min-width="190"><template #default="{ row }"><span class="table-code">{{ row.upstreamModel }}</span></template></el-table-column>
        <el-table-column label="模型能力" min-width="250">
          <template #default="{ row }"><div class="capability-list"><el-tag v-for="item in capabilities(row.modelKey)" :key="item" size="small" effect="plain">{{ item }}</el-tag></div></template>
        </el-table-column>
        <el-table-column label="计费" width="150">
          <template #default="{ row }"><span class="table-primary">${{ price(row.unitPriceUsd) }}</span><span class="price-unit"> / {{ row.billingMode === 'PER_SECOND' ? '秒' : '次' }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="88"><template #default="{ row }"><span :class="['status-dot', { 'is-active': row.enabled === 1 }]">{{ row.enabled === 1 ? '启用' : '停用' }}</span></template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{ row }"><div class="row-actions"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row.id)">删除</el-button></div></template></el-table-column>
      </el-table>
      <div class="table-footer">
        <span>共 {{ page.total }} 个视频模型</span>
        <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" background layout="sizes, prev, pager, next" :total="page.total" @size-change="loadData" @current-change="loadData" />
      </div>
    </div>

    <CrudDialog v-model="visible" :title="form.id ? '编辑视频模型' : '新增视频模型'" @submit="submit">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="118px">
        <el-form-item label="模型模板" prop="modelKey">
          <el-select v-model="form.modelKey" :disabled="Boolean(form.id)" style="width:100%" @change="applyTemplate">
            <el-option v-for="item in templates" :key="item.key" :label="item.name" :value="item.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型服务商" prop="providerId"><el-select v-model="form.providerId" style="width:100%"><el-option v-for="item in providers" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="显示名称" prop="displayName"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="上游模型标识" prop="upstreamModel"><el-input v-model="form.upstreamModel" /></el-form-item>
        <el-form-item label="生成路径" prop="generationPath"><el-input v-model="form.generationPath" /></el-form-item>
        <el-form-item label="计费方式" prop="billingMode">
          <el-radio-group v-model="form.billingMode"><el-radio-button value="PER_REQUEST">按次</el-radio-button><el-radio-button value="PER_SECOND">按秒</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item :label="form.billingMode === 'PER_SECOND' ? '每秒价格（USD）' : '每次价格（USD）'" prop="unitPriceUsd">
          <el-input-number v-model="form.unitPriceUsd" :min="0" :precision="6" :step="0.01" style="width:100%" />
        </el-form-item>
        <el-form-item label="模型能力"><div class="capability-list"><el-tag v-for="item in capabilities(form.modelKey)" :key="item" effect="plain">{{ item }}</el-tag></div></el-form-item>
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
import { modelProviderApi, videoModelApi } from '../../api/system'

const templates = [
  { key: 'seedance-2.0', name: 'Seedance 2.0', upstream: 'seedance-2.0', path: '/v1/videos' },
  { key: 'seedance-2.0-fast', name: 'Seedance 2.0 Fast', upstream: 'seedance-2.0-fast', path: '/v1/videos' },
  { key: 'seedance-2.0-mini', name: 'Seedance 2.0 Mini', upstream: 'seedance-2.0-mini', path: '/v1/videos' },
  { key: 'grok-video', name: 'Grok Video', upstream: 'cy-gv1-grok-video', path: '/v1/videos' },
  { key: 'grok-video-1.5', name: 'Grok Video 1.5', upstream: 'grok-video-1.5', path: '/v1/videos' },
  { key: 'omni-fast', name: 'Omni Fast', upstream: 'omni-fast', path: '/v1/videos' },
  { key: 'omni-fast-no-water', name: 'Omni Fast (No Watermark)', upstream: 'omni-fast-no-water', path: '/v1/videos' },
  { key: 'omni-v2v', name: 'Omni V2V', upstream: 'omni-v2v', path: '/v1/videos' },
  { key: 'omni-v2v-no-water', name: 'Omni V2V (No Watermark)', upstream: 'omni-v2v-no-water', path: '/v1/videos' },
]
const list = ref([]), providers = ref([]), visible = ref(false), formRef = ref(null)
const filters = reactive({ name: '' }), page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const empty = () => ({ id: null, providerId: null, modelKey: 'seedance-2.0', displayName: 'Seedance 2.0', upstreamModel: 'seedance-2.0', generationPath: '/v1/videos', billingMode: 'PER_REQUEST', unitPriceUsd: 0, enabled: 0, modelSort: 1 })
const form = reactive(empty())
const rules = { providerId: [{ required: true, message: '请选择服务商' }], modelKey: [{ required: true, message: '请选择模型模板' }], displayName: [{ required: true, message: '请输入显示名称' }], upstreamModel: [{ required: true, message: '请输入上游模型标识' }], generationPath: [{ required: true, message: '请输入生成路径' }], unitPriceUsd: [{ required: true, message: '请输入价格' }] }

function capabilities(key) {
  if (key.startsWith('omni-fast')) return ['文生 / 图生', '首尾帧', '最多 5 图', '10 秒 / 720p', '16:9 / 9:16']
  if (key.startsWith('omni-v2v')) return ['视频必填', '1–2 视频', '可选 2 图', '10 秒 / 720p', '16:9 / 9:16']
  if (key === 'grok-video-1.5') return ['单图必填', '4–15 秒', '480p / 720p', '横屏 / 竖屏']
  if (key === 'grok-video') return ['文生 / 图生', '最多 7 图', '视频编辑', '480p / 720p']
  return ['文生 / 图生', '首尾帧', '4 图 / 3 视频 / 1 音频', '原生音频']
}
function price(value) { return Number(value || 0).toFixed(4).replace(/0+$/, '').replace(/\.$/, '') || '0' }
function applyTemplate(key) { const item = templates.find(value => value.key === key); if (item) Object.assign(form, { displayName: item.name, upstreamModel: item.upstream, generationPath: item.path }) }
async function loadData() { const data = await videoModelApi.list({ ...page, name: filters.name || undefined }); list.value = data.records; Object.assign(page, { total: data.total, pageNum: data.pageNum, pageSize: data.pageSize }) }
function handleSearch() { page.pageNum = 1; loadData() }
function resetSearch() { filters.name = ''; page.pageNum = 1; loadData() }
function openCreate() { Object.assign(form, empty()); form.providerId = providers.value[0]?.id ?? null; visible.value = true }
function openEdit(row) { Object.assign(form, row); visible.value = true }
async function submit() { if (!await formRef.value?.validate().catch(() => false)) return; form.id ? await videoModelApi.update(form.id, form) : await videoModelApi.add(form); visible.value = false; ElMessage.success('保存成功'); await loadData() }
async function remove(id) { await ElMessageBox.confirm('确认删除该视频模型吗？', '提示'); await videoModelApi.remove(id); ElMessage.success('删除成功'); await loadData() }
onMounted(async () => { providers.value = await modelProviderApi.options(); await loadData() })
</script>

<style scoped>
.model-key { display: block; margin-top: 4px; color: var(--el-text-color-secondary); font: 12px/1.3 ui-monospace, monospace; }
.capability-list { display: flex; flex-wrap: wrap; gap: 6px; min-width: 0; }
</style>
