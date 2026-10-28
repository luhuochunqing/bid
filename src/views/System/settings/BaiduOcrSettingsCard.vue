<template>
  <div class="ocr-section">
    <div class="ocr-card">
      <div class="provider-head">
        <div>
          <p class="provider-code">TABLE OCR ENGINE</p>
          <h3>百度智能云表格识别 (Baidu Table OCR)</h3>
          <p class="ocr-subdesc">用于招标文件跨页长表、带全页边框等复杂版面的高精度结构化抽取与旁路对照。</p>
        </div>
        <el-switch
          v-model="modelValue.enabled"
          active-text="引擎已启用"
          inactive-text="引擎已停用"
        />
      </div>

      <el-form label-position="top" class="provider-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="API Key (Client ID)">
              <el-input
                v-model="modelValue.apiKey"
                type="password"
                show-password
                :placeholder="modelValue.apiKeyConfigured ? `已配置：${modelValue.apiKeyMasked}` : '请输入百度智能云 API Key'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Secret Key (Client Secret)">
              <el-input
                v-model="modelValue.secretKey"
                type="password"
                show-password
                :placeholder="modelValue.secretKeyConfigured ? `已配置：${modelValue.secretKeyMasked}` : '请输入百度智能云 Secret Key'"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="provider-status">
        <el-tag :type="modelValue.apiKeyConfigured && modelValue.secretKeyConfigured ? 'success' : 'info'" effect="plain">
          {{ modelValue.apiKeyConfigured && modelValue.secretKeyConfigured ? '凭据已配置' : '未完整配置凭据' }}
        </el-tag>
        <el-tag
          v-if="modelValue.lastTestStatus"
          :type="modelValue.lastTestStatus === 'success' ? 'success' : 'danger'"
          effect="plain"
        >
          {{ modelValue.lastTestStatus === 'success' ? '连接正常' : '连接失败' }}
        </el-tag>
      </div>

      <p v-if="modelValue.lastTestMessage" class="test-message">
        {{ modelValue.lastTestMessage }}
      </p>

      <div class="provider-actions">
        <el-button
          :loading="testing"
          @click="$emit('test')"
        >
          <el-icon><Connection /></el-icon>
          测试连通性
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Connection } from '@element-plus/icons-vue'

const modelValue = defineModel({ type: Object, required: true })
defineProps({
  testing: { type: Boolean, default: false },
})
defineEmits(['test'])
</script>

<style scoped>
.ocr-section {
  margin-top: 8px;
}

.ocr-card {
  padding: 20px;
  background: #ffffff;
  border: 1px solid #e1e7dc;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: all 0.2s ease;
}

.ocr-card:hover {
  border-color: #435937;
  box-shadow: 0 4px 16px rgba(67, 89, 55, 0.08);
}

.provider-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.provider-code {
  margin: 0 0 6px;
  color: #6d7d5d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.ocr-card h3 {
  margin: 0;
  color: #1f2d1d;
}

.ocr-subdesc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6d7d5d;
}

.provider-form {
  margin-top: 4px;
}

.provider-status,
.provider-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.provider-actions {
  justify-content: flex-end;
  margin-top: 4px;
}

.test-message {
  margin: 0;
  color: #55624c;
  font-size: 13px;
  line-height: 1.5;
}
</style>
