<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div class="filter-card">
        <el-form :inline="true" :model="filters" class="filter-form" label-position="top">
          <el-form-item label="接口" class="field-api-name">
            <el-input v-model="filters.apiName" clearable placeholder="接口" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="操作人" class="field-operator">
            <el-input v-model="filters.operatorName" clearable placeholder="操作人" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="结果" class="field-result">
            <el-select v-model="filters.success" clearable placeholder="结果">
              <el-option :value="1" label="成功" />
              <el-option :value="0" label="失败" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <el-table :data="list" border>
      <el-table-column prop="operationTime" label="时间" min-width="170" />
      <el-table-column prop="apiName" label="接口" min-width="120" />
      <el-table-column prop="businessType" label="类型" min-width="90" />
      <el-table-column prop="operatorName" label="操作人" min-width="120" />
      <el-table-column prop="ipAddress" label="IP" min-width="130" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="requestUri" label="地址" min-width="180" show-overflow-tooltip />
      <el-table-column label="内容" min-width="260">
        <template #default="{ row }">
          <el-popover v-if="row.afterData" placement="left" width="420" trigger="click">
            <template #reference>
              <el-button link type="primary">查看内容</el-button>
            </template>
            <pre class="json-box">{{ formatJson(row.afterData) }}</pre>
          </el-popover>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="异常信息" min-width="220" show-overflow-tooltip />
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { operLogApi } from '../../api/system'

const list = ref([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})
const filters = reactive({
  apiName: '',
  operatorName: '',
  success: null,
})

function formatJson(value) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function handleSearch() {
  pagination.pageNum = 1
  loadData()
}

function resetFilters() {
  filters.apiName = ''
  filters.operatorName = ''
  filters.success = null
  pagination.pageNum = 1
  loadData()
}

async function loadData() {
  const data = await operLogApi.list({
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    apiName: filters.apiName || undefined,
    operatorName: filters.operatorName || undefined,
    success: filters.success ?? undefined,
  })
  list.value = data.records
  pagination.total = data.total
  pagination.pageNum = data.pageNum
  pagination.pageSize = data.pageSize
}

onMounted(loadData)
</script>

<style scoped>
.field-api-name,
.field-operator {
  min-width: 240px;
  flex: 1 1 240px;
}

.field-result {
  width: 160px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.json-box {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 12px;
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
}
</style>
