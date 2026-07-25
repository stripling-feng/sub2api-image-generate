<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div class="page-actions">
        <el-button
          v-permission="'system:user:resetPassword'"
          :disabled="selectedIds.length === 0"
          @click="handleBatchResetPassword"
        >
          批量重置密码
        </el-button>
        <el-button
          v-permission="'system:user:add'"
          type="primary"
          @click="openCreate"
          >新增用户</el-button
        >
      </div>

      <div class="filter-card">
        <el-form
          :inline="true"
          :model="filters"
          class="filter-form"
          label-position="top"
        >
          <el-form-item label="用户名" class="field-username">
            <el-input
              v-model="filters.username"
              clearable
              placeholder="用户名模糊搜索"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="部门" class="field-dept">
            <el-tree-select
              v-model="filters.deptId"
              clearable
              check-strictly
              node-key="id"
              placeholder="部门筛选"
              :data="deptTree"
              :props="{ label: 'menuName', children: 'children' }"
            />
          </el-form-item>
          <el-form-item label="岗位" class="field-post">
            <el-select
              v-model="filters.postId"
              clearable
              placeholder="岗位筛选"
            >
              <el-option
                v-for="post in posts"
                :key="post.id"
                :label="post.postName"
                :value="post.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="角色" class="field-role">
            <el-select
              v-model="filters.roleId"
              clearable
              placeholder="角色筛选"
            >
              <el-option
                v-for="role in roles"
                :key="role.id"
                :label="role.roleName"
                :value="role.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <el-table :data="list" border @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="52" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column prop="nickname" label="昵称" min-width="140" />
      <el-table-column label="部门" min-width="160">
        <template #default="{ row }">{{ getDeptName(row.deptId) }}</template>
      </el-table-column>
      <el-table-column label="岗位" min-width="140">
        <template #default="{ row }">{{ getPostName(row.postId) }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">{{
          getRoleNames(row.roleIds).join("、") || "-"
        }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" min-width="140" />
      <el-table-column prop="email" label="邮箱" min-width="220" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? "启用" : "停用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="180" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'system:user:edit'"
            link
            type="primary"
            @click="openEdit(row)"
            >编辑</el-button
          >
          <el-button
            v-permission="'system:user:resetPassword'"
            link
            type="warning"
            @click="handleResetPassword(row.id)"
          >
            重置密码
          </el-button>
          <el-button
            v-permission="'system:user:remove'"
            link
            type="danger"
            @click="handleDelete(row.id)"
            >删除</el-button
          >
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

    <CrudDialog
      v-model="visible"
      :title="form.id ? '编辑用户' : '新增用户'"
      @submit="handleSubmit"
    >
      <el-alert
        v-if="!form.id"
        :title="`新增用户默认密码为：${defaultPassword}`"
        type="info"
        :closable="false"
        class="password-tip"
      />
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="Boolean(form.id)" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="部门" prop="deptId">
          <el-tree-select
            v-model="form.deptId"
            check-strictly
            node-key="id"
            placeholder="请选择部门"
            style="width: 100%"
            :data="deptTree"
            :props="{ label: 'menuName', children: 'children' }"
          />
        </el-form-item>
        <el-form-item label="岗位" prop="postId">
          <el-select
            v-model="form.postId"
            placeholder="请选择岗位"
            style="width: 100%"
          >
            <el-option
              v-for="post in posts"
              :key="post.id"
              :label="post.postName"
              :value="post.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select
            v-model="form.roleIds"
            multiple
            placeholder="请选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
    </CrudDialog>

    <el-dialog
      v-model="resetVisible"
      :title="`重置密码${resetTarget === 'batch' ? '（批量）' : ''}`"
      width="460px"
      append-to-body
      @close="resetResetForm"
    >
      <el-form
        ref="resetFormRef"
        :model="resetPwdForm"
        :rules="resetFormRules"
        label-width="100px"
      >
        <el-form-item label="密码方式">
          <el-radio-group v-model="resetPwdForm.mode">
            <el-radio value="default">系统默认密码</el-radio>
            <el-radio value="custom">自定义密码</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="resetPwdForm.mode === 'default'" label="预览密码">
          <el-input :model-value="defaultPassword" disabled />
        </el-form-item>
        <el-form-item
          v-if="resetPwdForm.mode === 'custom'"
          label="自定义密码"
          prop="password"
        >
          <el-input
            v-model="resetPwdForm.password"
            type="password"
            show-password
            placeholder="请输入自定义密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmResetPassword"
          >确认重置</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import CrudDialog from "../../components/CrudDialog.vue";
import {
  deptApi,
  postApi,
  roleApi,
  systemConfigApi,
  userApi,
} from "../../api/system";

const visible = ref(false);
const formRef = ref(null);
const list = ref([]);
const roles = ref([]);
const posts = ref([]);
const deptTree = ref([]);
const deptFlat = ref([]);
const selectedIds = ref([]);
const defaultPassword = ref("admin123");

// Reset password dialog state
const resetVisible = ref(false);
const resetFormRef = ref(null);
const resetTarget = ref("single");
const resetSingleId = ref(null);
const resetPwdForm = reactive({
  mode: "default",
  password: "",
});
const resetFormRules = {
  password: [
    { required: true, message: "请输入自定义密码", trigger: "blur" },
    { min: 6, message: "密码长度至少 6 个字符", trigger: "blur" },
  ],
};

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
});

const filters = reactive({
  username: "",
  deptId: null,
  postId: null,
  roleId: null,
});

const emptyForm = () => ({
  id: null,
  username: "",
  nickname: "",
  deptId: null,
  postId: null,
  roleIds: [],
  phone: "",
  email: "",
  status: 1,
});

const form = reactive(emptyForm());
const formRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 2, max: 20, message: "用户名长度为 2-20 个字符", trigger: "blur" },
  ],
  nickname: [{ required: true, message: "请输入昵称", trigger: "blur" }],
  deptId: [{ required: true, message: "请选择部门", trigger: "change" }],
  postId: [{ required: true, message: "请选择岗位", trigger: "change" }],
  roleIds: [
    {
      required: true,
      type: "array",
      min: 1,
      message: "请选择至少一个角色",
      trigger: "change",
    },
  ],
  phone: [
    { pattern: /^1\d{10}$/, message: "手机号格式不正确", trigger: "blur" },
  ],
  email: [{ type: "email", message: "邮箱格式不正确", trigger: "blur" }],
};

function flattenDeptTree(nodes, result = []) {
  for (const node of nodes) {
    result.push({ id: node.id, menuName: node.menuName });
    if (node.children?.length) flattenDeptTree(node.children, result);
  }
  return result;
}

function getDeptName(deptId) {
  return deptFlat.value.find((item) => item.id === deptId)?.menuName || "-";
}

function getPostName(postId) {
  return posts.value.find((item) => item.id === postId)?.postName || "-";
}

function getRoleNames(roleIds = []) {
  return roles.value
    .filter((item) => roleIds.includes(item.id))
    .map((item) => item.roleName);
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map((item) => item.id);
}

function resetForm() {
  Object.assign(form, emptyForm());
  formRef.value?.clearValidate();
}

function resetFilters() {
  filters.username = "";
  filters.deptId = null;
  filters.postId = null;
  filters.roleId = null;
  pagination.pageNum = 1;
  loadData();
}

function handleSearch() {
  pagination.pageNum = 1;
  loadData();
}

async function loadOptions() {
  const [roleList, postList, deptList, password] = await Promise.all([
    roleApi.options(),
    postApi.options(),
    deptApi.tree(),
    systemConfigApi.defaultPassword(),
  ]);
  roles.value = roleList;
  posts.value = postList;
  deptTree.value = deptList;
  deptFlat.value = flattenDeptTree(deptList);
  defaultPassword.value = password || "admin123";
}

async function loadData() {
  const data = await userApi.list({
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    username: filters.username || undefined,
    deptId: filters.deptId || undefined,
    postId: filters.postId || undefined,
    roleId: filters.roleId || undefined,
  });
  list.value = data.records;
  selectedIds.value = [];
  pagination.total = data.total;
  pagination.pageNum = data.pageNum;
  pagination.pageSize = data.pageSize;
}

function openCreate() {
  resetForm();
  form.deptId = deptFlat.value[0]?.id || null;
  form.postId = posts.value[0]?.id || null;
  visible.value = true;
}

function openEdit(row) {
  resetForm();
  Object.assign(form, {
    ...row,
    roleIds: [...(row.roleIds || [])],
  });
  visible.value = true;
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  if (form.id) {
    await userApi.update(form.id, form);
  } else {
    await userApi.add(form);
  }
  ElMessage.success("保存成功");
  visible.value = false;
  await loadData();
}

function handleResetPassword(id) {
  resetTarget.value = "single";
  resetSingleId.value = id;
  resetPwdForm.mode = "default";
  resetPwdForm.password = "";
  resetVisible.value = true;
}

function handleBatchResetPassword() {
  if (selectedIds.value.length === 0) return;
  resetTarget.value = "batch";
  resetPwdForm.mode = "default";
  resetPwdForm.password = "";
  resetVisible.value = true;
}

async function confirmResetPassword() {
  const valid = await resetFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  const password =
    resetPwdForm.mode === "custom" ? resetPwdForm.password : null;
  const showPassword =
    resetPwdForm.mode === "custom"
      ? resetPwdForm.password
      : defaultPassword.value;

  if (resetTarget.value === "single") {
    await userApi.resetPassword(resetSingleId.value, password);
    ElMessage.success(`重置密码成功，密码为 ${showPassword}`);
  } else {
    await userApi.batchResetPassword(selectedIds.value, password);
    ElMessage.success(`批量重置密码成功，密码为 ${showPassword}`);
    await loadData();
  }
  resetVisible.value = false;
}

function resetResetForm() {
  resetFormRef.value?.clearValidate();
  resetPwdForm.mode = "default";
  resetPwdForm.password = "";
}

async function handleDelete(id) {
  await ElMessageBox.confirm("确认删除该用户吗？", "提示");
  await userApi.remove(id);
  ElMessage.success("删除成功");
  await loadData();
}

onMounted(async () => {
  await loadOptions();
  await loadData();
});
</script>

<style scoped>
.field-username,
.field-dept,
.field-post,
.field-role {
  min-width: 220px;
  flex: 1 1 220px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.password-tip {
  margin-bottom: 16px;
}
</style>
