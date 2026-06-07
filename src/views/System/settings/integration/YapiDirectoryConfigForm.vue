<!-- Input: useOrganizationIntegrationSettings composable -->
<!-- Output: YAPI directory configuration form with connection and SDK event settings -->
<!-- Pos: src/views/System/settings/integration/ -->
<!-- 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->

<template>
  <div v-loading="loading" class="yapi-config-body">
    <div v-if="testResult" class="test-result-row">
      <el-tag
        :type="testResult.success ? 'success' : 'danger'"
        effect="plain"
        size="small"
      >
        {{ testResult.success ? '连接成功' : '连接失败' }}
      </el-tag>
      <span class="test-message">{{ testResult.message }}</span>
    </div>

    <h4 class="section-title">YAPI 连接配置</h4>
    <el-form label-position="top" class="yapi-form">
      <el-form-item label="YAPI Base URL">
        <el-input
          v-model="form.orgDirectoryBaseUrl"
          placeholder="请输入 YAPI 基础地址"
          clearable
        />
      </el-form-item>

      <div class="form-grid">
        <el-form-item label="Auth Client ID">
          <el-input
            v-model="form.orgDirectoryAuthClientId"
            placeholder="请输入客户端 ID"
            clearable
          />
        </el-form-item>
        <el-form-item label="Auth Client Secret">
          <el-input
            v-model="form.orgDirectoryAuthClientSecret"
            type="password"
            show-password
            placeholder="已配置（留空保持不变）"
            clearable
          />
        </el-form-item>
      </div>

      <div class="form-grid">
        <el-form-item label="用户详情路径">
          <el-input
            v-model="form.orgDirectoryUserDetailPath"
            placeholder="/users/{userId}"
            clearable
          />
        </el-form-item>
        <el-form-item label="部门详情路径">
          <el-input
            v-model="form.orgDirectoryDeptDetailPath"
            placeholder="/departments/{deptId}"
            clearable
          />
        </el-form-item>
      </div>

      <div class="form-grid">
        <el-form-item label="用户时间窗路径">
          <el-input
            v-model="form.orgDirectoryUserWindowPath"
            placeholder="可留空"
            clearable
          />
        </el-form-item>
        <el-form-item label="部门时间窗路径">
          <el-input
            v-model="form.orgDirectoryDeptWindowPath"
            placeholder="可留空"
            clearable
          />
        </el-form-item>
      </div>
    </el-form>

    <h4 class="section-title">SDK 事件配置</h4>
    <el-form label-position="top" class="yapi-form">
      <el-form-item>
        <div class="switch-item">
          <el-switch v-model="form.orgEventSdkEnabled" />
          <span class="switch-label">启用 SDK</span>
        </div>
      </el-form-item>

      <div class="form-grid">
        <el-form-item label="Consumer Group">
          <el-input
            v-model="form.orgEventConsumerGroup"
            placeholder="bid-org-consumer-test"
            clearable
          />
        </el-form-item>
        <el-form-item label="SDK 注册地址">
          <el-input
            v-model="form.orgEventServerRegisterUrl"
            placeholder="请输入 SDK 注册地址"
            clearable
          />
        </el-form-item>
      </div>
    </el-form>

    <div class="yapi-actions">
      <el-button
        :loading="testing"
        :disabled="!form.orgDirectoryBaseUrl || loading"
        @click="testConnection"
      >测试 YAPI 连接</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="loading"
        @click="saveSettings"
      >保存配置</el-button>
    </div>
  </div>
</template>

<script setup>
import { useOrganizationIntegrationSettings } from '../useOrganizationIntegrationSettings.js'

const {
  loading,
  saving,
  testing,
  form,
  testResult,
  save: saveSettings,
  testConnection,
} = useOrganizationIntegrationSettings()
</script>

<style scoped>
.yapi-config-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0;
}

.test-result-row {
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

.section-title {
  margin: 0;
  color: #1f2d1d;
  font-size: 14px;
  font-weight: 600;
}

.yapi-form {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
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

.yapi-actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .yapi-actions {
    flex-direction: column;
  }

  .yapi-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
