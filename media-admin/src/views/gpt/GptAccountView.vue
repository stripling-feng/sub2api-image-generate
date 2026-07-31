<template>
  <div class="page-card data-page gpt-account-page">
    <div class="data-page-header">
      <div class="data-page-heading">
        <h1>账号管理</h1>
        <p>导入 ChatGPT Access Token，集中查看账号状态、当前套餐和 Plus 优惠资格。</p>
      </div>
      <el-button v-permission="'gpt:account:import'" type="primary" :icon="Upload" @click="openImport">
        导入 Access Token
      </el-button>
    </div>

    <div class="account-overview" aria-label="账号概览">
      <div>
        <span class="overview-value">{{ page.total }}</span>
        <span class="overview-label">账号总数</span>
      </div>
      <div>
        <span class="overview-value">{{ activeCount }}</span>
        <span class="overview-label">本页可用</span>
      </div>
      <div>
        <span class="overview-value">{{ freePlanCount }}</span>
        <span class="overview-label">本页 Free</span>
      </div>
      <div>
        <span class="overview-value">{{ reviewCount }}</span>
        <span class="overview-label">资格待确认</span>
      </div>
    </div>

    <div class="data-filter-bar account-filter-bar">
      <el-input
        v-model="filters.keyword"
        clearable
        :prefix-icon="Search"
        placeholder="邮箱、名称或 Account ID"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="filters.planType" clearable placeholder="当前套餐">
        <el-option label="Free" value="free" />
        <el-option label="Plus" value="plus" />
        <el-option label="Pro" value="pro" />
        <el-option label="Team" value="team" />
      </el-select>
      <el-select v-model="filters.plusEligibility" clearable placeholder="免费 Plus 资格">
        <el-option label="符合" value="ELIGIBLE" />
        <el-option label="待结算确认" value="REVIEW" />
        <el-option label="已是 Plus" value="ALREADY_PLUS" />
        <el-option label="无资格证据" value="NOT_ELIGIBLE" />
      </el-select>
      <el-select v-model="filters.used" clearable placeholder="使用状态">
        <el-option label="已使用" value="true" />
        <el-option label="未使用" value="false" />
      </el-select>
      <el-select v-model="filters.accountStatus" clearable placeholder="账号状态">
        <el-option label="可用" value="ACTIVE" />
        <el-option label="不可访问" value="INACCESSIBLE" />
        <el-option label="已停用" value="DEACTIVATED" />
        <el-option label="Token 已过期" value="EXPIRED" />
        <el-option label="检查失败" value="ERROR" />
      </el-select>
      <el-button type="primary" plain :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="RefreshLeft" @click="resetSearch">重置</el-button>
      <el-button
        v-permission="'gpt:account:refresh'"
        :icon="Refresh"
        :disabled="selectedIds.length === 0"
        :loading="batchRefreshing"
        @click="refreshSelected"
      >
        刷新选中
      </el-button>
      <el-button
        v-permission="'gpt:account:remove'"
        type="danger"
        plain
        :icon="Delete"
        :disabled="selectedIds.length === 0"
        :loading="batchDeleting"
        @click="removeSelected"
      >
        删除选中
      </el-button>
    </div>

    <div class="data-table-shell">
      <el-table
        v-loading="loading"
        :data="list"
        table-layout="fixed"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="账号" min-width="230">
          <template #default="{ row }">
            <div class="account-cell">
              <span class="table-primary">{{ row.email || row.displayName || '未识别邮箱' }}</span>
              <span class="account-id">{{ row.accountId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="当前套餐" width="142">
          <template #default="{ row }">
            <div class="plan-cell">
              <el-tag :type="planTagType(row.planType)" effect="light">{{ planLabel(row.planType) }}</el-tag>
              <span>{{ row.subscriptionPlan || '无订阅' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="免费 Plus 资格" min-width="210">
          <template #default="{ row }">
            <el-tooltip :content="row.eligibilityReason || '暂无判断依据'" placement="top" :show-after="250">
              <span :class="['eligibility-state', `is-${String(row.plusEligibility || 'unknown').toLowerCase()}`]">
                {{ eligibilityLabel(row.plusEligibility) }}
              </span>
            </el-tooltip>
            <div class="eligibility-reason">{{ row.eligibilityReason || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="124">
          <template #default="{ row }">
            <span :class="['status-dot', { 'is-active': row.accountStatus === 'ACTIVE' }]">
              {{ accountStatusLabel(row.accountStatus) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="使用状态" width="118" align="center">
          <template #default="{ row }">
            <el-switch
              v-permission="'gpt:account:refresh'"
              :model-value="Boolean(row.used)"
              :loading="updatingUsedId === row.id"
              inline-prompt
              active-text="已"
              inactive-text="未"
              @change="(value) => updateUsed(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="Token" min-width="156">
          <template #default="{ row }">
            <div class="token-cell">
              <code>{{ row.tokenFingerprint }}...</code>
              <span :class="{ 'is-expired': isExpired(row.tokenExpiresAt) }">
                {{ row.tokenExpiresAt ? `${formatDate(row.tokenExpiresAt)} 到期` : '到期时间未知' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最后检查" width="158">
          <template #default="{ row }">
            <div class="checked-cell">
              <span>{{ formatDate(row.lastCheckedAt) }}</span>
              <el-tooltip v-if="row.lastError" :content="row.lastError" placement="top">
                <span class="check-error">检查失败</span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="112" fixed="right" align="center" class-name="operation-column">
          <template #default="{ row }">
            <div class="row-actions">
              <el-tooltip content="刷新状态" placement="top" :show-after="250">
                <el-button
                  v-permission="'gpt:account:refresh'"
                  circle
                  text
                  type="primary"
                  :icon="Refresh"
                  :loading="refreshingId === row.id"
                  @click="refreshOne(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除账号" placement="top" :show-after="250">
                <el-button
                  v-permission="'gpt:account:remove'"
                  circle
                  text
                  type="danger"
                  :icon="Delete"
                  @click="remove(row)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <span>共 {{ page.total }} 个账号</span>
        <el-pagination
          v-model:current-page="page.pageNum"
          v-model:page-size="page.pageSize"
          background
          layout="sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="page.total"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog
      v-model="importVisible"
      title="导入 Access Token"
      width="620px"
      append-to-body
      @closed="resetImport"
    >
      <div class="import-guidance">
        <el-icon><Lock /></el-icon>
        <div>
          <strong>Token 将在服务端明文保存</strong>
          <span>每行输入一个 Token，最多 50 个；列表仅显示 Token 指纹。</span>
        </div>
      </div>
      <el-form label-position="top">
        <el-form-item label="Access Token">
          <el-input
            v-model="importText"
            type="textarea"
            :rows="10"
            resize="vertical"
            autocomplete="off"
            placeholder="eyJ...&#10;eyJ..."
          />
        </el-form-item>
      </el-form>
      <div v-if="importResult" class="import-result">
        <span class="result-success">成功 {{ importResult.succeeded }}</span>
        <span :class="{ 'result-failed': importResult.failed > 0 }">失败 {{ importResult.failed }}</span>
        <ul v-if="importResult.failed > 0">
          <li v-for="(item, index) in failedImports" :key="index">第 {{ item.index + 1 }} 个：{{ item.message }}</li>
        </ul>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :icon="Upload" :loading="importing" @click="submitImport">导入并检查状态</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Lock, Refresh, RefreshLeft, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { gptAccountApi } from '../../api/system'

const loading = ref(false)
const importing = ref(false)
const batchRefreshing = ref(false)
const batchDeleting = ref(false)
const refreshingId = ref(null)
const updatingUsedId = ref(null)
const importVisible = ref(false)
const importText = ref('')
const importResult = ref(null)
const list = ref([])
const selectedIds = ref([])
const filters = reactive({ keyword: '', planType: '', accountStatus: '', plusEligibility: '', used: '' })
const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })

const activeCount = computed(() => list.value.filter((item) => item.accountStatus === 'ACTIVE').length)
const freePlanCount = computed(() => list.value.filter((item) => item.planType === 'free').length)
const reviewCount = computed(() => list.value.filter((item) => item.plusEligibility === 'REVIEW').length)
const failedImports = computed(() => (importResult.value?.items || [])
  .map((item, index) => ({ ...item, index }))
  .filter((item) => !item.success))

async function loadData() {
  loading.value = true
  try {
    const data = await gptAccountApi.list({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      keyword: filters.keyword || undefined,
      planType: filters.planType || undefined,
      accountStatus: filters.accountStatus || undefined,
      plusEligibility: filters.plusEligibility || undefined,
      used: filters.used === '' ? undefined : filters.used,
    })
    list.value = data.records || []
    Object.assign(page, { total: data.total, pageNum: data.pageNum, pageSize: data.pageSize })
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.pageNum = 1
  loadData()
}

function resetSearch() {
  Object.assign(filters, { keyword: '', planType: '', accountStatus: '', plusEligibility: '', used: '' })
  page.pageNum = 1
  loadData()
}

function openImport() {
  importVisible.value = true
}

function resetImport() {
  importText.value = ''
  importResult.value = null
}

async function submitImport() {
  const tokens = importText.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean)
  if (tokens.length === 0) {
    ElMessage.warning('请至少输入一个 Access Token')
    return
  }
  if (tokens.length > 50) {
    ElMessage.warning('单次最多导入 50 个 Access Token')
    return
  }
  importing.value = true
  try {
    importResult.value = await gptAccountApi.importTokens(tokens)
    importText.value = ''
    if (importResult.value.succeeded > 0) {
      ElMessage.success(`成功导入 ${importResult.value.succeeded} 个账号`)
      await loadData()
    }
    if (importResult.value.failed === 0) importVisible.value = false
  } finally {
    importing.value = false
  }
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map((row) => row.id)
}

async function refreshOne(row) {
  refreshingId.value = row.id
  try {
    const updated = await gptAccountApi.refresh(row.id)
    const index = list.value.findIndex((item) => item.id === row.id)
    if (index >= 0) list.value.splice(index, 1, updated)
    ElMessage.success(updated.lastError ? '状态检查完成，账号存在异常' : '账号状态已更新')
  } finally {
    refreshingId.value = null
  }
}

async function refreshSelected() {
  batchRefreshing.value = true
  try {
    await gptAccountApi.refreshBatch(selectedIds.value)
    ElMessage.success(`已检查 ${selectedIds.value.length} 个账号`)
    await loadData()
  } finally {
    batchRefreshing.value = false
  }
}

async function removeSelected() {
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个账号吗？`, '批量删除账号', { type: 'warning' })
  batchDeleting.value = true
  try {
    await gptAccountApi.removeBatch(selectedIds.value)
    ElMessage.success(`已删除 ${selectedIds.value.length} 个账号`)
    await loadData()
  } finally {
    batchDeleting.value = false
  }
}

async function updateUsed(row, value) {
  const previous = Boolean(row.used)
  row.used = value
  updatingUsedId.value = row.id
  try {
    const updated = await gptAccountApi.updateUsed(row.id, value)
    const index = list.value.findIndex((item) => item.id === row.id)
    if (index >= 0) list.value.splice(index, 1, updated)
    ElMessage.success(value ? '已标记为已使用' : '已标记为未使用')
  } catch (error) {
    row.used = previous
    throw error
  } finally {
    updatingUsedId.value = null
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除 ${row.email || row.accountId} 吗？`, '删除账号', { type: 'warning' })
  await gptAccountApi.remove(row.id)
  ElMessage.success('账号已删除')
  await loadData()
}

function planLabel(plan) {
  const labels = { free: 'Free', plus: 'Plus', pro: 'Pro', team: 'Team' }
  return labels[String(plan || '').toLowerCase()] || plan || 'Unknown'
}

function planTagType(plan) {
  const value = String(plan || '').toLowerCase()
  if (value === 'plus') return 'success'
  if (value === 'pro' || value === 'team') return 'warning'
  return 'info'
}

function eligibilityLabel(status) {
  return {
    ELIGIBLE: '符合免费资格',
    REVIEW: '待结算确认',
    ALREADY_PLUS: '已是 Plus',
    NOT_ELIGIBLE: '无资格证据',
  }[status] || '未知'
}

function accountStatusLabel(status) {
  return {
    ACTIVE: '可用',
    INACCESSIBLE: '不可访问',
    DEACTIVATED: '已停用',
    EXPIRED: 'Token 已过期',
    ERROR: '检查失败',
  }[status] || status || '未知'
}

function formatDate(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value))
}

function isExpired(value) {
  return value ? new Date(value).getTime() < Date.now() : false
}

onMounted(loadData)
</script>

<style scoped>
.gpt-account-page {
  height: 100%;
  min-height: 0;
  gap: 16px;
}

.gpt-account-page.data-page {
  align-self: stretch;
}

.account-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}

.account-overview > div {
  display: flex;
  align-items: baseline;
  gap: 9px;
  min-width: 0;
  padding: 14px 18px;
  border-right: 1px solid var(--border);
}

.account-overview > div:last-child {
  border-right: 0;
}

.overview-value {
  color: var(--text);
  font-size: 22px;
  font-weight: 780;
}

.overview-label {
  color: var(--muted);
  font-size: 12px;
}

.account-filter-bar {
  flex-shrink: 0;
  flex-wrap: wrap;
}

.account-filter-bar .el-input {
  width: min(280px, 100%);
}

.account-filter-bar .el-select {
  width: 152px;
}

.account-cell,
.plan-cell,
.token-cell,
.checked-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.account-id,
.plan-cell span,
.token-cell span,
.checked-cell {
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-cell code {
  color: #334155;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
}

:deep(.operation-column) {
  background: #fff !important;
}

.row-actions {
  justify-content: center;
  gap: 8px;
}

.row-actions .el-button {
  width: 30px;
  height: 30px;
  margin-left: 0 !important;
  padding: 0;
  border-radius: 50%;
}

.token-cell .is-expired,
.check-error {
  color: #dc2626;
}

.eligibility-state {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.eligibility-state::before {
  width: 7px;
  height: 7px;
  content: "";
  background: #94a3b8;
  border-radius: 50%;
}

.eligibility-state.is-eligible {
  color: #15803d;
}

.eligibility-state.is-eligible::before {
  background: #22c55e;
}

.eligibility-state.is-review {
  color: #b45309;
}

.eligibility-state.is-review::before {
  background: #f59e0b;
}

.eligibility-state.is-already_plus {
  color: #2563eb;
}

.eligibility-state.is-already_plus::before {
  background: #3b82f6;
}

.eligibility-reason {
  max-width: 260px;
  margin-top: 5px;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-guidance {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 18px;
  padding: 14px;
  color: #334155;
  background: #f6f8fc;
  border-left: 3px solid var(--primary);
}

:deep(.data-table-shell) {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

:deep(.data-table-shell .el-table) {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

:deep(.data-table-shell .el-table__inner-wrapper) {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

:deep(.data-table-shell .el-table__body-wrapper) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

:deep(.data-table-shell .table-footer) {
  flex-shrink: 0;
}

.import-guidance .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--primary);
}

.import-guidance strong,
.import-guidance span {
  display: block;
}

.import-guidance span {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

.import-result {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  color: var(--muted-strong);
  font-size: 13px;
}

.result-success {
  color: #15803d;
}

.result-failed {
  color: #dc2626;
}

.import-result ul {
  flex: 1 0 100%;
  margin: 0;
  padding-left: 20px;
  color: #b91c1c;
}

@media (max-width: 900px) {
  .account-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .account-overview > div:nth-child(2) {
    border-right: 0;
  }

  .account-filter-bar .el-select,
  .account-filter-bar .el-button {
    flex: 1 1 140px;
  }
}
</style>
