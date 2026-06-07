<template>
  <el-dialog
    v-model="visible"
    title="资质到期告警配置"
    width="500px"
    :close-on-click-modal="false"
    @open="loadConfig"
  >
    <el-form v-if="config" label-position="top">
      <el-form-item label="提前提醒天数">
        <div class="config-slider-row">
          <el-slider
            v-model="config.alertDays"
            :min="1"
            :max="365"
            :step="1"
            show-input
            input-size="small"
          />
          <span class="config-days-label">{{ config.alertDays }} 天</span>
        </div>
        <div class="config-description">
          当资质证书剩余有效期小于或等于此天数时，系统将自动发出到期提醒。
        </div>
      </el-form-item>

      <el-form-item label="启用状态">
        <el-switch
          v-model="config.enabled"
          active-text="启用"
          inactive-text="停用"
        />
        <div class="config-description" style="margin-top: 4px">
          关闭后系统将不再自动检测并提醒资质到期。
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
import { alertConfigApi } from '@/api/modules/alertConfig.js'

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
  alertDays: 90,
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
    const response = await alertConfigApi.getConfig()
    if (response?.data) {
      config.alertDays = response.data.alertDays ?? 90
      config.enabled = response.data.enabled ?? true
    }
  } catch (error) {
    console.error('Failed to load alert config:', error)
    ElMessage.error('加载告警配置失败')
  }
}

async function handleSave() {
  saving.value = true
  try {
    const response = await alertConfigApi.updateConfig(config.alertDays, config.enabled)
    if (response?.success !== false) {
      ElMessage.success('告警配置已保存')
      visible.value = false
    } else {
      ElMessage.error(response?.msg || '保存失败')
    }
  } catch (error) {
    console.error('Failed to save alert config:', error)
    ElMessage.error('保存告警配置失败')
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
