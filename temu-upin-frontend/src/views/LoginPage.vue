<template>
  <div class="login-page">
    <div class="login-backdrop"></div>
    <div class="login-shell">
      <div class="login-copy">
        <div class="eyebrow">TEMU UPIN</div>
        <h1>平台后台登录</h1>
        <p>登录后进入平台后台，统一处理商品采集、同步和发布相关业务。</p>
      </div>

      <a-card class="login-card" :bordered="false">
        <div class="login-card-head">
          <div class="login-title">欢迎回来</div>
          <div class="login-subtitle">登录后直接进入商品库页面</div>
        </div>

        <a-form layout="vertical" :model="form" @submit.prevent="handleSubmit">
          <a-form-item label="账号">
            <a-input
              v-model:value="form.username"
              size="large"
              placeholder="请输入账号"
              allow-clear
              @pressEnter="handleSubmit"
            />
          </a-form-item>

          <a-form-item label="密码">
            <a-input-password
              v-model:value="form.password"
              size="large"
              placeholder="请输入密码"
              @pressEnter="handleSubmit"
            />
          </a-form-item>

          <a-button type="primary" size="large" block :loading="submitting" @click="handleSubmit">
            登录并进入商品库
          </a-button>
        </a-form>
      </a-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { authApi } from '@/platform/api/auth'
import { hasPlatformAuth, savePlatformAuth } from '@/utils/platformAuth'

const router = useRouter()

const form = reactive({
  username: 'admin',
  password: 'admin778899'
})

const submitting = ref(false)

if (hasPlatformAuth()) {
  router.replace('/platform/product-collections')
}

async function handleSubmit() {
  if (submitting.value) return

  submitting.value = true
  try {
    const res = await authApi.login({
      username: form.username?.trim(),
      password: form.password
    })
    if (!res?.success || !res?.data?.accessToken) {
      message.error(res?.message || '登录失败')
      return
    }
    savePlatformAuth(res.data)
    message.success('登录成功')
    router.replace('/platform/product-collections')
  } catch (error) {
    message.error(error.message || '登录失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.2), transparent 32%),
    radial-gradient(circle at bottom right, rgba(34, 197, 94, 0.18), transparent 30%),
    linear-gradient(135deg, #f3f7fb 0%, #edf6f1 45%, #f8fafc 100%);
}

.login-backdrop {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.08) 1px, transparent 1px);
  background-size: 34px 34px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.95), rgba(0, 0, 0, 0.65));
}

.login-shell {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(320px, 520px) minmax(320px, 420px);
  align-items: center;
  justify-content: center;
  gap: 48px;
  padding: 32px;
}

.login-copy h1 {
  margin: 12px 0 14px;
  font-size: clamp(36px, 5vw, 54px);
  line-height: 1.02;
  color: #0f172a;
  letter-spacing: -0.04em;
}

.login-copy p {
  max-width: 420px;
  font-size: 16px;
  line-height: 1.8;
  color: #475569;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: #0f172a;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.login-card {
  border-radius: 28px;
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.14);
  overflow: hidden;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(18px);
}

.login-card :deep(.ant-card-body) {
  padding: 28px;
}

.login-card-head {
  margin-bottom: 20px;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
}

.login-subtitle {
  margin-top: 6px;
  color: #64748b;
}

.login-card :deep(.ant-form-item-label > label) {
  color: #334155;
  font-weight: 600;
}

.login-card :deep(.ant-input-affix-wrapper),
.login-card :deep(.ant-input) {
  border-radius: 14px;
}

.login-card :deep(.ant-btn-primary) {
  height: 48px;
  border-radius: 14px;
  font-weight: 600;
  background: linear-gradient(135deg, #0284c7 0%, #16a34a 100%);
  border: none;
  box-shadow: 0 14px 30px rgba(2, 132, 199, 0.22);
}

@media (max-width: 960px) {
  .login-shell {
    grid-template-columns: 1fr;
    gap: 24px;
    padding: 20px;
  }

  .login-copy {
    text-align: center;
  }

  .login-copy p {
    margin-left: auto;
    margin-right: auto;
  }
}
</style>
