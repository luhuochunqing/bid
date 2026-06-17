<template>
  <el-form
    :model="localBasic"
    label-position="top"
    :disabled="disabled"
    class="basic-fields-form"
  >
    <el-form-item
      label="计划入围供应商数量"
      prop="plannedShortlistedCount"
      :error="errors.plannedShortlistedCount"
    >
      <el-input-number
        v-model="localBasic.plannedShortlistedCount"
        :min="1"
        :precision="0"
        :disabled="disabled"
        placeholder="正整数，最多10位"
        class="field-number"
      />
    </el-form-item>

    <el-form-item
      label="电商MRO+办公流水金额（万）"
      prop="mroOfficeFlowAmount"
      :error="errors.mroOfficeFlowAmount"
    >
      <el-input-number
        v-model="localBasic.mroOfficeFlowAmount"
        :min="0"
        :precision="2"
        :disabled="disabled"
        placeholder="单位万，非负数"
        class="field-number"
      />
    </el-form-item>

    <el-form-item
      label="客户营收（亿）"
      prop="customerRevenue"
      :error="errors.customerRevenue"
    >
      <el-input-number
        v-model="localBasic.customerRevenue"
        :min="0"
        :precision="2"
        :disabled="disabled"
        placeholder="单位亿，非负数"
        class="field-number"
      />
    </el-form-item>

    <div class="field-group">
      <el-form-item
        label="招标文件不利项"
        prop="unfavorableItems"
        :error="errors.unfavorableItems"
      >
        <el-input
          v-model="localBasic.unfavorableItems"
          type="textarea"
          :rows="3"
          placeholder="请填写招标文件中的不利项"
          maxlength="5000"
          :disabled="disabled"
        />
      </el-form-item>

      <el-form-item
        label="风险预判"
        prop="riskAssessment"
        :error="errors.riskAssessment"
      >
        <el-input
          v-model="localBasic.riskAssessment"
          type="textarea"
          :rows="3"
          placeholder="请填写风险预判"
          maxlength="5000"
          :disabled="disabled"
        />
      </el-form-item>

      <el-form-item
        label="是否有兜底方案"
        prop="contingencyPlan"
      >
        <el-switch
          :model-value="localBasic.contingencyPlan === '是'"
          @update:model-value="localBasic.contingencyPlan = $event ? '是' : '否'"
          :disabled="disabled"
        />
      </el-form-item>

      <el-form-item
        label="是否了解评标全流程"
        prop="processKnowledge"
      >
        <el-input
          v-model="localBasic.processKnowledge"
          type="textarea"
          :rows="3"
          placeholder="请填写对评标流程的了解程度"
          maxlength="5000"
          :disabled="disabled"
        />
      </el-form-item>

      <el-form-item
        label="需要的支持及备注"
        prop="supportNotes"
      >
        <el-input
          v-model="localBasic.supportNotes"
          type="textarea"
          :rows="3"
          placeholder="请填写需要的支持及备注"
          maxlength="5000"
          :disabled="disabled"
        />
      </el-form-item>
    </div>

    <slot name="gap" />
  </el-form>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Object, required: true },
  disabled: { type: Boolean, default: false },
  errors: {
    type: Object,
    default: () => ({}),
  },
})

const emit = defineEmits(['update:modelValue'])

const localBasic = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
</script>

<style scoped>
.basic-fields-form {
  max-width: 800px;
}

.basic-fields-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.basic-fields-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.6;
  padding-bottom: 6px;
}

.field-number {
  width: 100%;
  max-width: 360px;
}

/* 只读详情态：隐藏加减按钮，避免视觉干扰 */
.field-number.is-disabled :deep(.el-input-number__decrease),
.field-number.is-disabled :deep(.el-input-number__increase) {
  display: none;
}

.field-number.is-disabled :deep(.el-input__wrapper) {
  padding-left: 12px;
  padding-right: 12px;
}

.field-group {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--gray-150, #E8ECF0);
}

.field-group :deep(.el-textarea) {
  width: 100%;
  max-width: 360px;
}

.field-group :deep(.el-textarea__inner) {
  min-height: 72px !important;
}
</style>
