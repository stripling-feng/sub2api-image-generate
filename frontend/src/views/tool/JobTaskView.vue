<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div class="page-actions">
        <el-button v-permission="'tool:job:add'" type="primary" @click="openCreate">新增任务</el-button>
      </div>

      <div class="filter-card">
        <el-form :inline="true" :model="filters" class="filter-form" label-position="top">
          <el-form-item label="任务名称" class="field-task-name">
            <el-input v-model="filters.taskName" clearable placeholder="任务名称" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="类名" class="field-class-name">
            <el-input v-model="filters.className" clearable placeholder="类名" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="状态" class="field-task-status">
            <el-select v-model="filters.status" clearable placeholder="状态">
              <el-option label="启用" :value="1" />
              <el-option label="暂停" :value="0" />
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
      <el-table-column prop="taskName" label="任务名称" min-width="140" />
      <el-table-column prop="taskGroup" label="分组" min-width="120" />
      <el-table-column prop="cronExpression" label="Cron" min-width="170" />
      <el-table-column prop="className" label="类名" min-width="220" show-overflow-tooltip />
      <el-table-column prop="methodName" label="方法名" min-width="140" />
      <el-table-column prop="methodParam" label="方法参数" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '暂停' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="调度状态" width="100">
        <template #default="{ row }">
          <el-tag :type="stateTagType(row.schedulerState)">{{ stateLabel(row.schedulerState) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="nextFireTime" label="下次执行" min-width="170" />
      <el-table-column prop="previousFireTime" label="上次执行" min-width="170" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="info" @click="openLogs(row)">日志</el-button>
          <el-button v-permission="'tool:job:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'tool:job:run'" link type="success" @click="handleRun(row.id)">执行一次</el-button>
          <el-button v-if="row.status === 1" v-permission="'tool:job:pause'" link type="warning" @click="handlePause(row.id)">暂停</el-button>
          <el-button v-else v-permission="'tool:job:pause'" link type="primary" @click="handleResume(row.id)">恢复</el-button>
          <el-button v-permission="'tool:job:remove'" link type="danger" @click="handleDelete(row.id)">删除</el-button>
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

    <CrudDialog v-model="visible" :title="form.id ? '编辑任务' : '新增任务'" @submit="handleSubmit">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-form-item label="任务名称" prop="taskName"><el-input v-model="form.taskName" /></el-form-item>
        <el-form-item label="任务分组" prop="taskGroup"><el-input v-model="form.taskGroup" /></el-form-item>
        <el-form-item label="Cron" prop="cronExpression"><el-input v-model="form.cronExpression" placeholder="例如: 0 0/5 * * * ?" /></el-form-item>
        <el-form-item label="类名" prop="className"><el-input v-model="form.className" placeholder="例如: com.feng.system.task.DemoTask" /></el-form-item>
        <el-form-item label="方法名" prop="methodName"><el-input v-model="form.methodName" placeholder="仅支持无参或单 String 参数方法" /></el-form-item>
        <el-form-item label="方法参数"><el-input v-model="form.methodParam" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
    </CrudDialog>

    <el-dialog v-model="logVisible" title="执行日志" width="1100px" append-to-body>
      <div class="filter-card log-filter-card">
        <el-form :inline="true" :model="logFilters" class="filter-form" label-position="top">
          <el-form-item label="执行状态" class="field-log-status">
            <el-select v-model="logFilters.executeStatus" clearable placeholder="执行状态">
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" @click="loadLogs">查询</el-button>
            <el-button @click="resetLogFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table :data="logList" border max-height="520">
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.executeStatus === 1 ? 'success' : 'danger'">{{ row.executeStatus === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" min-width="170" />
        <el-table-column prop="endTime" label="结束时间" min-width="170" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="executeResult" label="执行结果" min-width="220" show-overflow-tooltip />
        <el-table-column prop="errorMessage" label="错误信息" min-width="260" show-overflow-tooltip />
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="logPagination.pageNum"
          v-model:page-size="logPagination.pageSize"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="logPagination.total"
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudDialog from '../../components/CrudDialog.vue'
import { jobTaskApi, jobTaskLogApi } from '../../api/system'

const visible = ref(false)
const formRef = ref(null)
const logVisible = ref(false)
const currentTaskId = ref(null)
const list = ref([])
const logList = ref([])

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const logPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const filters = reactive({ taskName: '', className: '', status: undefined })
const logFilters = reactive({ executeStatus: undefined })

const emptyForm = () => ({
  id: null,
  taskName: '',
  taskGroup: 'DEFAULT',
  cronExpression: '0 0/5 * * * ?',
  className: '',
  methodName: '',
  methodParam: '',
  status: 1,
  remark: '',
})
const form = reactive(emptyForm())
const formRules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskGroup: [{ required: true, message: '请输入任务分组', trigger: 'blur' }],
  cronExpression: [{ required: true, message: '请输入 Cron 表达式', trigger: 'blur' }],
  className: [{ required: true, message: '请输入类名', trigger: 'blur' }],
  methodName: [{ required: true, message: '请输入方法名', trigger: 'blur' }],
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
  filters.taskName = ''
  filters.className = ''
  filters.status = undefined
  pagination.pageNum = 1
  loadData()
}

function resetLogFilters() {
  logFilters.executeStatus = undefined
  logPagination.pageNum = 1
  loadLogs()
}

async function loadData() {
  const data = await jobTaskApi.list({
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    taskName: filters.taskName || undefined,
    className: filters.className || undefined,
    status: filters.status,
  })
  list.value = data.records
  pagination.total = data.total
  pagination.pageNum = data.pageNum
  pagination.pageSize = data.pageSize
}

async function loadLogs() {
  if (!currentTaskId.value) return
  const data = await jobTaskLogApi.list({
    pageNum: logPagination.pageNum,
    pageSize: logPagination.pageSize,
    taskId: currentTaskId.value,
    executeStatus: logFilters.executeStatus,
  })
  logList.value = data.records
  logPagination.total = data.total
  logPagination.pageNum = data.pageNum
  logPagination.pageSize = data.pageSize
}

function openCreate() {
  resetForm()
  visible.value = true
}

function openEdit(row) {
  resetForm()
  Object.assign(form, row)
  visible.value = true
}

async function openLogs(row) {
  currentTaskId.value = row.id
  logFilters.executeStatus = undefined
  logPagination.pageNum = 1
  logVisible.value = true
  await loadLogs()
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.id) await jobTaskApi.update(form.id, form)
  else await jobTaskApi.add(form)
  ElMessage.success('保存成功')
  visible.value = false
  await loadData()
}

async function handlePause(id) {
  await jobTaskApi.pause(id)
  ElMessage.success('任务已暂停')
  await loadData()
}

async function handleResume(id) {
  await jobTaskApi.resume(id)
  ElMessage.success('任务已恢复')
  await loadData()
}

async function handleRun(id) {
  await jobTaskApi.runOnce(id)
  ElMessage.success('任务已触发')
  await loadData()
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确认删除该任务吗？', '提示')
  await jobTaskApi.remove(id)
  ElMessage.success('删除成功')
  await loadData()
}

function stateLabel(state) {
  if (state === 'NORMAL') return '运行中'
  if (state === 'PAUSED') return '已暂停'
  if (state === 'BLOCKED') return '阻塞'
  if (state === 'ERROR') return '异常'
  if (state === 'COMPLETE') return '完成'
  return '未调度'
}

function stateTagType(state) {
  if (state === 'NORMAL') return 'success'
  if (state === 'PAUSED') return 'info'
  if (state === 'ERROR' || state === 'BLOCKED') return 'danger'
  return 'warning'
}

onMounted(loadData)
</script>

<style scoped>
.field-task-name,
.field-class-name {
  min-width: 260px;
  flex: 1 1 260px;
}

.field-task-status,
.field-log-status {
  width: 160px;
}

.log-filter-card {
  margin-bottom: 16px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
