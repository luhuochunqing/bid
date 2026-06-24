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
        <UserPicker
          v-model="localForm.assignee"
          mode="candidates"
          context="tender"
          placeholder="选择人员"
          class="full-width"
          @select="onUserSelect"
        />
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
import { ref, computed } from 'vue'
import UserPicker from '@/components/common/UserPicker.vue'

const modelValue = defineModel({ type: Boolean, default: false })
const props = defineProps({
  tenderId: { type: Number, default: null },
  tenderTitle: { type: String, default: '' },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['reset', 'submit'])

const localForm = ref({ assignee: null, remark: '' })
const selectedUser = ref(null)

const selectedDepartment = computed(() => {
  return selectedUser.value?.departmentName || selectedUser.value?.deptName || ''
})

function onUserSelect(user) {
  selectedUser.value = user
}

const handleSubmit = () => {
  emit('submit', {
    tenderId: props.tenderId,
    assignee: localForm.value.assignee,
    remark: localForm.value.remark,
  })
}

const handleClose = () => {
  localForm.value = { assignee: null, remark: '' }
  selectedUser.value = null
  emit('reset')
}
</script>
