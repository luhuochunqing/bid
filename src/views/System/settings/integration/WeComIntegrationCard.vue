<!-- Input: form / secretConfigured / testResult / testSendResult / loading / saving / testing / sending props; emits save / test-conn / send-test -->
<!-- Output: 企业微信 configuration card with form fields, switches, notifyUserIds, and action buttons -->
<!-- Pos: src/views/System/settings/integration/ -->
<!-- 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->

<template>
  <div class="wecom-card">
    <div class="card-toolbar">
      <div>
        <p class="card-kicker">Enterprise WeChat</p>
        <h3 class="card-title">企业微信</h3>
      </div>
      <div class="toolbar-actions">
        <el-button
          :loading="testing"
          :disabled="!secretConfigured || loading || testing"
          @click="$emit('test-conn')"
        >测试连接</el-button>
        <el-button
          :loading="sending"
          :disabled="!secretConfigured || sending"
          @click="$emit('send-test')"
        >发送测试消息</el-button>
        <el-button :loading="saving" type="primary" @click="$emit('save')">保存配置</el-button>
      </div>
    </div>

    <div v-if="testResult" class="test-result-row">
      <el-tag
        :type="testResult.success ? 'success' : 'danger'"
        effect="plain"
        size="small"
      >
        {{ testResult.success ? '连接成功' : '连接失败' }}
      </el-tag>
      <span class="test-message">{{ testResult.message }}</span>
      <span v-if="testResult.probedAt" class="test-time">{{ formatProbedAt(testResult.probedAt) }}</span>
    </div>

    <div v-if="testSendResult" class="test-send-result-row">
      <el-tag
        :type="testSendResult.success ? 'success' : 'danger'"
        effect="plain"
        size="small"
      >
        {{ testSendResult.success ? '发送成功' : '发送失败' }}
      </el-tag>
      <span v-if="testSendResult.success" class="test-message">
        已发送至 {{ testSendResult.sentTo }}
        <span v-if="testSendResult.sentAt" class="test-time">{{ formatProbedAt(testSendResult.sentAt) }}</span>
      </span>
      <span v-else class="test-message">
        {{ testSendResult.errmsg }}
        <span v-if="testSendResult.errcode" class="test-time">错误码 {{ testSendResult.errcode }}</span>
      </span>
    </div>

    <el-form
      v-loading="loading"
      label-position="top"
      class="wecom-form"
    >
      <div class="form-grid">
        <el-form-item label="企业 CorpID" required>
          <el-input
            v-model="form.corpId"
            placeholder="请输入企业 CorpID"
            clearable
          />
        </el-form-item>

        <el-form-item label="应用 AgentID" required>
          <el-input
            v-model.number="form.agentId"
            placeholder="请输入应用 AgentID（数字）"
            clearable
          />
        </el-form-item>
      </div>

      <el-form-item label="应用 Secret" required>
        <el-input
          v-model="form.corpSecret"
          type="password"
          show-password
          :placeholder="secretConfigured ? '已配置（留空保持不变）' : '请输入应用 Secret'"
          clearable
        />
      </el-form-item>

      <div class="switch-row">
        <div class="switch-item">
          <el-switch v-model="form.ssoEnabled" />
          <span class="switch-label">启用单点登录 (SSO)</span>
        </div>
        <div class="switch-item">
          <el-switch v-model="form.messageEnabled" />
          <span class="switch-label">启用应用消息推送</span>
        </div>
      </div>

      <el-form-item label="通知接收人">
        <el-input
          v-model="form.notifyUserIds"
          type="textarea"
          :rows="2"
          placeholder="企业微信 UserID，多个用半角逗号或换行分隔"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
defineModel('form', { type: Object, required: true })
defineProps({
  secretConfigured: { type: Boolean, default: false },
  testResult: { type: Object, default: null },
  testSendResult: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  testing: { type: Boolean, default: false },
  sending: { type: Boolean, default: false },
})

defineEmits(['save', 'test-conn', 'send-test'])

const formatProbedAt = (probedAt) => new Date(probedAt).toLocaleString('zh-CN')
</script>

<style scoped>
.wecom-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 24px;
  border: 1px solid rgba(67, 89, 55, 0.14);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
}

.card-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(67, 89, 55, 0.1);
}

.toolbar-actions {
  display: flex;
  gap: 10px;
}

.card-kicker {
  margin: 0 0 4px;
  color: #6d7d5d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.card-title {
  margin: 0;
  color: #1f2d1d;
  font-size: 18px;
}

.test-result-row,
.test-send-result-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.03);
}

.test-message {
  color: #52624c;
  font-size: 14px;
}

.test-time {
  margin-left: auto;
  color: var(--text-tertiary);
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.switch-row {
  display: flex;
  gap: 32px;
}

.switch-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.switch-label {
  color: #52624c;
  font-size: 14px;
}

@media (max-width: 768px) {
  .card-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .switch-row {
    flex-direction: column;
    gap: 14px;
  }
}
</style>
