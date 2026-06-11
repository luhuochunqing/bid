<template>
  <el-form
    :model="localBasic"
    label-width="220px"
    :disabled="disabled"
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
      />
    </el-form-item>

    <el-form-item
      label="电商MRO+办公流水金额（亿）"
      prop="mroOfficeFlowAmount"
      :error="errors.mroOfficeFlowAmount"
    >
      <el-input-number
        v-model="localBasic.mroOfficeFlowAmount"
        :min="0"
        :precision="2"
        :disabled="disabled"
        placeholder="单位亿，非负数"
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
      />
    </el-form-item>

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
        :readonly="disabled"
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
        :readonly="disabled"
      />
    </el-form-item>

    <el-form-item
      label="项目经理综合评估是否有兜底方案"
      prop="contingencyPlan"
    >
      <el-input
        v-model="localBasic.contingencyPlan"
        type="textarea"
        :rows="3"
        placeholder="请填写兜底方案评估"
        maxlength="5000"
        :readonly="disabled"
      />
    </el-form-item>

    <el-form-item
      label="项目经理是否了解评标全流程"
      prop="processKnowledge"
    >
      <el-input
        v-model="localBasic.processKnowledge"
        type="textarea"
        :rows="3"
        placeholder="请填写对评标流程的了解程度"
        maxlength="5000"
        :readonly="disabled"
      />
    </el-form-item>

    <el-form-item
      label="需要的支持及其他关键信息备注"
      prop="supportNotes"
    >
      <el-input
        v-model="localBasic.supportNotes"
        type="textarea"
        :rows="3"
        placeholder="请填写需要的支持及备注"
        maxlength="5000"
        :readonly="disabled"
      />
    </el-form-item>

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
