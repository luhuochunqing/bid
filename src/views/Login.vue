<template>
  <div class="login-page">
    <!-- 左侧品牌区域 -->
    <LoginBrandSection />

    <!-- 右侧登录区域 -->
    <div class="login-section">
      <div class="login-container">
        <div class="login-header">
          <h2 class="login-title">欢迎回来</h2>
          <p class="login-subtitle">登录您的账户继续工作</p>
        </div>

        <LoginForm />

        <SocialLogin />

        <LoginDevAccountsHint v-if="LoginDevAccountsHint" />
      </div>
    </div>

    <!-- 全局加载遮罩 -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p>正在处理...</p>
    </div>
  </div>
</template>

<script setup>
import { defineAsyncComponent, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import LoginBrandSection from '@/components/login/LoginBrandSection.vue'
import LoginForm from '@/components/login/LoginForm.vue'
import SocialLogin from '@/components/login/SocialLogin.vue'

const LoginDevAccountsHint = import.meta.env.DEV
  ? defineAsyncComponent(() => import('@/components/common/LoginDevAccountsHint.vue'))
  : null

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

onMounted(async () => {
  const urlParams = new URLSearchParams(window.location.search)
  const ssoToken = urlParams.get('ssoToken')
  const code = urlParams.get('code')
  const state = urlParams.get('state')
  const redirect = urlParams.get('redirect') || '/dashboard'

  if (ssoToken) {
    loading.value = true
    try {
      await userStore.loginByHomeSso(ssoToken)
      ElMessage.success('登录成功')
      router.push(redirect)
    } catch (error) {
      const msg = error?.response?.data?.msg || error?.message || 'SSO 登录失败'
      ElMessage.error(msg)
      router.replace('/login')
    } finally {
      loading.value = false
    }
  } else if (code && state) {
    loading.value = true
    try {
      await userStore.loginByWeCom(code, state)
      ElMessage.success('企业微信登录成功')
      router.push(redirect)
    } catch (error) {
      if (error?.response?.data?.code === 40101) {
        ElMessage.warning('您的企业微信账号尚未绑定系统账号，请先手动登录一次进行绑定')
      } else {
        const msg = error?.response?.data?.msg || error?.message || '企业微信登录失败'
        ElMessage.error(msg)
      }
      router.replace('/login')
    } finally {
      loading.value = false
    }
  }
})
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background: #F8FAFC;
}

.login-section {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: var(--bg-card);
}

.login-container {
  width: 100%;
  max-width: 400px;
}

.login-header {
  margin-bottom: 40px;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: #0F172A;
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--text-slate);
}

.loading-overlay {
  position: fixed;
  inset: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid var(--accent-blue);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@media (max-width: 640px) {
  .login-section {
    padding: 24px;
  }
}
</style>
