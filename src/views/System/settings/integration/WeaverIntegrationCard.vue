<!-- Input: integration form model and save handler for OA connector -->
<!-- Output: OA / 审批流配置卡片（OA 开关、OA 地址、回调地址、签名密钥） -->
<!-- Pos: src/views/System/settings/integration/ -->

<template>
  <div class="weaver-card">
    <div class="card-toolbar">
      <div>
        <p class="card-kicker">Workflow OA</p>
        <h3 class="card-title">泛微 OA 连接器</h3>
      </div>
      <el-button :loading="saving" type="primary" @click="$emit('save')">保存配置</el-button>
    </div>

    <el-form v-loading="loading" label-position="top" class="weaver-form">
      <div class="switch-row">
        <el-switch v-model="form.oaEnabled" />
        <span class="switch-label">启用 OA 审批流</span>
      </div>

      <el-form-item label="泛微 OA 地址" required>
        <el-input
          v-model="form.oaUrl"
          placeholder="例如：https://oa.example.com"
          clearable
        />
      </el-form-item>

      <el-form-item label="OA 回调地址" required>
        <el-input
          v-model="form.callbackUrl"
          placeholder="例如：https://api.example.com/api/integrations/oa/weaver/callback"
          clearable
        />
      </el-form-item>

      <el-form-item label="OA 回调密钥">
        <el-input
          v-model="form.apiKey"
          type="password"
          show-password
          :placeholder="secretConfigured ? '已配置（留空保持不变）' : '请输入 OA 回调签名密钥'"
          clearable
        />
      </el-form-item>
    </el-form>

    <div class="help-row">
      <el-tag type="info" effect="plain" size="small">说明</el-tag>
      <span>当前 OA 回调验证从“系统设置 / integrationConfig.apiKey”读取；默认占位密钥会被忽略。</span>
    </div>
  </div>
</template>

<script setup>
const form = defineModel('form', { type: Object, required: true })
defineProps({
  secretConfigured: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
})

defineEmits(['save'])
</script>

<style scoped>
.weaver-card {
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

.switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.switch-label {
  color: #52624c;
  font-size: 14px;
}

.help-row {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #66705f;
  font-size: 13px;
}

@media (max-width: 768px) {
  .card-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
