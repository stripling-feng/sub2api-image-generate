<template>
  <div class="login-page">
    <div class="login-shell">
      <section class="login-panel">
        <div class="panel-brand">
          <div class="brand-mark">{{ publicConfig.siteName.slice(0, 2).toUpperCase() }}</div>
          <div class="brand-copy">
            <strong>{{ publicConfig.siteName }}</strong>
            <span>管理后台登录</span>
          </div>
        </div>

        <div class="headline">
          <h2>欢迎登录</h2>
        </div>

        <el-form :model="form" class="login-form" @submit.prevent="handleLogin">
          <el-form-item>
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入密码"
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-button"
            :loading="submitting"
            @click="handleLogin"
          >
            登录系统
          </el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import { systemConfigApi } from '../../api/system'

const router = useRouter()
const authStore = useAuthStore()
const submitting = ref(false)
const form = reactive({
  username: '',
  password: '',
})

const publicConfig = reactive({
  siteName: 'Feng AI Admin',
  copyright: '© 2026 Feng AI Admin. All rights reserved.',
})

async function loadPublicConfig() {
  Object.assign(publicConfig, await systemConfigApi.publicInfo())
  document.title = publicConfig.siteName
}

async function handleLogin() {
  if (submitting.value) return

  submitting.value = true
  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } finally {
    submitting.value = false
  }
}

onMounted(loadPublicConfig)
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 28px;
  background:
    radial-gradient(circle at 12% 18%, rgba(37, 99, 235, 0.22), transparent 18%),
    radial-gradient(circle at 88% 28%, rgba(14, 165, 233, 0.18), transparent 18%),
    radial-gradient(circle at 80% 88%, rgba(16, 185, 129, 0.08), transparent 18%),
    linear-gradient(135deg, #08111e 0%, #13263d 42%, #eff4fb 42%, #f8fbff 100%);
}

.login-shell {
  width: min(440px, 100%);
}

.login-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 24px;
  padding: 36px 34px 34px;
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(248, 251, 255, 0.94));
  border: 1px solid rgba(186, 198, 214, 0.42);
  box-shadow:
    0 30px 60px rgba(8, 17, 30, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
}

.panel-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 16px;
  background: linear-gradient(145deg, #1d4ed8, #38bdf8);
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.04em;
  box-shadow: 0 16px 30px rgba(37, 99, 235, 0.28);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.brand-copy strong {
  font-size: 18px;
  color: #0f172a;
}

.brand-copy span {
  color: #64748b;
  font-size: 13px;
  letter-spacing: 0.04em;
}

.headline h2 {
  margin: 0;
  font-size: 30px;
  line-height: 1.1;
  color: #0f172a;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 48px;
  padding: 0 14px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.92);
  box-shadow:
    inset 0 0 0 1px rgba(148, 163, 184, 0.22),
    0 8px 18px rgba(15, 23, 42, 0.04);
  transition:
    box-shadow 0.2s ease,
    background-color 0.2s ease,
    transform 0.2s ease;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow:
    inset 0 0 0 1px rgba(37, 99, 235, 0.5),
    0 0 0 4px rgba(37, 99, 235, 0.12),
    0 10px 24px rgba(37, 99, 235, 0.1);
}

.login-form :deep(.el-input__inner) {
  color: #0f172a;
  font-size: 15px;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: #94a3b8;
}

.login-button {
  width: 100%;
  min-height: 48px;
  margin-top: 6px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8 55%, #0ea5e9);
  box-shadow: 0 18px 34px rgba(37, 99, 235, 0.26);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.login-button:hover,
.login-button:focus-visible {
  transform: translateY(-1px);
  box-shadow: 0 22px 38px rgba(37, 99, 235, 0.3);
}

.login-button:active {
  transform: translateY(0);
}

@media (max-width: 960px) {
  .login-page {
    padding: 16px;
  }

  .login-panel {
    padding: 30px 22px 24px;
  }

  .headline h2 {
    font-size: 28px;
  }
}
</style>
