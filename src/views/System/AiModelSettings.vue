<!-- Input: AI model and rule configuration -->
<!-- Output: AI模型与规则配置页面 -->
<!-- Pos: src/views/System/ -->

<template>
  <div class="ai-model-settings-page">
    <div class="page-toolbar">
      <div>
        <p class="page-kicker">AI Configuration</p>
        <h2>AI能力模型</h2>
      </div>
    </div>
    <AiModelSettingsPanel
      :loading="aiLoading"
      :saving="aiSaving"
      :testing-provider="testingProvider"
      v-model:system-config="systemConfig"
      v-model:ai-model-config="aiModelConfig"
      :save="saveAiSettings"
      :test-provider="testProvider"
    />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import AiModelSettingsPanel from './settings/AiModelSettingsPanel.vue'
import { useAiModelSettings } from './settings/useAiModelSettings'

const {
  loading: aiLoading,
  saving: aiSaving,
  testingProvider,
  systemConfig,
  aiModelConfig,
  load,
  save: saveAiSettings,
  testProvider
} = useAiModelSettings()

onMounted(() => { load() })
</script>

<style scoped>
.ai-model-settings-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
}

.page-toolbar {
  padding: 6px 2px 10px;
  border-bottom: 1px solid rgba(67, 89, 55, 0.1);
}

.page-kicker {
  margin: 0 0 6px;
  color: #6d7d5d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-toolbar h2 {
  margin: 0;
  color: #1f2d1d;
}
</style>
