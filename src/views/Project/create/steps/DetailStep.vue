<template>
  <!-- Adaptive Dynamic Form (M4.1: Dynamic Form Engine — project.detail scope) -->
  <AdaptiveFormPage
    ref="adaptiveForm"
    scope="project.detail"
    :model-value="detailForm"
    :disabled="false"
    @update:model-value="handleDynamicUpdate"
    @submit="$emit('submit')"
  >
    <!-- #fallback-form: original hardcoded detail form -->
    <template #fallback-form>
      <el-form ref="formRef" :model="detailForm" :rules="detailRules" label-width="120px">
        <el-divider content-position="left">项目详情</el-divider>

        <el-form-item label="项目描述" prop="description">
          <el-input
            v-model="detailForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入项目描述、需求概述等"
          />
        </el-form-item>

        <el-form-item label="项目标签" prop="tags">
          <el-select
            v-model="detailForm.tags"
            multiple
            filterable
            allow-create
            placeholder="请选择或输入标签"
          >
            <el-option label="智慧办公" value="智慧办公" />
            <el-option label="信创" value="信创" />
            <el-option label="大数据" value="大数据" />
            <el-option label="云计算" value="云计算" />
            <el-option label="物联网" value="物联网" />
            <el-option label="AI" value="AI" />
            <el-option label="高优先级" value="高优先级" />
          </el-select>
        </el-form-item>

        <el-form-item label="预计开工日期">
          <el-date-picker
            v-model="detailForm.startDate"
            type="date"
            placeholder="请选择预计开工日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <el-form-item label="预计完工日期">
          <el-date-picker
            v-model="detailForm.endDate"
            type="date"
            placeholder="请选择预计完工日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="detailForm.remark"
            type="textarea"
            :rows="3"
            placeholder="其他备注信息"
          />
        </el-form-item>
      </el-form>
    </template>
  </AdaptiveFormPage>
</template>

<script setup>
import { ref, shallowRef } from 'vue'
import AdaptiveFormPage from '@/components/common/AdaptiveFormPage.vue'

const detailFormRef = defineModel('detailForm', { type: Object, required: true })
// Proxy: template uses `detailForm` (Vue auto-unwraps), script uses `detailFormRef.value`
const detailForm = detailFormRef

const formRef = ref(null)
const adaptiveForm = shallowRef(null)

const detailRules = {
  description: [{ required: true, message: '请输入项目描述', trigger: 'blur' }]
}

function handleDynamicUpdate(value) {
  Object.assign(detailForm.value, value)
}

async function validate() {
  if (adaptiveForm.value?.isDynamic?.value) {
    const result = await adaptiveForm.value.validate()
    return result === ''
  }
  return formRef.value?.validate().catch(() => false)
}

defineExpose({ validate })
</script>
