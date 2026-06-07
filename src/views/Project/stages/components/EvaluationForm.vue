<template>
  <div class="evaluation-form">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="140px"
      size="default"
    >
      <el-form-item label="项目背景" prop="background">
        <el-input
          v-model="formData.background"
          type="textarea"
          :rows="4"
          placeholder="请输入项目背景"
        />
      </el-form-item>

      <el-form-item label="竞争对手情况" prop="competitors">
        <el-input
          v-model="formData.competitors"
          type="textarea"
          :rows="4"
          placeholder="请输入竞争对手情况"
        />
      </el-form-item>

      <el-form-item label="项目合同周期" prop="contractPeriod">
        <el-input
          v-model="formData.contractPeriod"
          placeholder="如：12个月、2年"
          maxlength="64"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="入围家数" prop="shortlistedBidders">
        <el-input-number
          v-model="formData.shortlistedBidders"
          :min="1"
          :max="999"
          placeholder="请输入入围家数"
        />
      </el-form-item>

      <el-form-item label="平台服务费" prop="platformFee">
        <el-input-number
          v-model="formData.platformFee"
          :precision="2"
          :min="0"
          :max="9999999999.99"
          placeholder="请输入平台服务费"
        />
      </el-form-item>

      <el-form-item label="上一轮报价情况" prop="previousBid">
        <el-input
          v-model="formData.previousBid"
          type="textarea"
          :rows="3"
          placeholder="请输入上一轮报价情况（非必填）"
        />
      </el-form-item>

      <el-form-item label="建议是否投标" prop="recommendation">
        <el-radio-group v-model="formData.recommendation">
          <el-radio :value="true">建议投标</el-radio>
          <el-radio :value="false">不建议投标</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="$emit('save')">保存表单</el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'

const props = defineProps({
  modelValue: {
    type: Object,
    required: true
  },
  saving: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'save', 'reset'])

const formRef = ref(null)

const formData = reactive({
  get background() { return props.modelValue.background },
  set background(v) { emit('update:modelValue', { ...props.modelValue, background: v }) },
  get competitors() { return props.modelValue.competitors },
  set competitors(v) { emit('update:modelValue', { ...props.modelValue, competitors: v }) },
  get contractPeriod() { return props.modelValue.contractPeriod },
  set contractPeriod(v) { emit('update:modelValue', { ...props.modelValue, contractPeriod: v }) },
  get shortlistedBidders() { return props.modelValue.shortlistedBidders },
  set shortlistedBidders(v) { emit('update:modelValue', { ...props.modelValue, shortlistedBidders: v }) },
  get platformFee() { return props.modelValue.platformFee },
  set platformFee(v) { emit('update:modelValue', { ...props.modelValue, platformFee: v }) },
  get previousBid() { return props.modelValue.previousBid },
  set previousBid(v) { emit('update:modelValue', { ...props.modelValue, previousBid: v }) },
  get recommendation() { return props.modelValue.recommendation },
  set recommendation(v) { emit('update:modelValue', { ...props.modelValue, recommendation: v }) }
})

const formRules = {
  background: [{ required: true, message: '请输入项目背景', trigger: 'blur' }],
  competitors: [{ required: true, message: '请输入竞争对手情况', trigger: 'blur' }],
  contractPeriod: [{ required: true, message: '请输入项目合同周期', trigger: 'blur' }],
  shortlistedBidders: [{ required: true, message: '请输入入围家数', trigger: 'blur' }],
  platformFee: [{ required: true, message: '请输入平台服务费', trigger: 'blur' }]
}

function validate() {
  return formRef.value?.validate()
}

function resetFields() {
  formRef.value?.resetFields()
}

defineExpose({ validate, resetFields })
</script>

<style scoped>
.evaluation-form {
  /* no extra styles needed */
}
</style>
