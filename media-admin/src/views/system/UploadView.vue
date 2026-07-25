<template>
  <div class="page-card upload-page">
    <div class="page-toolbar">
      <div class="page-actions">
        <input
          ref="fileInputRef"
          class="hidden-input"
          type="file"
          @change="handleFileChange"
        />
        <el-button
          v-permission="'system:upload:add'"
          type="primary"
          :loading="uploading"
          @click="triggerUpload"
        >
          上传文件
        </el-button>
      </div>
    </div>

    <el-table :data="list" border>
      <el-table-column
        prop="originalName"
        label="原名"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column
        prop="currentName"
        label="存储文件名"
        min-width="240"
        show-overflow-tooltip
      />
      <el-table-column label="文件预览">
        <template #default="{ row }">
          <div class="preview-card-item" @click="handlePreview(row)">
            <template v-if="isImage(row) && getPreviewSource(row)">
              <img
                :src="getPreviewSource(row)"
                class="preview-thumb image"
                alt="preview"
              />
            </template>
            <template v-else-if="isVideo(row) && getPreviewSource(row)">
              <div class="preview-thumb video-wrap">
                <video
                  :src="getPreviewSource(row)"
                  class="preview-thumb video"
                  muted
                  preload="metadata"
                />
                <span class="play-badge">
                  <el-icon><VideoPlay /></el-icon>
                </span>
              </div>
            </template>
            <template v-else>
              <div class="preview-thumb icon-wrap" :class="iconClass(row)">
                <el-icon class="file-icon">
                  <Picture v-if="isImage(row)" />
                  <VideoPlay v-else-if="isVideo(row)" />
                  <Document v-else-if="isPdf(row)" />
                  <Files v-else />
                </el-icon>
                <span class="ext-badge">{{ getFileBadge(row) }}</span>
              </div>
            </template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="文件大小" min-width="120">
        <template #default="{ row }">{{
          formatFileSize(row.fileSize)
        }}</template>
      </el-table-column>
      <el-table-column
        prop="fileType"
        label="文件类型"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column
        prop="md5Value"
        label="MD5 值"
        min-width="280"
        show-overflow-tooltip
      />
      <el-table-column prop="createTime" label="上传时间" min-width="180" />
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

    <el-dialog
      v-model="previewDialog.visible"
      :title="previewDialog.title"
      width="760px"
      destroy-on-close
      append-to-body
    >
      <div
        v-if="previewDialog.kind === 'image'"
        class="dialog-preview image-dialog"
      >
        <img :src="previewDialog.url" alt="preview" />
      </div>
      <div v-else-if="previewDialog.kind === 'video'" class="dialog-preview">
        <video
          :src="previewDialog.url"
          controls
          autoplay
          class="dialog-video"
        />
      </div>
      <div v-else-if="previewDialog.kind === 'pdf'" class="dialog-preview">
        <iframe
          :src="previewDialog.url"
          class="dialog-pdf"
          title="pdf-preview"
        />
      </div>
      <template #footer>
        <el-button @click="previewDialog.visible = false">关闭</el-button>
        <el-button type="primary" @click="downloadFile(previewDialog.row)"
          >下载文件</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import axios from "axios";
import { reactive, ref, onMounted, onBeforeUnmount } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Document, Files, Picture, VideoPlay } from "@element-plus/icons-vue";
import { uploadFileApi } from "../../api/system";
import { getToken } from "../../utils/auth";

const list = ref([]);
const fileInputRef = ref(null);
const uploading = ref(false);
const previewUrlMap = reactive({});

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
});

const previewDialog = reactive({
  visible: false,
  kind: "",
  title: "",
  url: "",
  row: null,
});

function formatFileSize(size) {
  if (!size && size !== 0) {
    return "-";
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(2)} KB`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(2)} MB`;
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function getExtension(row) {
  const name = row?.originalName || row?.currentName || "";
  const index = name.lastIndexOf(".");
  if (index < 0 || index === name.length - 1) {
    return "";
  }
  return name.slice(index + 1).toLowerCase();
}

function isAccessibleUrl(path) {
  return (
    typeof path === "string" &&
    (path.startsWith("/") || /^(https?:)?\/\//.test(path))
  );
}

function isImage(row) {
  return (
    row?.fileType?.startsWith("image/") ||
    ["png", "jpg", "jpeg", "gif", "webp", "bmp", "svg"].includes(
      getExtension(row),
    )
  );
}

function isVideo(row) {
  return (
    row?.fileType?.startsWith("video/") ||
    ["mp4", "webm", "ogg", "mov", "m4v"].includes(getExtension(row))
  );
}

function isPdf(row) {
  return row?.fileType?.includes("pdf") || getExtension(row) === "pdf";
}

function isPreviewable(row) {
  return isImage(row) || isVideo(row) || isPdf(row);
}

function iconClass(row) {
  if (isImage(row)) return "icon-image";
  if (isVideo(row)) return "icon-video";
  if (isPdf(row)) return "icon-pdf";
  return "icon-file";
}

function getFileBadge(row) {
  const ext = getExtension(row);
  return ext ? ext.slice(0, 5).toUpperCase() : "FILE";
}

function buildContentUrl(id, download = false) {
  return `/api/system/upload-files/${id}/content${download ? "?download=true" : ""}`;
}

function getAuthHeaders() {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function revokePreviewUrls() {
  Object.values(previewUrlMap).forEach((url) => {
    if (typeof url === "string" && url.startsWith("blob:")) {
      URL.revokeObjectURL(url);
    }
  });
  Object.keys(previewUrlMap).forEach((key) => {
    delete previewUrlMap[key];
  });
}

async function fetchFileBlob(row, download = false) {
  const response = await axios.get(buildContentUrl(row.id, download), {
    baseURL: "/",
    responseType: "blob",
    headers: getAuthHeaders(),
  });
  return response.data;
}

async function ensurePreviewBlobUrl(row) {
  if (previewUrlMap[row.id]) {
    return previewUrlMap[row.id];
  }
  const blob = await fetchFileBlob(row, false);
  const objectUrl = URL.createObjectURL(blob);
  previewUrlMap[row.id] = objectUrl;
  return objectUrl;
}

function getPreviewSource(row) {
  if (previewUrlMap[row.id]) {
    return previewUrlMap[row.id];
  }
  if (isAccessibleUrl(row.filePath)) {
    return row.filePath;
  }
  return "";
}

async function prepareInlinePreview(row) {
  if (!isPreviewable(row) || isAccessibleUrl(row.filePath)) {
    return;
  }
  if (!isImage(row) && !isVideo(row)) {
    return;
  }
  try {
    await ensurePreviewBlobUrl(row);
  } catch (error) {
    console.error("prepare preview failed", error);
  }
}

function triggerUpload() {
  fileInputRef.value?.click();
}

async function handleFileChange(event) {
  const file = event.target.files?.[0];
  if (!file) {
    return;
  }
  uploading.value = true;
  try {
    await uploadFileApi.upload(file);
    ElMessage.success("上传成功");
    await loadData();
  } finally {
    uploading.value = false;
    event.target.value = "";
  }
}

async function handlePreview(row) {
  if (!isPreviewable(row)) {
    await askDownload(row);
    return;
  }

  let previewUrl = getPreviewSource(row);
  if (!previewUrl) {
    try {
      previewUrl = await ensurePreviewBlobUrl(row);
    } catch {
      ElMessage.error("当前文件暂时无法预览");
      return;
    }
  }

  previewDialog.kind = isImage(row) ? "image" : isVideo(row) ? "video" : "pdf";
  previewDialog.title = row.originalName || row.currentName || "文件预览";
  previewDialog.url = previewUrl;
  previewDialog.row = row;
  previewDialog.visible = true;
}

async function askDownload(row) {
  try {
    await ElMessageBox.confirm(
      "当前文件类型暂不支持直接预览，是否下载该文件？",
      "文件预览",
      {
        type: "info",
        confirmButtonText: "下载",
        cancelButtonText: "取消",
      },
    );
    await downloadFile(row);
  } catch {
    // ignore
  }
}

async function downloadFile(row) {
  if (!row) {
    return;
  }
  if (isAccessibleUrl(row.filePath)) {
    const link = document.createElement("a");
    link.href = row.filePath;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.download = row.originalName || row.currentName || "download";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    return;
  }

  const blob = await fetchFileBlob(row, true);
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = row.originalName || row.currentName || "download";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(objectUrl);
}

async function loadData() {
  revokePreviewUrls();
  const data = await uploadFileApi.list({
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
  });
  list.value = data.records;
  pagination.total = data.total;
  pagination.pageNum = data.pageNum;
  pagination.pageSize = data.pageSize;
  await Promise.all(list.value.map((row) => prepareInlinePreview(row)));
}

onMounted(loadData);
onBeforeUnmount(revokePreviewUrls);
</script>

<style scoped>
.upload-page {
  display: flex;
  flex-direction: column;
}

.hidden-input {
  display: none;
}

.preview-card-item {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.preview-thumb {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  object-fit: cover;
  overflow: hidden;
  background: linear-gradient(
    180deg,
    rgba(247, 250, 255, 0.95),
    rgba(239, 244, 255, 0.95)
  );
}

.preview-thumb.video-wrap,
.preview-thumb.icon-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-thumb.video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.play-badge {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #fff;
  background: rgba(15, 23, 42, 0.68);
}

.file-icon {
  font-size: 26px;
}

.icon-image {
  color: #2563eb;
}

.icon-video {
  color: #dc2626;
}

.icon-pdf {
  color: #ea580c;
}

.icon-file {
  color: #475569;
}

.ext-badge {
  position: absolute;
  bottom: 6px;
  right: 6px;
  padding: 2px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  color: #0f172a;
  background: rgba(255, 255, 255, 0.9);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.dialog-preview {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 420px;
}

.image-dialog img {
  max-width: 100%;
  max-height: 70vh;
  border-radius: 12px;
}

.dialog-video {
  width: 100%;
  max-height: 70vh;
  border-radius: 12px;
  background: #000;
}

.dialog-pdf {
  width: 100%;
  height: 70vh;
  border: none;
  border-radius: 12px;
  background: #fff;
}
</style>
