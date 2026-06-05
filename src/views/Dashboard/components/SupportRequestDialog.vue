<!-- Input: Workbench SupportRequestDialog props and user actions
Output: presentational Workbench SupportRequestDialog section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <el-dialog v-model="dialogVisible" :title="title" width="640px" destroy-on-close>
    <el-form :model="form" label-width="110px">
      <el-form-item label="关联项目" required>
        <EmptyState
          v-if="projectsError"
          state="error"
          icon="!"
          title="项目加载失败"
          :description="projectsError"
          action-label="重试"
          @action="emit('retry-projects')"
        />
        <EmptyState
          v-else-if="projects.length === 0"
          icon="项"
          title="暂无可申请支持的项目"
          description="请先创建或分配投标项目，再提交标书支持申请。"
        />
        <el-select
          v-else
          :model-value="form.projectId"
          filterable
          placeholder="请选择投标项目"
          style="width: 100%"
          @update:model-value="(value) => updateField('projectId', value)"
        >
          <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="申请类型" required>
        <el-select
          :model-value="form.type"
          style="width: 100%"
          @update:model-value="(value) => updateField('type', value)"
        >
          <el-option label="技术支持" value="technical_support" />
          <el-option label="商务支持" value="commercial_support" />
          <el-option label="综合支持" value="bid_support" />
        </el-select>
      </el-form-item>
      <el-form-item label="期望完成时间">
        <el-date-picker
          :model-value="form.dueDate"
          type="datetime"
          placeholder="请选择期望完成时间"
          style="width: 100%"
          value-format="YYYY-MM-DDTHH:mm:ss"
          @update:model-value="(value) => updateField('dueDate', value)"
        />
      </el-form-item>
      <el-form-item label="需求说明" required>
        <el-input
          :model-value="form.description"
          type="textarea"
          :rows="5"
          maxlength="500"
          show-word-limit
          placeholder="请说明需要的支持内容、交付物和时间要求"
          @update:model-value="(value) => updateField('description', value)"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="emit('submit')">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import EmptyState from './EmptyState.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '标书支持申请' },
  form: {
    type: Object,
    default: () => ({ projectId: null, type: 'bid_support', dueDate: '', description: '' }),
  },
  projects: { type: Array, default: () => [] },
  projectsError: { type: String, default: '' },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'update:form', 'cancel', 'submit', 'retry-projects'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const updateField = (field, value) => {
  emit('update:form', { ...props.form, [field]: value })
}

const handleCancel = () => {
  emit('update:modelValue', false)
  emit('cancel')
}
</script>
