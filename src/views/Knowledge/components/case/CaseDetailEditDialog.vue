<template>
  <el-dialog
    v-model="modelValue"
    title="编辑案例"
    width="720px"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="案例标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入案例标题" />
      </el-form-item>

      <el-form-item label="客户名称" prop="customer">
        <el-input v-model="form.customer" placeholder="请输入客户名称" />
      </el-form-item>

      <el-form-item label="客户行业" prop="industry">
        <el-select v-model="form.industry" placeholder="请选择行业" style="width: 100%">
          <el-option
            v-for="item in industries"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="项目金额" prop="amount">
        <el-input-number v-model="form.amount" :min="0" :step="10" />
        <span class="field-suffix">万元</span>
      </el-form-item>

      <el-form-item label="所在地区" prop="location">
        <el-input v-model="form.location" placeholder="如：浙江杭州" />
      </el-form-item>

      <el-form-item label="标签" prop="tags">
        <el-select
          v-model="form.tags"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="选择或输入标签"
          style="width: 100%"
        >
          <el-option
            v-for="item in tagOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="项目概述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          placeholder="简要描述项目背景、目标和主要内容"
        />
      </el-form-item>

      <el-form-item label="项目亮点" prop="highlights">
        <el-input
          v-model="highlightText"
          type="textarea"
          :rows="4"
          placeholder="每行一个亮点"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { caseCommonTags, caseIndustryOptions, createCaseEditRules } from './caseMeta.js'

const modelValue = defineModel({ type: Boolean, default: false })
const form = defineModel('form', { type: Object, required: true })
defineProps({
  saving: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['save'])

const formRef = ref(null)
const rules = createCaseEditRules()
const industries = caseIndustryOptions
const tagOptions = caseCommonTags

const highlightText = computed({
  get: () => Array.isArray(form.value.highlights) ? form.value.highlights.join('\n') : '',
  set: (value) => {
    form.value.highlights = String(value || '')
      .split('\n')
      .map(item => item.trim())
      .filter(Boolean)
  }
})

const handleSave = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    emit('save')
  } catch (error) {
    // Element Plus validation already renders inline feedback.
  }
}
</script>

<style scoped>
.field-suffix {
  margin-left: 8px;
  color: var(--text-secondary-ui);
}
</style>
