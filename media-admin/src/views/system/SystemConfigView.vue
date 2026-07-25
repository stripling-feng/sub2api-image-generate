<template>
  <div class="config-page">
    <section class="page-card config-shell">
      <div class="shell-header">
        <div>
          <div class="shell-eyebrow">SYSTEM SETTINGS</div>
          <h2>系统配置</h2>
          <p>通过标签切换管理基础信息、安全策略和上传存储配置。</p>
        </div>
        <el-button
          v-permission="'system:config:edit'"
          type="primary"
          @click="handleSubmit"
        >
          保存配置
        </el-button>
      </div>

      <el-form ref="formRef" :model="form" :rules="formRules">
        <el-tabs v-model="activeTab" class="config-tabs">
          <el-tab-pane label="基础配置" name="basic">
            <div class="setting-group">
              <div class="group-title">站点信息</div>
              <div class="group-tip">
                影响侧边栏品牌、浏览器标题和版权信息。
              </div>
              <div class="basic-grid">
                <div class="basic-main-card">
                  <div class="basic-card-head">
                    <div class="basic-card-title">品牌主信息</div>
                    <div class="basic-card-tip">
                      这里控制系统标题、品牌文案和页脚展示。
                    </div>
                  </div>
                  <div class="basic-form-stack">
                    <el-form-item
                      class="field-card basic-field"
                      prop="siteName"
                    >
                      <template #label
                        ><span class="field-label">网站名称</span></template
                      >
                      <el-input
                        v-model="form.siteName"
                        placeholder="请输入完整站点名称"
                      />
                    </el-form-item>
                    <el-form-item
                      class="field-card basic-field"
                      prop="copyright"
                    >
                      <template #label
                        ><span class="field-label">版权信息</span></template
                      >
                      <el-input
                        v-model="form.copyright"
                        placeholder="请输入页脚版权文案"
                      />
                    </el-form-item>
                  </div>
                </div>

                <div class="basic-side-card">
                  <div class="basic-preview-mark">{{ shortNamePreview }}</div>
                  <div class="basic-preview-title">品牌角标</div>
                  <div class="basic-preview-tip">
                    侧边栏角标与部分紧凑场景会自动取网站名称前 2 位展示。
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="安全配置" name="security">
            <div class="setting-group">
              <div class="group-title">账号安全</div>
              <div class="group-tip">
                控制默认密码，以及登录失败后的统计和锁定策略。
              </div>
              <div class="field-grid">
                <el-form-item class="field-card full" prop="defaultPassword">
                  <template #label
                    ><span class="field-label">默认密码</span></template
                  >
                  <el-input v-model="form.defaultPassword" show-password />
                </el-form-item>
                <el-form-item class="field-card" prop="loginFailMaxAttempts">
                  <template #label
                    ><span class="field-label">失败限制次数</span></template
                  >
                  <el-input-number
                    v-model="form.loginFailMaxAttempts"
                    :min="1"
                    :max="20"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item class="field-card" prop="loginFailWindowMinutes">
                  <template #label
                    ><span class="field-label">统计窗口（分钟）</span></template
                  >
                  <el-input-number
                    v-model="form.loginFailWindowMinutes"
                    :min="1"
                    :max="1440"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item
                  class="field-card full"
                  prop="loginFailLockMinutes"
                >
                  <template #label
                    ><span class="field-label">锁定时长（分钟）</span></template
                  >
                  <el-input-number
                    v-model="form.loginFailLockMinutes"
                    :min="1"
                    :max="1440"
                    style="width: 100%"
                  />
                </el-form-item>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="上传配置" name="upload">
            <div class="setting-group">
              <div class="group-title">上传存储</div>
              <div class="group-tip">
                支持本地存储、MinIO 和阿里云
                OSS，切换后仅校验当前所选存储方式的必填项。
              </div>

              <el-form-item prop="uploadProvider" class="provider-form-item">
                <template #label
                  ><span class="field-label">存储方式</span></template
                >
                <el-radio-group
                  v-model="form.uploadProvider"
                  class="provider-grid"
                >
                  <el-radio-button
                    v-for="option in uploadProviderOptions"
                    :key="option.value"
                    :label="option.value"
                  >
                    {{ option.label }}
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>

              <div class="provider-panel">
                <div class="provider-intro">
                  <div class="provider-badge">
                    {{ currentUploadProvider.label }}
                  </div>
                  <h3>{{ currentUploadProvider.title }}</h3>
                  <p>{{ currentUploadProvider.description }}</p>
                </div>

                <div v-if="form.uploadProvider === 'server'" class="field-grid">
                  <el-form-item class="field-card" prop="uploadServerBasePath">
                    <template #label
                      ><span class="field-label">本地存储目录</span></template
                    >
                    <el-input
                      v-model="form.uploadServerBasePath"
                      placeholder="例如：uploads"
                    />
                  </el-form-item>
                  <el-form-item class="field-card" prop="uploadServerBaseUrl">
                    <template #label
                      ><span class="field-label">访问前缀</span></template
                    >
                    <el-input
                      v-model="form.uploadServerBaseUrl"
                      placeholder="可选，例如：https://static.example.com/uploads"
                    />
                  </el-form-item>
                </div>

                <div
                  v-else-if="form.uploadProvider === 'minio'"
                  class="field-grid"
                >
                  <el-form-item class="field-card" prop="uploadMinioEndpoint">
                    <template #label
                      ><span class="field-label">MinIO Endpoint</span></template
                    >
                    <el-input
                      v-model="form.uploadMinioEndpoint"
                      placeholder="例如：http://127.0.0.1:9000"
                    />
                  </el-form-item>
                  <el-form-item class="field-card" prop="uploadMinioBucket">
                    <template #label
                      ><span class="field-label">Bucket</span></template
                    >
                    <el-input
                      v-model="form.uploadMinioBucket"
                      placeholder="请输入 MinIO Bucket"
                    />
                  </el-form-item>
                  <el-form-item class="field-card" prop="uploadMinioAccessKey">
                    <template #label
                      ><span class="field-label">Access Key</span></template
                    >
                    <el-input
                      v-model="form.uploadMinioAccessKey"
                      placeholder="请输入 MinIO Access Key"
                    />
                  </el-form-item>
                  <el-form-item class="field-card" prop="uploadMinioSecretKey">
                    <template #label
                      ><span class="field-label">Secret Key</span></template
                    >
                    <el-input
                      v-model="form.uploadMinioSecretKey"
                      show-password
                      placeholder="请输入 MinIO Secret Key"
                    />
                  </el-form-item>
                  <el-form-item
                    class="field-card full"
                    prop="uploadMinioDomain"
                  >
                    <template #label
                      ><span class="field-label">自定义域名</span></template
                    >
                    <el-input
                      v-model="form.uploadMinioDomain"
                      placeholder="可选，例如：https://minio.example.com"
                    />
                  </el-form-item>
                </div>

                <div v-else class="field-grid">
                  <el-form-item class="field-card" prop="uploadOssEndpoint">
                    <template #label
                      ><span class="field-label">OSS Endpoint</span></template
                    >
                    <el-input
                      v-model="form.uploadOssEndpoint"
                      placeholder="例如：oss-cn-hangzhou.aliyuncs.com"
                    />
                  </el-form-item>
                  <el-form-item class="field-card" prop="uploadOssBucket">
                    <template #label
                      ><span class="field-label">Bucket</span></template
                    >
                    <el-input
                      v-model="form.uploadOssBucket"
                      placeholder="请输入阿里云 OSS Bucket"
                    />
                  </el-form-item>
                  <el-form-item class="field-card" prop="uploadOssAccessKeyId">
                    <template #label
                      ><span class="field-label">AccessKey ID</span></template
                    >
                    <el-input
                      v-model="form.uploadOssAccessKeyId"
                      placeholder="请输入阿里云 AccessKey ID"
                    />
                  </el-form-item>
                  <el-form-item
                    class="field-card"
                    prop="uploadOssAccessKeySecret"
                  >
                    <template #label
                      ><span class="field-label"
                        >AccessKey Secret</span
                      ></template
                    >
                    <el-input
                      v-model="form.uploadOssAccessKeySecret"
                      show-password
                      placeholder="请输入阿里云 AccessKey Secret"
                    />
                  </el-form-item>
                  <el-form-item class="field-card full" prop="uploadOssDomain">
                    <template #label
                      ><span class="field-label">自定义域名</span></template
                    >
                    <el-input
                      v-model="form.uploadOssDomain"
                      placeholder="可选，例如：https://cdn.example.com"
                    />
                  </el-form-item>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { systemConfigApi } from "../../api/system";
import { useAuthStore } from "../../stores/auth";

const authStore = useAuthStore();
const activeTab = ref("basic");
const formRef = ref(null);
const uploadProviderOptions = [
  {
    value: "server",
    label: "本地存储",
    title: "服务器本地存储",
    description:
      "文件保存到后端本地目录，适合单机部署或通过共享目录统一管理静态资源。",
  },
  {
    value: "minio",
    label: "MinIO 存储",
    title: "MinIO 对象存储",
    description:
      "兼容 S3 协议，适合私有化部署场景，支持自定义访问域名和独立 Bucket。",
  },
  {
    value: "aliyun-oss",
    label: "阿里云存储",
    title: "阿里云 OSS 存储",
    description:
      "适合公网访问和云上部署，配置 Endpoint、Bucket 与访问密钥后即可切换使用。",
  },
];

const form = reactive({
  siteName: "",
  copyright: "",
  defaultPassword: "",
  loginFailMaxAttempts: 5,
  loginFailWindowMinutes: 5,
  loginFailLockMinutes: 5,
  uploadProvider: "server",
  uploadServerBasePath: "uploads",
  uploadServerBaseUrl: "",
  uploadOssEndpoint: "",
  uploadOssBucket: "",
  uploadOssAccessKeyId: "",
  uploadOssAccessKeySecret: "",
  uploadOssDomain: "",
  uploadMinioEndpoint: "",
  uploadMinioBucket: "",
  uploadMinioAccessKey: "",
  uploadMinioSecretKey: "",
  uploadMinioDomain: "",
});

const currentUploadProvider = computed(
  () =>
    uploadProviderOptions.find((item) => item.value === form.uploadProvider) ||
    uploadProviderOptions[0],
);
const shortNamePreview = computed(() =>
  (form.siteName || "Feng AI Admin").slice(0, 2).toUpperCase(),
);

function validateUploadField(value, message) {
  return Boolean(String(value || "").trim()) || new Error(message);
}

function createProviderRule(providers, getter, message) {
  return {
    trigger: "blur",
    validator: (_, value, callback) => {
      if (!providers.includes(form.uploadProvider)) {
        callback();
        return;
      }

      const targetValue = getter ? getter() : value;
      const result = validateUploadField(targetValue, message);
      if (result === true) {
        callback();
        return;
      }
      callback(result);
    },
  };
}

const formRules = {
  siteName: [{ required: true, message: "请输入网站名称", trigger: "blur" }],
  copyright: [{ required: true, message: "请输入版权信息", trigger: "blur" }],
  defaultPassword: [
    { required: true, message: "请输入默认密码", trigger: "blur" },
  ],
  loginFailMaxAttempts: [
    {
      required: true,
      type: "number",
      message: "请输入失败限制次数",
      trigger: "change",
    },
  ],
  loginFailWindowMinutes: [
    {
      required: true,
      type: "number",
      message: "请输入统计窗口",
      trigger: "change",
    },
  ],
  loginFailLockMinutes: [
    {
      required: true,
      type: "number",
      message: "请输入锁定时长",
      trigger: "change",
    },
  ],
  uploadProvider: [
    { required: true, message: "请选择上传存储方式", trigger: "change" },
  ],
  uploadServerBasePath: [
    createProviderRule(
      ["server"],
      () => form.uploadServerBasePath,
      "请输入本地存储目录",
    ),
  ],
  uploadMinioEndpoint: [
    createProviderRule(
      ["minio"],
      () => form.uploadMinioEndpoint,
      "请输入 MinIO Endpoint",
    ),
  ],
  uploadMinioBucket: [
    createProviderRule(
      ["minio"],
      () => form.uploadMinioBucket,
      "请输入 MinIO Bucket",
    ),
  ],
  uploadMinioAccessKey: [
    createProviderRule(
      ["minio"],
      () => form.uploadMinioAccessKey,
      "请输入 MinIO Access Key",
    ),
  ],
  uploadMinioSecretKey: [
    createProviderRule(
      ["minio"],
      () => form.uploadMinioSecretKey,
      "请输入 MinIO Secret Key",
    ),
  ],
  uploadOssEndpoint: [
    createProviderRule(
      ["aliyun-oss"],
      () => form.uploadOssEndpoint,
      "请输入 OSS Endpoint",
    ),
  ],
  uploadOssBucket: [
    createProviderRule(
      ["aliyun-oss"],
      () => form.uploadOssBucket,
      "请输入阿里云 OSS Bucket",
    ),
  ],
  uploadOssAccessKeyId: [
    createProviderRule(
      ["aliyun-oss"],
      () => form.uploadOssAccessKeyId,
      "请输入阿里云 AccessKey ID",
    ),
  ],
  uploadOssAccessKeySecret: [
    createProviderRule(
      ["aliyun-oss"],
      () => form.uploadOssAccessKeySecret,
      "请输入阿里云 AccessKey Secret",
    ),
  ],
};

async function loadData() {
  Object.assign(form, await systemConfigApi.detail());
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  await systemConfigApi.update(form);
  authStore.setSiteConfig(await systemConfigApi.publicInfo());
  ElMessage.success("配置已保存");
}

onMounted(loadData);
</script>

<style scoped>
.config-page {
  min-height: 100%;
}

.config-shell {
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

.config-tabs {
  margin-top: 22px;
}

.setting-group {
  padding-top: 8px;
}

.group-title {
  font-size: 18px;
  font-weight: 800;
}

.group-tip {
  margin-top: 6px;
  margin-bottom: 18px;
  color: var(--muted);
  font-size: 13px;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.basic-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 0.9fr);
  gap: 16px;
  align-items: stretch;
}

.basic-main-card,
.basic-side-card {
  border-radius: 24px;
  border: 1px solid var(--border);
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.98),
    rgba(246, 249, 255, 0.96)
  );
}

.basic-main-card {
  padding: 22px;
}

.basic-side-card {
  padding: 22px;
  background:
    radial-gradient(
      circle at top right,
      rgba(37, 99, 235, 0.14),
      transparent 36%
    ),
    linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.98),
      rgba(242, 247, 255, 0.98)
    );
}

.basic-card-head {
  margin-bottom: 16px;
}

.basic-card-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--text);
}

.basic-card-tip,
.basic-preview-tip {
  margin-top: 6px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.basic-form-stack {
  display: grid;
  gap: 14px;
}

.basic-field {
  height: 100%;
}

.basic-field.compact {
  margin-top: 18px;
}

.basic-preview-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 88px;
  height: 88px;
  padding: 0 20px;
  border-radius: 24px;
  font-size: 30px;
  font-weight: 900;
  letter-spacing: 0.08em;
  color: #0f172a;
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.96),
    rgba(219, 234, 254, 0.92)
  );
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.basic-preview-title {
  margin-top: 18px;
  font-size: 20px;
  font-weight: 800;
  color: var(--text);
}

.field-card {
  margin-bottom: 0;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.98),
    rgba(247, 250, 255, 0.98)
  );
}

.field-card.full {
  grid-column: 1 / -1;
}

.field-card :deep(.el-form-item__content) {
  display: block;
}

.field-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--text);
}

.provider-form-item {
  margin-bottom: 0;
}

.provider-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.provider-grid :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 12px 16px;
  border-radius: 16px;
  border: 1px solid var(--border);
  box-shadow: none;
}

.provider-grid :deep(.el-radio-button:first-child .el-radio-button__inner),
.provider-grid :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 16px;
}

.provider-panel {
  margin-top: 18px;
}

.provider-intro {
  margin-bottom: 16px;
  padding: 18px 20px;
  border-radius: 18px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  background: linear-gradient(
    135deg,
    rgba(37, 99, 235, 0.08),
    rgba(14, 165, 233, 0.04)
  );
}

.provider-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  color: var(--primary);
  background: rgba(255, 255, 255, 0.86);
}

.provider-intro h3 {
  margin: 12px 0 6px;
  font-size: 20px;
}

.provider-intro p {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

@media (max-width: 768px) {
  .shell-header {
    flex-direction: column;
    align-items: stretch;
  }

  .basic-grid {
    grid-template-columns: 1fr;
  }

  .field-grid {
    grid-template-columns: 1fr;
  }

  .field-card.full {
    grid-column: auto;
  }

  .provider-grid {
    grid-template-columns: 1fr;
  }
}
</style>
