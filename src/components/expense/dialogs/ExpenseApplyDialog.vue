<template>
  <el-dialog :model-value="modelValue" title="费用申请" width="500px" @update:model-value="$emit('update:modelValue', $event)">
    <el-form :model="form" label-width="100px">
      <el-form-item label="费用类型">
        <el-select v-model="form.type">
          <el-option label="保证金" value="保证金" />
          <el-option label="标书购买费" value="标书购买费" />
          <el-option label="差旅费" value="差旅费" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>
      <el-form-item label="关联项目">
        <el-select v-model="form.project" placeholder="请选择">
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="金额">
        <el-input-number v-model="form.amount" :min="0" :precision="2" />
      </el-form-item>
      <el-form-item label="申请说明">
        <el-input v-model="form.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineModel('form', { type: Object, required: true })

defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  projects: {
    type: Array,
    default: () => []
  },
  submitting: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue', 'submit'])
</script>
