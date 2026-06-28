<template>
  <div class="ai-model-panel">
    <div class="ai-toolbar">
      <div>
        <p class="panel-kicker">AI Model Gateway</p>
        <h2>AI 模型配置</h2>
      </div>
      <div class="toolbar-actions">
        <el-switch
          v-model="systemConfig.enableAI"
          active-text="AI 已启用"
          inactive-text="AI 已停用"
        />
        <el-button :loading="saving" type="primary" @click="save">
          <el-icon><Check /></el-icon>
          保存配置
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="!systemConfig.enableAI"
      title="AI 总开关已关闭，业务侧 AI 分析将不会调用真实模型。"
      type="warning"
      show-icon
      :closable="false"
      class="ai-alert"
    />

    <div class="provider-selector">
      <span class="selector-label">当前激活厂商</span>
      <el-radio-group v-model="aiModelConfig.activeProvider">
        <el-radio-button
          v-for="provider in aiModelConfig.providers"
          :key="provider.providerCode"
          :value="provider.providerCode"
        >
          {{ provider.providerName }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading" class="provider-grid">
      <section
        v-for="provider in aiModelConfig.providers"
        :key="provider.providerCode"
        class="provider-card"
        :class="{ active: provider.providerCode === aiModelConfig.activeProvider }"
      >
        <div class="provider-head">
          <div>
            <p class="provider-code">{{ provider.providerCode }}</p>
            <h3>{{ provider.providerName }}</h3>
          </div>
          <el-switch v-model="provider.enabled" />
        </div>

        <el-form label-position="top" class="provider-form">
          <el-form-item label="API 地址">
            <el-input v-model="provider.baseUrl" placeholder="https://.../chat/completions" />
          </el-form-item>
          <el-form-item label="模型名称">
            <el-input v-model="provider.model" placeholder="请输入模型名称" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input
              v-model="provider.apiKeyPlaintext"
              type="password"
              show-password
              :placeholder="provider.apiKeyConfigured ? `已配置：${provider.apiKeyMasked}` : '请输入 API Key'"
            />
          </el-form-item>
        </el-form>

        <div class="provider-status">
          <el-tag :type="provider.apiKeyConfigured ? 'success' : 'info'" effect="plain">
            {{ provider.apiKeyConfigured ? 'Key 已配置' : '未配置 Key' }}
          </el-tag>
          <el-tag
            v-if="provider.lastTestStatus"
            :type="provider.lastTestStatus === 'success' ? 'success' : 'danger'"
            effect="plain"
          >
            {{ provider.lastTestStatus === 'success' ? '连接正常' : '连接失败' }}
          </el-tag>
        </div>

        <p v-if="provider.lastTestMessage" class="test-message">
          {{ provider.lastTestMessage }}
        </p>

        <div class="provider-actions">
          <el-button
            :loading="testingProvider === provider.providerCode"
            @click="testProvider(provider)"
          >
            <el-icon><Connection /></el-icon>
            测试连接
          </el-button>
          <el-button
            v-if="provider.providerCode !== aiModelConfig.activeProvider"
            text
            type="primary"
            @click="aiModelConfig.activeProvider = provider.providerCode"
          >
            设为当前
          </el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { Check, Connection } from '@element-plus/icons-vue'

defineModel('systemConfig', { type: Object, required: true })
defineModel('aiModelConfig', { type: Object, required: true })
defineProps({
  loading: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  testingProvider: { type: String, default: '' },
  save: { type: Function, required: true },
  testProvider: { type: Function, required: true },
})
</script>

<style scoped>
.ai-model-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.ai-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 6px 2px 10px;
  border-bottom: 1px solid rgba(67, 89, 55, 0.1);
}

.panel-kicker,
.provider-code {
  margin: 0 0 6px;
  color: #6d7d5d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.ai-toolbar h2,
.provider-card h3 {
  margin: 0;
  color: #1f2d1d;
}

.toolbar-actions,
.provider-actions,
.provider-status {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ai-alert {
  border-radius: 8px;
}

.provider-selector {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.selector-label {
  color: #52624c;
  font-weight: 700;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.provider-card {
  padding: 18px;
  border: 1px solid rgba(67, 89, 55, 0.14);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
}

.provider-card.active {
  border-color: rgba(68, 107, 73, 0.62);
  box-shadow: 0 12px 28px rgba(48, 64, 37, 0.08);
}

.provider-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.provider-form {
  margin-bottom: 12px;
}

.test-message {
  min-height: 20px;
  margin: 8px 0 0;
  color: #6a5d44;
  font-size: 13px;
  line-height: 1.5;
}

.provider-actions {
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 960px) {
  .provider-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .ai-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
