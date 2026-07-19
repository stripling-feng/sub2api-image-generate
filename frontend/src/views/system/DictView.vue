<template>
  <div class="dict-page">
    <div class="page-card dict-card">
      <div class="page-toolbar">
        <div class="page-actions">
          <el-button v-permission="'system:dict:add'" type="primary" @click="openTypeCreate">新增字典</el-button>
        </div>

        <div class="filter-card">
          <el-form :inline="true" :model="typeFilters" class="filter-form" label-position="top">
            <el-form-item label="字典名称" class="field-type-name">
              <el-input v-model="typeFilters.typeName" clearable placeholder="字典名称" @keyup.enter="handleTypeSearch" />
            </el-form-item>
            <el-form-item label="字典类型" class="field-type-code">
              <el-input v-model="typeFilters.typeCode" clearable placeholder="字典类型" @keyup.enter="handleTypeSearch" />
            </el-form-item>
            <el-form-item label="操作">
              <el-button type="primary" @click="handleTypeSearch">查询</el-button>
              <el-button @click="resetTypeFilters">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="dict-layout">
        <div class="dict-panel">
          <div class="panel-header">
            <div>
              <div class="panel-title">字典类型</div>
              <div class="panel-subtitle">维护业务字典分类和类型编码</div>
            </div>
          </div>

          <el-table
            :data="typeList"
            border
            highlight-current-row
            height="100%"
            @current-change="handleTypeCurrentChange"
            @row-click="handleTypeRowClick"
          >
            <el-table-column prop="typeName" label="字典名称" min-width="140" />
            <el-table-column prop="typeCode" label="字典类型" min-width="180" />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'system:dict:edit'" link type="primary" @click.stop="openTypeEdit(row)">编辑</el-button>
                <el-button v-permission="'system:dict:remove'" link type="danger" @click.stop="handleTypeDelete(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="typePagination.pageNum"
              v-model:page-size="typePagination.pageSize"
              background
              layout="total, sizes, prev, pager, next"
              :total="typePagination.total"
              @size-change="loadTypeList"
              @current-change="loadTypeList"
            />
          </div>
        </div>

        <div class="dict-panel">
          <div class="panel-header">
            <div>
              <div class="panel-title">字典数据</div>
              <div class="panel-subtitle">
                <template v-if="selectedType">
                  当前类型：
                  <span class="type-pill">{{ selectedType.typeName }}</span>
                  <span class="type-pill code">{{ selectedType.typeCode }}</span>
                </template>
                <template v-else>
                  先选择左侧字典类型
                </template>
              </div>
            </div>
            <el-button v-permission="'system:dict:add'" type="primary" :disabled="!selectedType" @click="openDataCreate">新增数据</el-button>
          </div>

          <div class="filter-card data-filter-card">
            <el-form :inline="true" :model="dataFilters" class="filter-form" label-position="top">
              <el-form-item label="字典标签" class="field-data-label">
                <el-input v-model="dataFilters.dictLabel" clearable placeholder="字典标签" @keyup.enter="handleDataSearch" />
              </el-form-item>
              <el-form-item label="字典键值" class="field-data-value">
                <el-input v-model="dataFilters.dictValue" clearable placeholder="字典键值" @keyup.enter="handleDataSearch" />
              </el-form-item>
              <el-form-item label="操作">
                <el-button type="primary" :disabled="!selectedType" @click="handleDataSearch">查询</el-button>
                <el-button :disabled="!selectedType" @click="resetDataFilters">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <el-table v-if="selectedType" :data="dataList" border height="100%">
            <el-table-column prop="dictLabel" label="字典标签" min-width="140" />
            <el-table-column prop="dictValue" label="字典键值" min-width="120" />
            <el-table-column prop="dictSort" label="排序" width="80" />
            <el-table-column label="标签样式" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.tagType || 'info'">{{ row.tagType || 'default' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'system:dict:edit'" link type="primary" @click="openDataEdit(row)">编辑</el-button>
                <el-button v-permission="'system:dict:remove'" link type="danger" @click="handleDataDelete(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="dict-empty">
            <el-empty description="请先选择字典类型后查看数据" />
          </div>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="dataPagination.pageNum"
              v-model:page-size="dataPagination.pageSize"
              background
              layout="total, sizes, prev, pager, next"
              :total="dataPagination.total"
              :disabled="!selectedType"
              @size-change="loadDataList"
              @current-change="loadDataList"
            />
          </div>
        </div>
      </div>
    </div>

    <CrudDialog v-model="typeVisible" :title="typeForm.id ? '编辑字典类型' : '新增字典类型'" @submit="handleTypeSubmit">
      <el-form :model="typeForm" label-width="90px">
        <el-form-item label="字典名称"><el-input v-model="typeForm.typeName" /></el-form-item>
        <el-form-item label="字典类型"><el-input v-model="typeForm.typeCode" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="typeForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
    </CrudDialog>

    <CrudDialog v-model="dataVisible" :title="dataForm.id ? '编辑字典数据' : '新增字典数据'" @submit="handleDataSubmit">
      <el-form :model="dataForm" label-width="90px">
        <el-form-item label="字典标签"><el-input v-model="dataForm.dictLabel" /></el-form-item>
        <el-form-item label="字典键值"><el-input v-model="dataForm.dictValue" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="dataForm.dictSort" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="标签样式">
          <el-select v-model="dataForm.tagType" clearable placeholder="默认">
            <el-option label="默认" value="" />
            <el-option label="success" value="success" />
            <el-option label="info" value="info" />
            <el-option label="warning" value="warning" />
            <el-option label="danger" value="danger" />
          </el-select>
        </el-form-item>
        <el-form-item label="类名"><el-input v-model="dataForm.cssClass" placeholder="可选" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="dataForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
    </CrudDialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudDialog from '../../components/CrudDialog.vue'
import { dictApi } from '../../api/system'

const typeVisible = ref(false)
const dataVisible = ref(false)
const typeList = ref([])
const dataList = ref([])
const selectedType = ref(null)

const typePagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})

const dataPagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})

const typeFilters = reactive({
  typeName: '',
  typeCode: '',
})

const dataFilters = reactive({
  dictLabel: '',
  dictValue: '',
})

const emptyTypeForm = () => ({
  id: null,
  typeName: '',
  typeCode: '',
  remark: '',
})

const emptyDataForm = () => ({
  id: null,
  typeId: null,
  dictLabel: '',
  dictValue: '',
  dictSort: 1,
  tagType: '',
  cssClass: '',
  remark: '',
})

const typeForm = reactive(emptyTypeForm())
const dataForm = reactive(emptyDataForm())

function resetTypeForm() {
  Object.assign(typeForm, emptyTypeForm())
}

function resetDataForm() {
  Object.assign(dataForm, emptyDataForm())
}

function handleTypeSearch() {
  typePagination.pageNum = 1
  loadTypeList()
}

function resetTypeFilters() {
  typeFilters.typeName = ''
  typeFilters.typeCode = ''
  typePagination.pageNum = 1
  loadTypeList()
}

function handleDataSearch() {
  dataPagination.pageNum = 1
  loadDataList()
}

function resetDataFilters() {
  dataFilters.dictLabel = ''
  dataFilters.dictValue = ''
  dataPagination.pageNum = 1
  loadDataList()
}

async function loadTypeList() {
  const data = await dictApi.typeList({
    pageNum: typePagination.pageNum,
    pageSize: typePagination.pageSize,
    typeName: typeFilters.typeName || undefined,
    typeCode: typeFilters.typeCode || undefined,
  })
  typeList.value = data.records
  typePagination.total = data.total
  typePagination.pageNum = data.pageNum
  typePagination.pageSize = data.pageSize

  const nextSelected = selectedType.value
    ? typeList.value.find((item) => item.id === selectedType.value.id)
    : typeList.value[0]
  selectedType.value = nextSelected || null
  dataPagination.pageNum = 1
  await loadDataList()
}

async function loadDataList() {
  if (!selectedType.value) {
    dataList.value = []
    dataPagination.total = 0
    return
  }
  const data = await dictApi.dataList({
    pageNum: dataPagination.pageNum,
    pageSize: dataPagination.pageSize,
    typeId: selectedType.value.id,
    dictLabel: dataFilters.dictLabel || undefined,
    dictValue: dataFilters.dictValue || undefined,
  })
  dataList.value = data.records
  dataPagination.total = data.total
  dataPagination.pageNum = data.pageNum
  dataPagination.pageSize = data.pageSize
}

function handleTypeCurrentChange(row) {
  if (!row || row.id === selectedType.value?.id) {
    return
  }
  selectedType.value = row
  dataPagination.pageNum = 1
  loadDataList()
}

function handleTypeRowClick(row) {
  handleTypeCurrentChange(row)
}

function openTypeCreate() {
  resetTypeForm()
  typeVisible.value = true
}

function openTypeEdit(row) {
  resetTypeForm()
  Object.assign(typeForm, {
    id: row.id,
    typeName: row.typeName,
    typeCode: row.typeCode,
    remark: row.remark || '',
  })
  typeVisible.value = true
}

async function handleTypeSubmit() {
  if (typeForm.id) {
    await dictApi.updateType(typeForm.id, typeForm)
  } else {
    await dictApi.addType(typeForm)
  }
  ElMessage.success('保存成功')
  typeVisible.value = false
  await loadTypeList()
}

async function handleTypeDelete(id) {
  await ElMessageBox.confirm('确认删除该字典类型吗？', '提示')
  await dictApi.removeType(id)
  ElMessage.success('删除成功')
  if (selectedType.value?.id === id) {
    selectedType.value = null
  }
  await loadTypeList()
}

function openDataCreate() {
  if (!selectedType.value) {
    ElMessage.warning('请先选择字典类型')
    return
  }
  resetDataForm()
  dataForm.typeId = selectedType.value.id
  dataVisible.value = true
}

function openDataEdit(row) {
  resetDataForm()
  Object.assign(dataForm, {
    id: row.id,
    typeId: row.typeId || selectedType.value?.id || null,
    dictLabel: row.dictLabel,
    dictValue: row.dictValue,
    dictSort: row.dictSort,
    tagType: row.tagType || '',
    cssClass: row.cssClass || '',
    remark: row.remark || '',
  })
  dataVisible.value = true
}

async function handleDataSubmit() {
  dataForm.typeId = selectedType.value?.id || dataForm.typeId
  if (!dataForm.typeId) {
    ElMessage.warning('请先选择字典类型')
    return
  }
  if (dataForm.id) {
    await dictApi.updateData(dataForm.id, dataForm)
  } else {
    await dictApi.addData(dataForm)
  }
  ElMessage.success('保存成功')
  dataVisible.value = false
  await loadDataList()
}

async function handleDataDelete(id) {
  await ElMessageBox.confirm('确认删除该字典数据吗？', '提示')
  await dictApi.removeData(id)
  ElMessage.success('删除成功')
  await loadDataList()
}

onMounted(loadTypeList)
</script>

<style scoped>
.dict-page {
  height: 100%;
}

.dict-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.dict-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(360px, 0.95fr) minmax(500px, 1.25fr);
  gap: 18px;
}

.dict-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 250, 255, 0.96));
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.panel-title {
  font-size: 18px;
  font-weight: 800;
}

.panel-subtitle {
  margin-top: 6px;
  color: var(--muted);
  font-size: 13px;
}

.type-pill {
  display: inline-flex;
  align-items: center;
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--primary-soft);
  color: var(--primary-deep);
  font-weight: 700;
}

.type-pill.code {
  background: rgba(15, 23, 42, 0.06);
  color: var(--text);
}

.data-filter-card {
  margin-bottom: 14px;
}

.dict-empty {
  flex: 1;
  display: grid;
  place-items: center;
}

.field-type-name,
.field-type-code,
.field-data-label,
.field-data-value {
  min-width: 200px;
  flex: 1 1 200px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 1180px) {
  .dict-layout {
    grid-template-columns: 1fr;
  }
}
</style>
