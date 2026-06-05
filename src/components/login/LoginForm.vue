<template>
  <el-form
    ref="loginFormRef"
    :model="loginForm"
    :rules="loginRules"
    class="login-form"
    @submit.prevent="handleLogin"
  >
    <el-form-item prop="username">
      <label class="form-label">用户名</label>
      <el-input
        v-model="loginForm.username"
        placeholder="请输入用户名"
        size="large"
        :prefix-icon="User"
        class="form-input"
      />
    </el-form-item>

    <el-form-item prop="password">
      <label class="form-label">密码</label>
      <el-input
        v-model="loginForm.password"
        type="password"
        placeholder="请输入密码"
        size="large"
        :prefix-icon="Lock"
        show-password
        class="form-input"
        @keyup.enter="handleLogin"
      />
    </el-form-item>

    <el-form-item>
      <div class="form-actions">
        <el-checkbox v-model="loginForm.remember" class="login-checkbox">
          记住我
        </el-checkbox>
        <a href="#" class="forgot-link">忘记密码？</a>
      </div>
    </el-form-item>

    <el-form-item>
      <el-button
        type="primary"
        size="large"
        :loading="loading"
        class="login-button"
        @click="handleLogin"
      >
        {{ loading ? '登录中...' : '登录' }}
      </el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  remember: false
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 3, message: '密码长度不能少于3位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()
    loading.value = true

    await userStore.login(loginForm.username, loginForm.password, loginForm.remember)

    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    if (error !== false) {
      ElMessage.error(error?.message || '登录失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 表单样式 */
.login-form {
  margin-bottom: 24px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--sidebar-text);
  margin-bottom: 8px;
}

.form-input :deep(.el-input__wrapper) {
  padding: 10px 16px;
  border-radius: 8px;
  border: 1px solid var(--gray-200);
  box-shadow: none;
  transition: all 0.2s ease;
}

.form-input :deep(.el-input__wrapper:hover) {
  border-color: #cbd5e1;
}

.form-input :deep(.el-input__inner) {
  font-size: 14px;
  color: #0F172A;
  outline: none;
  box-shadow: none;
}

.form-input :deep(.el-input__inner::placeholder) {
  color: var(--gray-400);
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.login-checkbox {
  color: var(--text-slate);
  font-size: 13px;
}

.forgot-link {
  color: var(--accent-blue);
  font-size: 13px;
  text-decoration: none;
  transition: color 0.2s ease;
}

.forgot-link:hover {
  color: var(--brand-xiyu-logo-hover);
}

.login-button {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--accent-blue) 0%, var(--brand-xiyu-logo-hover) 100%);
  border: none;
  transition: all 0.3s ease;
}

.login-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(3, 105, 161, 0.25);
}
</style>
