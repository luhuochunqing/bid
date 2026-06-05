<template>
  <el-dialog
    v-model="visible"
    title="业绩到期提醒配置"
    width="560px"
    :close-on-click-modal="false"
    @open="loadConfig"
  >
    <el-form v-if="config" label-position="top">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #title>
          蓝图表格 §4.5.1.9 差异化提醒窗口：
          央企（CENTRAL_SOE）提前 <strong>180 天</strong>，其他客户类型提前 <strong>90 天</strong>。
          提醒将在每天 09:00 自动扫描并发送站内通知。
        </template>
      </el-alert>

      <el-form-item label="启用状态">
        <el-switch
          v-model="config.enabled"
          active-text="启用提醒"
          inactive-text="停用提醒"
        />
        <div class="config-description">
          关闭后系统将不再自动检测并提醒业绩合同到期。
        </div>
      </el-form-item>

      <el-form-item label="央企客户提醒窗口" style="margin-top: 16px">
        <div class="config-slider-row">
          <el-slider
            v-model="config.alertDaysSoe"
            :min="1"
            :max="365"
            :step="1"
            show-input
            input-size="small"
            :disabled="!config.enabled"
          />
          <span class="config-days-label">{{ config.alertDaysSoe }} 天</span>
        </div>
        <div class="config-description">
          央企客户（CENTRAL_SOE）合同提前此天数触发到期提醒。
        </div>
      </el-form-item>

      <el-form-item label="其他客户提醒窗口" style="margin-top: 16px">
        <div class="config-slider-row">
          <el-slider
            v-model="config.alertDaysDefault"
            :min="1"
            :max="365"
            :step="1"
            show-input
            input-size="small"
            :disabled="!config.enabled"
          />
          <span class="config-days-label">{{ config.alertDaysDefault }} 天</span>
        </div>
        <div class="config-description">
          政府机关/事业单位、地方国企、民企、港澳台/外企等客户类型提前此天数触发到期提醒。
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { performanceAlertConfigApi } from '@/api/modules/performanceAlertConfig.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const saving = ref(false)
const config = reactive({
  alertDaysSoe: 180,
  alertDaysDefault: 90,
  enabled: true
})

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

async function loadConfig() {
  try {
    const response = await performanceAlertConfigApi.getConfig()
    if (response?.data) {
      config.alertDaysSoe = response.data.alertDaysSoe ?? 180
      config.alertDaysDefault = response.data.alertDaysDefault ?? 90
      config.enabled = response.data.enabled ?? true
    }
  } catch (error) {
    console.error('Failed to load performance alert config:', error)
    ElMessage.error('加载提醒配置失败')
  }
}

async function handleSave() {
  saving.value = true
  try {
    const response = await performanceAlertConfigApi.updateConfig(
      config.alertDaysSoe,
      config.alertDaysDefault,
      config.enabled
    )
    if (response?.success !== false) {
      ElMessage.success('提醒配置已保存')
      visible.value = false
    } else {
      ElMessage.error(response?.msg || '保存失败')
    }
  } catch (error) {
    console.error('Failed to save performance alert config:', error)
    ElMessage.error('保存提醒配置失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.config-slider-row {
  display: flex;
  align-items: center;
  gap: 12px;

  :deep(.el-slider) {
    flex: 1;
  }
}

.config-days-label {
  white-space: nowrap;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  min-width: 48px;
}

.config-description {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  line-height: 1.4;
}
</style>
