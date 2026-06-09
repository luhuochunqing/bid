<!-- Input: WeCom + OA + organization integration cards，系统集成独立页面 -->
<!-- Output: 企业微信、泛微 OA 配置入口与组织架构运维入口 -->
<!-- Pos: src/views/System/ -->

<template>
  <div class="system-integration-panel">
    <div class="panel-toolbar">
      <div>
        <p class="panel-kicker">System Integration</p>
        <h2>系统集成</h2>
      </div>
    </div>

    <div class="integration-stack">
      <WeComIntegrationCard
        v-model:form="form"
        :secret-configured="secretConfigured"
        :test-result="testResult"
        :loading="loading"
        :saving="saving"
        :testing="testing"
        @save="save"
        @test-conn="testConn"
      />

      <WeaverIntegrationCard
        v-model:form="oaForm"
        :secret-configured="oaSecretConfigured"
        :loading="oaLoading"
        :saving="oaSaving"
        @save="saveOaConfig"
      />

      <CrmIntegrationCard />
      <OrganizationIntegrationCard />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useWeComSettings } from './settings/useWeComSettings.js'
import WeComIntegrationCard from './settings/integration/WeComIntegrationCard.vue'
import WeaverIntegrationCard from './settings/integration/WeaverIntegrationCard.vue'
import CrmIntegrationCard from './settings/integration/CrmIntegrationCard.vue'
import OrganizationIntegrationCard from './settings/integration/OrganizationIntegrationCard.vue'
import { useSystemIntegrationSettings } from './settings/useSystemIntegrationSettings.js'

const {
  loading,
  saving,
  testing,
  form,
  secretConfigured,
  testResult,
  load,
  save,
  testConn,
} = useWeComSettings()

const {
  loading: oaLoading,
  saving: oaSaving,
  form: oaForm,
  secretConfigured: oaSecretConfigured,
  load: loadSystemIntegration,
  save: saveOaConfig,
} = useSystemIntegrationSettings()

onMounted(() => {
  load()
  loadSystemIntegration()
})
</script>

<style scoped>
.system-integration-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
}

.panel-toolbar {
  padding: 6px 2px 10px;
  border-bottom: 1px solid rgba(67, 89, 55, 0.1);
}

.panel-kicker {
  margin: 0 0 6px;
  color: #6d7d5d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.panel-toolbar h2 {
  margin: 0;
  color: #1f2d1d;
}

.integration-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
