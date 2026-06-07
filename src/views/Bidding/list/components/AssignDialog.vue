<template>
  <el-dialog
    v-model="modelValue"
    title="指派标讯"
    width="520px"
    @close="handleClose"
  >
    <el-form :model="localForm" label-width="100px">
      <el-form-item label="标讯标题">
        <el-text>{{ tenderTitle }}</el-text>
      </el-form-item>
      <el-form-item label="指派给" required>
        <el-select
          v-model="localForm.assignee"
          filterable
          placeholder="选择人员"
          class="full-width"
          :loading="loadingCandidates"
        >
          <el-option
            v-for="candidate in candidates"
            :key="candidate.id"
            :label="candidate.name"
            :value="candidate.id"
          >
            {{ candidate.name }} · {{ candidate.departmentName }}
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="项目部门">
        <el-text>{{ selectedDepartment || '请先选择项目负责人' }}</el-text>
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="localForm.remark"
          type="textarea"
          :rows="3"
          placeholder="填写指派说明"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确认指派</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'

const modelValue = defineModel({ type: Boolean, default: false })
const props = defineProps({
  tenderId: { type: Number, default: null },
  tenderTitle: { type: String, default: '' },
  candidates: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  loadingCandidates: { type: Boolean, default: false },
})

const emit = defineEmits(['reset', 'submit'])

const localForm = ref({ assignee: null, remark: '' })

const selectedDepartment = computed(() => {
  const candidate = props.candidates?.find(c => c.id === localForm.value.assignee)
  return candidate?.departmentName || ''
})

const handleSubmit = () => {
  emit('submit', {
    tenderId: props.tenderId,
    assignee: localForm.value.assignee,
    remark: localForm.value.remark,
  })
}

const handleClose = () => {
  localForm.value = { assignee: null, remark: '' }
  emit('reset')
}
</script>
