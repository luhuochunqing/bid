<template>
  <el-card class="tender-evaluation-form" shadow="never">
    <template #header>
      <div class="evaluation-card-header">
        <h3 class="evaluation-title">项目评估表</h3>
        <div class="evaluation-meta">
          <el-tag v-if="adaptiveForm.isDynamic?.value" type="success" size="small">动态表单</el-tag>
          <span v-if="adaptiveForm.loading?.value" class="meta-loading">
            <el-icon class="is-loading" :size="12"><Loading /></el-icon>
          </span>
          <span v-else class="evaluation-hint">共 3 段，请按顺序填写</span>
        </div>
      </div>
    </template>

    <!-- Adaptive form wrapper -->
    <AdaptiveFormPage
      ref="adaptiveForm"
      scope="tender.evaluation"
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="handleUpdate"
      @submit="handleSubmit"
    >
      <!-- Fallback: original form when dynamic schema unavailable -->
      <template #fallback-form>
        <el-collapse v-model="activeSection" class="evaluation-collapse">
          <!-- Section 1: 基础信息 -->
          <el-collapse-item title="一、基础信息" name="basic">
            <el-form :model="modelValue.basic" label-width="180px" :disabled="disabled">
              <el-form-item label="入围家数" prop="shortlistedCount">
                <el-input-number v-model="modelValue.basic.shortlistedCount" :min="1" :precision="0" />
              </el-form-item>
              <el-form-item label="年度电商采购金额（万元）" prop="annualProcurementAmount">
                <el-input-number v-model="modelValue.basic.annualProcurementAmount" :min="0" :precision="2" />
              </el-form-item>
              <el-form-item label="招标文件不利项" prop="adverseItems">
                <el-input v-model="modelValue.basic.adverseItems" type="textarea" :rows="3" placeholder="请填写招标文件中的不利项" maxlength="5000" />
              </el-form-item>
              <el-form-item label="风险预判举例" prop="riskAssessment">
                <el-input v-model="modelValue.basic.riskAssessment" type="textarea" :rows="3" placeholder="请填写风险预判" maxlength="5000" />
              </el-form-item>
              <el-form-item label="风险兜底方案" prop="riskMitigationPlan">
                <el-input v-model="modelValue.basic.riskMitigationPlan" type="textarea" :rows="3" placeholder="请填写风险兜底方案（可选）" maxlength="5000" />
              </el-form-item>
              <el-form-item label="项目经理是否了解评标全流程" prop="pmUnderstandsProcess">
                <el-input v-model="modelValue.basic.pmUnderstandsProcess" type="textarea" :rows="2" placeholder="请填写（可选）" maxlength="2000" />
              </el-form-item>
              <el-form-item label="需要的支持及关键信息备注" prop="supportNeeded">
                <el-input v-model="modelValue.basic.supportNeeded" type="textarea" :rows="3" placeholder="请填写需要的支持及备注（可选）" maxlength="5000" />
              </el-form-item>
              <el-form-item label="项目计划 GAP" prop="projectPlanGap">
                <AdaptiveGapUpload
                  v-model="modelValue.basic"
                  :tender-id="tenderId"
                />
              </el-form-item>
              <el-form-item label="客户营收（亿元）" prop="customerRevenue">
                <el-input-number v-model="modelValue.basic.customerRevenue" :min="0" :precision="2" />
              </el-form-item>
            </el-form>
          </el-collapse-item>

          <!-- Section 2: 客户信息矩阵 -->
          <el-collapse-item title="二、客户信息" name="customerInfo">
            <CustomerInfoMatrix
              v-if="modelValue.customerInfo"
              v-model="modelValue.customerInfo"
              :disabled="disabled"
            />
          </el-collapse-item>

          <!-- Section 3: 投标负责人建议 -->
          <el-collapse-item title="三、投标负责人建议" name="recommendation">
            <el-form :model="modelValue.recommendation" label-width="140px" :disabled="disabled">
              <el-form-item label="是否投标" prop="shouldBid">
                <el-select v-model="modelValue.recommendation.shouldBid" placeholder="请选择" clearable style="width: 200px" @change="handleShouldBidChange">
                  <el-option label="投标" :value="true" />
                  <el-option label="不投标" :value="false" />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="modelValue.recommendation.shouldBid === false"
                label="理由"
                prop="reason"
                required
              >
                <el-input
                  v-model="modelValue.recommendation.reason"
                  type="textarea"
                  :rows="4"
                  placeholder="请填写理由（选择不投标时必填）"
                  maxlength="5000"
                />
              </el-form-item>
            </el-form>
          </el-collapse-item>
        </el-collapse>
      </template>
    </AdaptiveFormPage>

    <div class="evaluation-actions">
      <template v-if="showDraftSubmitButtons">
        <el-button :disabled="disabled" @click="$emit('save-draft')">保存草稿</el-button>
        <el-button type="primary" :disabled="disabled" @click="handleSubmitAction">提交</el-button>
      </template>
      <template v-else-if="showDecisionButtons">
        <el-button type="primary" :disabled="disabled" @click="$emit('bid')">投标</el-button>
        <el-button type="danger" :disabled="disabled" @click="$emit('abandon')">弃标</el-button>
      </template>
    </div>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import AdaptiveFormPage from '@/components/common/AdaptiveFormPage.vue'
import CustomerInfoMatrix from './components/CustomerInfoMatrix.vue'
import AdaptiveGapUpload from './components/AdaptiveGapUpload.vue'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  disabled: { type: Boolean, default: false },
  tenderId: { type: Number, default: null },
  showDraftSubmitButtons: { type: Boolean, default: false },
  showDecisionButtons: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:modelValue',
  'submit',
  'save-draft',
  'bid',
  'abandon',
])

const activeSection = ref('basic')
const adaptiveForm = ref(null)

function handleUpdate(value) {
  emit('update:modelValue', value)
}

function handleShouldBidChange(value) {
  // 切换到"投标"时清空理由
  if (value === true && props.modelValue?.recommendation) {
    props.modelValue.recommendation.reason = ''
  }
}

function handleSubmit(formData) {
  emit('update:modelValue', formData)
  emit('submit', formData)
}

async function handleSubmitAction() {
  if (adaptiveForm.value?.isDynamic?.value) {
    const result = await adaptiveForm.value.submit()
    if (result?.valid === false) return
    return
  }
  emit('submit', props.modelValue)
}

defineExpose({
  validate: () => adaptiveForm.value?.validate?.() ?? Promise.resolve(''),
  adaptiveForm,
  activeSection,
})
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

.evaluation-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-loading {
  display: flex;
  align-items: center;
  color: var(--el-text-color-secondary);
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
</style>
