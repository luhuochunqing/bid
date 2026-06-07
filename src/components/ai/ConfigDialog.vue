<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="1200px"
    :close-on-click-modal="false"
    destroy-on-close
    class="ai-config-dialog"
  >
    <div class="config-container">
      <!-- 左侧 - 快速配置区 -->
      <div class="config-left">
        <div class="section-title">快速配置</div>

        <!-- 赢面计算权重 -->
        <div class="weight-section">
          <h4 class="subsection-title">赢面计算权重</h4>
          <p class="weight-tip">权重总和必须为 100%</p>

          <div class="weight-item">
            <div class="weight-header">
              <span class="weight-label">技术能力</span>
              <span class="weight-value">{{ formWeights.tech }}%</span>
            </div>
            <el-slider
              v-model="formWeights.tech"
              :min="0"
              :max="100"
              :show-tooltip="false"
              @change="validateWeights"
            />
          </div>

          <div class="weight-item">
            <div class="weight-header">
              <span class="weight-label">商务能力</span>
              <span class="weight-value">{{ formWeights.commercial }}%</span>
            </div>
            <el-slider
              v-model="formWeights.commercial"
              :min="0"
              :max="100"
              :show-tooltip="false"
              @change="validateWeights"
            />
          </div>

          <div class="weight-item">
            <div class="weight-header">
              <span class="weight-label">价格优势</span>
              <span class="weight-value">{{ formWeights.price }}%</span>
            </div>
            <el-slider
              v-model="formWeights.price"
              :min="0"
              :max="100"
              :show-tooltip="false"
              @change="validateWeights"
            />
          </div>

          <div class="weight-item">
            <div class="weight-header">
              <span class="weight-label">服务保障</span>
              <span class="weight-value">{{ formWeights.service }}%</span>
            </div>
            <el-slider
              v-model="formWeights.service"
              :min="0"
              :max="100"
              :show-tooltip="false"
              @change="validateWeights"
            />
          </div>

          <div class="weight-summary">
            <span :class="{ 'text-danger': !isWeightsValid, 'text-success': isWeightsValid }">
              当前总和: {{ totalWeights }}%
            </span>
            <span v-if="!isWeightsValid" class="weight-error">
              权重总和必须为 100%
            </span>
          </div>
        </div>

        <!-- 风险等级阈值 -->
        <div class="risk-section">
          <h4 class="subsection-title">风险等级阈值</h4>
          <p class="risk-tip">设置触发风险提醒的判定标准</p>

          <el-radio-group v-model="formData.riskLevel" class="risk-radio-group">
            <el-radio value="high" class="risk-radio risk-high">
              <span class="radio-label">
                <span class="radio-icon">🔴</span>
                高风险
              </span>
              <span class="radio-desc">严格标准，快速发现潜在问题</span>
            </el-radio>
            <el-radio value="medium" class="risk-radio risk-medium">
              <span class="radio-label">
                <span class="radio-icon">🟡</span>
                中风险
              </span>
              <span class="radio-desc">平衡标准，适度关注风险因素</span>
            </el-radio>
            <el-radio value="low" class="risk-radio risk-low">
              <span class="radio-label">
                <span class="radio-icon">🟢</span>
                低风险
              </span>
              <span class="radio-desc">宽松标准，仅关注明显风险</span>
            </el-radio>
          </el-radio-group>
        </div>
      </div>

      <!-- 右侧 - 提示词模板区 -->
      <div class="config-right">
        <div class="section-title">✏️ 提示词模板</div>

        <div class="prompt-section">
          <el-input
            v-model="formData.promptTemplate"
            type="textarea"
            :rows="12"
            placeholder="输入提示词模板，可使用变量..."
            class="prompt-textarea"
          />

          <!-- 可用变量标签 -->
          <div class="variables-section">
            <div class="variables-header">
              <span class="variables-title">可用变量</span>
              <span class="variables-hint">点击标签插入到光标位置</span>
            </div>
            <div class="variables-list">
              <el-tag
                v-for="variable in availableVariables"
                :key="variable.name"
                class="variable-tag"
                @click="insertVariable(variable.name)"
              >
                {{ variable.label }}
              </el-tag>
            </div>
          </div>

          <!-- 提示词预览 -->
          <div class="prompt-preview">
            <div class="preview-header">预览效果</div>
            <div class="preview-content">
              <template v-if="renderedPrompt">
                <span
                  v-for="(segment, index) in parsePrompt(renderedPrompt)"
                  :key="index"
                  :class="{ 'variable-highlight': segment.isVariable }"
                >
                  {{ segment.text }}
                </span>
              </template>
              <span v-else class="preview-empty">暂无内容</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部按钮 -->
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button
          type="info"
          :icon="VideoPlay"
          @click="handleTest"
          :disabled="!isFormValid"
        >
          测试运行
        </el-button>
        <el-button
          type="primary"
          :icon="Check"
          @click="handleSave"
          :disabled="!isFormValid"
        >
          保存
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, VideoPlay } from '@element-plus/icons-vue'

// Props
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  configId: {
    type: String,
    default: ''
  },
  configData: {
    type: Object,
    default: () => ({})
  }
})

// Emits
const emit = defineEmits(['update:modelValue', 'save', 'test'])

// 对话框显示状态
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => {
    emit('update:modelValue', val)
  }
})

// 表单数据
const formData = ref({
  weights: {
    tech: 30,
    commercial: 25,
    price: 25,
    service: 20
  },
  riskLevel: 'medium',
  promptTemplate: '请根据{projectName}的招标要求，结合我司的{companyStrength}，分析本次投标的赢面。'
})

const formWeights = computed({
  get: () => formData.value.weights,
  set: (val) => {
    formData.value.weights = val
  }
})

// 可用变量列表
const availableVariables = [
  { name: 'projectName', label: '{项目名称}' },
  { name: 'bidDeadline', label: '{投标截止日期}' },
  { name: 'budget', label: '{预算金额}' },
  { name: 'companyStrength', label: '{公司优势}' },
  { name: 'competitorInfo', label: '{竞争对手信息}' },
  { name: 'techRequirements', label: '{技术要求}' },
  { name: 'commercialTerms', label: '{商务条款}' },
  { name: 'riskFactors', label: '{风险因素}' },
  { name: 'winProbability', label: '{赢面概率}' },
  { name: 'suggestions', label: '{建议}' }
]

// 对话框标题
const dialogTitle = computed(() => {
  return props.configId ? '编辑 AI 配置' : '新建 AI 配置'
})

// 权重总和
const totalWeights = computed(() => {
  const weights = formData.value.weights
  return weights.tech + weights.commercial + weights.price + weights.service
})

// 权重是否有效
const isWeightsValid = computed(() => {
  return totalWeights.value === 100
})

// 表单是否有效
const isFormValid = computed(() => {
  return isWeightsValid.value && formData.value.promptTemplate.trim().length > 0
})

// 渲染后的提示词（用于预览）
const renderedPrompt = computed(() => {
  return formData.value.promptTemplate
})

// 验证权重
const validateWeights = () => {
  const weights = formData.value.weights
  const sum = weights.tech + weights.commercial + weights.price + weights.service

  if (sum > 100) {
    ElMessage.warning('权重总和不能超过 100%')
  }

  return sum === 100
}

// 解析提示词，识别变量
const parsePrompt = (prompt) => {
  const variablePattern = /\{([^}]+)\}/g
  const segments = []
  let lastIndex = 0
  let match

  while ((match = variablePattern.exec(prompt)) !== null) {
    // 添加变量前的文本
    if (match.index > lastIndex) {
      segments.push({
        text: prompt.slice(lastIndex, match.index),
        isVariable: false
      })
    }
    // 添加变量
    segments.push({
      text: match[0],
      isVariable: true
    })
    lastIndex = variablePattern.lastIndex
  }

  // 添加剩余文本
  if (lastIndex < prompt.length) {
    segments.push({
      text: prompt.slice(lastIndex),
      isVariable: false
    })
  }

  return segments.length > 0 ? segments : [{ text: prompt, isVariable: false }]
}

// 插入变量
const insertVariable = (variableName) => {
  const variableText = `{${variableName}}`
  const textarea = document.querySelector('.prompt-textarea textarea')

  if (textarea) {
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const currentValue = formData.value.promptTemplate

    formData.value.promptTemplate =
      currentValue.slice(0, start) + variableText + currentValue.slice(end)

    // 设置光标位置到插入变量后
    setTimeout(() => {
      textarea.focus()
      textarea.setSelectionRange(start + variableText.length, start + variableText.length)
    }, 0)
  } else {
    // 如果无法获取光标位置，追加到末尾
    formData.value.promptTemplate += variableText
  }
}

// 取消
const handleCancel = () => {
  dialogVisible.value = false
}

// 保存
const handleSave = () => {
  if (!isFormValid.value) {
    ElMessage.error('请完善配置信息')
    return
  }

  emit('save', {
    id: props.configId,
    config: {
      weights: formData.value.weights,
      riskLevel: formData.value.riskLevel,
      promptTemplate: formData.value.promptTemplate
    }
  })
}

// 测试运行
const handleTest = () => {
  if (!isFormValid.value) {
    ElMessage.error('请完善配置信息后再测试')
    return
  }

  emit('test', {
    id: props.configId,
    config: {
      weights: formData.value.weights,
      riskLevel: formData.value.riskLevel,
      promptTemplate: formData.value.promptTemplate
    }
  })
}

// 监听配置数据变化
watch(() => props.configData, (newData) => {
  if (newData && Object.keys(newData).length > 0) {
    // 处理 promptTemplate - 可能是对象或字符串
    let promptText = '请根据{projectName}的招标要求，结合我司的{companyStrength}，分析本次投标的赢面。'
    if (newData.promptTemplate) {
      if (typeof newData.promptTemplate === 'string') {
        promptText = newData.promptTemplate
      } else if (typeof newData.promptTemplate === 'object') {
        // 将对象拼接成字符串
        const pt = newData.promptTemplate
        promptText = `角色：${pt.role || ''}\n\n任务：${pt.task || ''}\n\n输出格式：${pt.outputFormat || ''}`
      }
    }

    formData.value = {
      weights: {
        tech: newData.formConfig?.winRateWeights?.technical ?? 30,
        commercial: newData.formConfig?.winRateWeights?.commercial ?? 30,
        price: newData.formConfig?.winRateWeights?.price ?? 20,
        service: newData.formConfig?.winRateWeights?.service ?? 20
      },
      riskLevel: newData.formConfig?.riskThreshold ?? 'medium',
      promptTemplate: promptText
    }
  }
}, { immediate: true, deep: true })

// 监听对话框打开，重置表单
watch(() => props.modelValue, (val) => {
  if (val && !props.configId) {
    // 新建时重置为默认值
    formData.value = {
      weights: { tech: 30, commercial: 25, price: 25, service: 20 },
      riskLevel: 'medium',
      promptTemplate: '请根据{projectName}的招标要求，结合我司的{companyStrength}，分析本次投标的赢面。'
    }
  }
})
</script>

<style lang="scss" scoped>
.ai-config-dialog {
  :deep(.el-dialog__body) {
    padding: 20px;
  }
}

.config-container {
  display: flex;
  gap: 24px;
  min-height: 500px;
}

.config-left {
  flex: 0 0 400px;
  padding-right: 20px;
  border-right: 1px solid var(--el-border-color-light);
}

.config-right {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 20px;
}

.subsection-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 8px 0;
}

.weight-section,
.risk-section {
  margin-bottom: 24px;
}

.weight-tip,
.risk-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 4px 0 16px 0;
}

.weight-item {
  margin-bottom: 16px;
}

.weight-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.weight-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.weight-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  min-width: 40px;
  text-align: right;
}

.weight-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background-color: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 14px;

  .text-success {
    color: var(--el-color-success);
  }

  .text-danger {
    color: var(--el-color-danger);
  }

  .weight-error {
    font-size: 12px;
    color: var(--el-color-danger);
  }
}

.risk-radio-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.risk-radio {
  display: flex;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  transition: all 0.2s;

  &:hover {
    border-color: var(--el-color-primary);
    background-color: var(--el-fill-color-light);
  }

  &.is-checked {
    border-color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
  }

  :deep(.el-radio__label) {
    display: flex;
    flex-direction: column;
    gap: 4px;
    width: 100%;
  }

  :deep(.el-radio__input) {
    margin-top: 2px;
  }
}

.radio-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.radio-icon {
  font-size: 16px;
}

.radio-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 22px;
}

.prompt-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.prompt-textarea {
  :deep(textarea) {
    font-family: 'Courier New', Consolas, monospace;
    font-size: 13px;
    line-height: 1.6;
  }
}

.variables-section {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 12px;
  background-color: var(--el-fill-color-lighter);
}

.variables-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.variables-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.variables-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.variables-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.variable-tag {
  cursor: pointer;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 12px;
  transition: all 0.2s;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }

  :deep(.el-tag__content) {
    white-space: nowrap;
  }
}

.prompt-preview {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
}

.preview-header {
  padding: 8px 12px;
  background-color: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-light);
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.preview-content {
  padding: 12px;
  min-height: 80px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-word;
}

.variable-highlight {
  background-color: var(--el-fill-color);
  color: var(--el-color-primary);
  padding: 2px 4px;
  border-radius: 4px;
  font-family: 'Courier New', Consolas, monospace;
  font-weight: 500;
}

.preview-empty {
  color: var(--el-text-color-placeholder);
  font-style: italic;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
