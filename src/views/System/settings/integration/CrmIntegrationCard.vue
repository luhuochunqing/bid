<template>
  <div class="crm-card">
    <div class="card-toolbar">
      <div>
        <p class="card-kicker">CRM</p>
        <h3 class="card-title">CRM 系统</h3>
      </div>
      <el-button :loading="saving" type="primary" @click="handleSave">保存配置</el-button>
    </div>

    <el-form v-loading="loading" label-position="top" class="crm-form">
      <el-form-item label="CRM 地址" required>
        <el-input v-model="form.baseUrl" placeholder="例如：https://crm.example.com" clearable />
      </el-form-item>
      <el-form-item label="认证 Token">
        <el-input v-model="form.authToken" type="password" placeholder="请输入接口认证 Token" show-password clearable />
      </el-form-item>
      <el-form-item label="客户端 ID">
        <el-input v-model="form.clientId" placeholder="请输入客户端 ID" clearable />
      </el-form-item>
      <div class="card-actions">
        <el-button :loading="testing" @click="handleTest">测试连接</el-button>
        <span v-if="testResult" class="test-result" :class="testResult.ok ? 'success' : 'error'">
          {{ testResult.ok ? '连接成功' : '连接失败: ' + (testResult.message || '') }}
        </span>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)

const form = reactive({ baseUrl: '', authToken: '', clientId: '' })

const handleSave = async () => {
  saving.value = true
  try {
    const token = sessionStorage.getItem('token')
    const res = await fetch('/api/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ integrationConfig: { crmBaseUrl: form.baseUrl, crmClientId: form.clientId, crmAuthToken: form.authToken } })
    })
    const data = await res.json()
    if (data.success) ElMessage.success('配置保存成功')
    else ElMessage.error(data.msg || '保存失败')
  } catch { ElMessage.error('保存失败') }
  finally { saving.value = false }
}

const handleTest = async () => {
  testing.value = true; testResult.value = null
  try {
    const token = sessionStorage.getItem('token')
    const res = await fetch('/api/xiyu/crm/customers?keyword=test&pageSize=1', {
      headers: { Authorization: `Bearer ${token}` }
    })
    testResult.value = res.ok ? { ok: true } : { ok: false, message: `HTTP ${res.status}` }
  } catch (e) { testResult.value = { ok: false, message: e.message } }
  finally { testing.value = false }
}
</script>

<style scoped>
.crm-card { background: var(--bg-card); border: 1px solid var(--gray-200); border-radius: 8px; padding: 20px; }
.card-toolbar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.card-kicker { margin: 0 0 4px; font-size: 12px; font-weight: 700; color: #6d7d5d; text-transform: uppercase; letter-spacing: 0.08em; }
.card-title { margin: 0; font-size: 18px; font-weight: 600; color: #1f2d1d; }
.crm-form { max-width: 520px; }
.card-actions { display: flex; align-items: center; gap: 12px; margin-top: 8px; }
.test-result { font-size: 13px; }
.test-result.success { color: var(--el-color-success); }
.test-result.error { color: var(--el-color-danger); }
</style>
