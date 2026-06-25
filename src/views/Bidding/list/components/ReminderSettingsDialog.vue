<template>
  <el-dialog
    v-model="modelValue"
    :title="editingReminder ? '编辑提醒设置' : '添加提醒设置'"
    width="600px"
  >
    <el-form :model="form" label-width="120px" :disabled="saving">
      <el-form-item label="提醒类型" required>
        <el-radio-group v-model="form.reminderType" :disabled="!!editingReminder">
          <el-radio value="REGISTRATION_DEADLINE">报名截止提醒</el-radio>
          <el-radio value="BID_OPENING">开标提醒</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="提前提醒时间" required>
        <el-select v-model="form.remindBeforeHours" placeholder="选择提前提醒时间">
          <el-option :value="12" label="提前12小时" />
          <el-option :value="24" label="提前24小时" />
          <el-option :value="48" label="提前48小时" />
          <el-option :value="72" label="提前72小时（3天）" />
          <el-option :value="168" label="提前168小时（7天）" />
        </el-select>
      </el-form-item>

      <el-form-item label="通知对象" required>
        <UserPicker
          v-model="selectedUserIds"
          mode="search"
          multiple
          placeholder="搜索并选择通知对象（姓名/工号/拼音）"
          style="width: 100%"
          :initial-options="userPickerInitialOptions"
          @select="handleUsersSelected"
        />
      </el-form-item>

      <el-form-item label="启用状态">
        <el-switch v-model="form.enabled" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        {{ editingReminder ? '保存修改' : '创建提醒' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useReminderSettings } from './useReminderSettings.js'
import UserPicker from '@/components/common/UserPicker.vue'
import { toReminderTargets, fromReminderTargets } from '@/utils/userPicker.js'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  tenderId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const {
  form,
  editingReminder,
  saving,
  openCreateDialog,
  openEditDialog,
  saveReminder
} = useReminderSettings(props.tenderId)

const modelValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// UserPicker v-model: array of selected user IDs
const selectedUserIds = ref([])

// Convert existing reminderTargets to initial-options format so UserPicker
// can display user names (not just IDs) when editing an existing reminder.
const userPickerInitialOptions = ref([])

// Sync selectedUserIds + initialOptions when form.reminderTargets changes
// (e.g. from openEditDialog which populates the form with existing targets).
watch(() => form.reminderTargets, (targets) => {
  if (targets && targets.length > 0) {
    selectedUserIds.value = targets.map(t => t.userId)
    userPickerInitialOptions.value = fromReminderTargets(targets)
  } else {
    selectedUserIds.value = []
    userPickerInitialOptions.value = []
  }
}, { immediate: true, deep: true })

// Convert UserPicker selected users to reminder target format and update form.
function handleUsersSelected(selectedUsers) {
  form.reminderTargets = toReminderTargets(selectedUsers)
}

function handleSave() {
  saveReminder().then(() => {
    emit('saved')
  })
}

// 暴露方法供父组件调用
defineExpose({
  openCreateDialog,
  openEditDialog
})
</script>