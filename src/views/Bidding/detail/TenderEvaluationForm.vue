<template>
  <el-card class="tender-evaluation-form" shadow="never">
    <template #header>
      <div class="evaluation-card-header">
        <h3 class="evaluation-title">项目评估表</h3>
        <span class="evaluation-hint">共 3 段，请按顺序填写</span>
      </div>
    </template>

    <!-- Section 1: 基础信息 -->
    <el-collapse v-model="activeSection" class="evaluation-collapse">
      <el-collapse-item title="一、基础信息" name="basic">
        <BasicFieldsSection
          v-model="form.basic"
          :disabled="isReadOnly"
          :errors="basicErrors"
        >
          <template #gap>
            <el-form-item label="项目计划 GAP" prop="projectPlanGap">
              <ProjectPlanGapUpload
                v-model="form.basic"
                :tender-id="tenderId"
                :disabled="isReadOnly"
              />
            </el-form-item>
          </template>
        </BasicFieldsSection>
      </el-collapse-item>

      <!-- Section 2: 客户信息矩阵 -->
      <el-collapse-item title="二、客户信息" name="customerInfo">
        <CustomerInfoMatrix
          v-model="form.customerInfo"
          :disabled="isReadOnly"
        />
      </el-collapse-item>

      <!-- Section 3: 项目负责人建议 -->
      <el-collapse-item title="三、项目负责人建议" name="recommendation">
        <el-form
          :model="form.recommendation"
          label-width="140px"
          :disabled="isRecommendationReadOnly"
        >
          <el-form-item
            label="是否投标"
            prop="shouldBid"
            :error="recommendationErrors.shouldBid"
          >
            <el-select
              v-model="form.recommendation.shouldBid"
              placeholder="请选择"
              clearable
              :disabled="isRecommendationReadOnly"
              style="width: 200px"
            >
              <el-option label="投标" :value="true" />
              <el-option label="不投标" :value="false" />
            </el-select>
          </el-form-item>

          <el-form-item
            v-if="form.recommendation.shouldBid === false"
            label="理由"
            prop="reason"
            :error="recommendationErrors.reason"
            required
          >
            <textarea
              v-if="isRecommendationReadOnly"
              v-autosize
              :value="form.recommendation.reason || '-'"
              readonly
              class="readonly-textarea"
            />
            <el-input
              v-else
              v-model="form.recommendation.reason"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 10 }"
              placeholder="请填写理由（选择不投标时必填）"
              maxlength="5000"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>

  </el-card>
</template>

<script setup>
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useTenderEvaluationForm } from './useTenderEvaluationForm.js'
import BasicFieldsSection from './components/BasicFieldsSection.vue'
import ProjectPlanGapUpload from './components/ProjectPlanGapUpload.vue'
import CustomerInfoMatrix from './components/CustomerInfoMatrix.vue'

const props = defineProps({
  evaluation: { type: Object, default: null },
  canFill: { type: Boolean, default: false },
  canDecide: { type: Boolean, default: false },
  canFillRecommendation: { type: Boolean, default: false },
  tenderId: { type: Number, required: true },
  savingDraft: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  /**
   * 当 true 时，隐藏内部按钮区域（保存草稿/提交/投标/弃标）。
   * 用于 evaluated_tracking 阶段：底部栏已接管提交按钮，内部不再重复展示。
   */
  hideActions: { type: Boolean, default: false },
})

const emit = defineEmits(['submit', 'save-draft', 'bid', 'abandon', 'dirty-changed'])

const {
  form,
  activeSection,
  isReadOnly,
  isRecommendationReadOnly,
  showDraftSubmitButtons,
  showDecisionButtons,
  hasUnsavedChanges,
  handleSubmit,
  handleSaveDraft,
  handleBid,
  handleAbandon,
  validateBasicSection,
  validateRecommendation,
} = useTenderEvaluationForm(props, emit)

// Inline error state for form fields
const basicErrors = computed(() => {
  const err = validateBasicSection(form.basic)
  if (!err) return {}
  if (err.includes('计划入围供应商数量')) return { plannedShortlistedCount: err }
  if (err.includes('电商MRO+办公流水金额')) return { mroOfficeFlowAmount: err }
  if (err.includes('客户营收')) return { customerRevenue: err }
  if (err.includes('不利项')) return { unfavorableItems: err }
  if (err.includes('风险预判')) return { riskAssessment: err }
  return {}
})

const recommendationErrors = computed(() => {
  const err = validateRecommendation(form.recommendation)
  if (!err) return {}
  if (err.includes('是否投标')) return { shouldBid: err }
  if (err.includes('理由')) return { reason: err }
  return {}
})

// ---- unsaved-changes tracking ----
watch(hasUnsavedChanges, (dirty) => {
  emit('dirty-changed', dirty)
})

function onBeforeUnload(e) {
  if (hasUnsavedChanges.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
onUnmounted(() => window.removeEventListener('beforeunload', onBeforeUnload))

defineExpose({ handleSubmit, handleSaveDraft, handleBid, handleAbandon })
</script>

<style scoped>
.tender-evaluation-form {
  margin-top: 16px;
}

.evaluation-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.evaluation-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.evaluation-hint {
  font-size: 12px;
  color: #909399;
}

.evaluation-collapse {
  margin-top: 8px;
}

.evaluation-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.readonly-textarea {
  width: 100%;
  min-height: 72px;
  padding: 5px 11px;
  border: 1px solid var(--gray-100, #E8E8E8);
  border-radius: 6px;
  font-family: inherit;
  font-size: inherit;
  line-height: 1.5;
  color: var(--text-primary-ui, #303133);
  background: var(--bg-subtle, #F5F7FA);
  resize: vertical;
  overflow-y: auto;
}
</style>
