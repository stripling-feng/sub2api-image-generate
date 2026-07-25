<template>
  <div class="docs-config-page">
    <section class="page-card docs-config-shell">
      <div class="shell-header">
        <div>
          <div class="shell-eyebrow">PUBLIC DOCS</div>
          <h2>前台文档配置</h2>
          <p>绑定后台上传的 Markdown 文件，前台 /index 文档弹窗会按图片文档和视频文档动态读取。</p>
        </div>
        <div class="header-actions">
          <el-button :loading="loading" @click="loadData">刷新</el-button>
          <el-button
            v-permission="'system:docs:edit'"
            type="primary"
            :loading="saving"
            @click="handleSave"
          >
            保存配置
          </el-button>
        </div>
      </div>

      <el-alert
        class="docs-alert"
        type="info"
        :closable="false"
        show-icon
      >
        <template #title>
          配置 Key 固定为 docs.image.file-id 和 docs.video.file-id，仅支持绑定 .md / text/markdown 文件。
        </template>
      </el-alert>

      <div v-loading="loading" class="docs-grid">
        <article
          v-for="item in docItems"
          :key="item.key"
          class="doc-config-card"
        >
          <div class="card-head">
            <div>
              <div class="card-label">{{ item.label }}</div>
              <h3>{{ item.title }}</h3>
            </div>
            <el-tag type="info" effect="plain">{{ item.configKey }}</el-tag>
          </div>

          <div class="bound-file">
            <div class="file-icon">MD</div>
            <div class="file-main">
              <div class="file-name">
                {{ currentFile(item.key)?.originalName || "未绑定 Markdown 文档" }}
              </div>
              <div class="file-meta">
                <span>文件 ID：{{ form[item.formKey] || "-" }}</span>
                <span v-if="currentFile(item.key)?.fileSize">大小：{{ formatFileSize(currentFile(item.key)?.fileSize) }}</span>
              </div>
            </div>
          </div>

          <el-form label-position="top">
            <el-form-item label="上传并绑定 Markdown">
              <input
                :ref="(el) => setFileInputRef(item.key, el)"
                class="hidden-input"
                type="file"
                accept=".md,text/markdown"
                @change="(event) => handleFileChange(item.key, event)"
              />
              <div class="inline-actions">
                <el-button
                  v-permission="'system:upload:add'"
                  :loading="uploadingKey === item.key"
                  @click="triggerUpload(item.key)"
                >
                  上传 .md 文件
                </el-button>
                <el-button @click="clearBinding(item.key)">清空绑定</el-button>
              </div>
            </el-form-item>

            <el-form-item label="也可以直接填写已上传文件 ID">
              <el-input-number
                v-model="form[item.formKey]"
                :min="1"
                :precision="0"
                :controls="false"
                placeholder="请输入 sys_upload_file.id"
                style="width: 100%"
              />
            </el-form-item>
          </el-form>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { publicDocsConfigApi, uploadFileApi } from "../../api/system";

const docItems = [
  {
    key: "image",
    label: "图片文档",
    title: "图片接口 Markdown",
    configKey: "docs.image.file-id",
    formKey: "imageFileId",
  },
  {
    key: "video",
    label: "视频文档",
    title: "视频接口 Markdown",
    configKey: "docs.video.file-id",
    formKey: "videoFileId",
  },
];

const loading = ref(false);
const saving = ref(false);
const uploadingKey = ref("");
const fileInputRefs = reactive({});
const boundFiles = reactive({
  image: null,
  video: null,
});
const form = reactive({
  imageFileId: null,
  videoFileId: null,
});

function setFileInputRef(key, element) {
  if (element) fileInputRefs[key] = element;
}

function currentFile(key) {
  return boundFiles[key];
}

function formatFileSize(size) {
  if (!size && size !== 0) return "-";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
}

function triggerUpload(key) {
  fileInputRefs[key]?.click();
}

function isMarkdownFile(file) {
  return file?.name?.toLowerCase().endsWith(".md") || ["text/markdown", "text/x-markdown"].includes(file?.type);
}

async function handleFileChange(key, event) {
  const file = event.target.files?.[0];
  if (!file) return;
  if (!isMarkdownFile(file)) {
    ElMessage.warning("只能上传 Markdown(.md) 文件");
    event.target.value = "";
    return;
  }

  uploadingKey.value = key;
  try {
    const uploaded = await uploadFileApi.upload(file);
    const item = docItems.find((doc) => doc.key === key);
    form[item.formKey] = uploaded.id;
    boundFiles[key] = uploaded;
    ElMessage.success("上传成功，已填入文件 ID");
  } finally {
    uploadingKey.value = "";
    event.target.value = "";
  }
}

function clearBinding(key) {
  const item = docItems.find((doc) => doc.key === key);
  form[item.formKey] = null;
  boundFiles[key] = null;
}

async function loadData() {
  loading.value = true;
  try {
    const data = await publicDocsConfigApi.detail();
    form.imageFileId = data.image?.fileId || null;
    form.videoFileId = data.video?.fileId || null;
    boundFiles.image = data.image?.fileId ? data.image : null;
    boundFiles.video = data.video?.fileId ? data.video : null;
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  saving.value = true;
  try {
    await publicDocsConfigApi.update({
      imageFileId: form.imageFileId || null,
      videoFileId: form.videoFileId || null,
    });
    ElMessage.success("文档配置已保存");
    await loadData();
  } finally {
    saving.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.docs-config-page {
  min-height: 100%;
}

.docs-config-shell {
  padding: 24px;
}

.shell-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border);
}

.shell-eyebrow {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: var(--primary);
}

.shell-header h2 {
  margin: 8px 0 6px;
  font-size: 28px;
}

.shell-header p {
  margin: 0;
  color: var(--muted);
}

.header-actions,
.inline-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.docs-alert {
  margin: 18px 0;
}

.docs-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.doc-config-card {
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 250, 255, 0.98));
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.card-label {
  color: var(--primary);
  font-size: 13px;
  font-weight: 800;
}

.card-head h3 {
  margin: 6px 0 0;
  font-size: 20px;
}

.bound-file {
  display: flex;
  gap: 14px;
  align-items: center;
  margin: 18px 0;
  padding: 14px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 14px;
  background: rgba(37, 99, 235, 0.04);
}

.file-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--primary);
  color: #fff;
  font-size: 13px;
  font-weight: 900;
}

.file-main {
  min-width: 0;
}

.file-name {
  overflow: hidden;
  color: var(--text);
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

.hidden-input {
  display: none;
}

@media (max-width: 900px) {
  .shell-header {
    flex-direction: column;
  }

  .docs-grid {
    grid-template-columns: 1fr;
  }
}
</style>
