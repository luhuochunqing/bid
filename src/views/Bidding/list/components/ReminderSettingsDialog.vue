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
          multiple
          placeholder="搜索并选择通知对象（姓名/工号/拼音）"
          style="width: 100%"
          @select="handleUsersSelected"
        />
      </el-form-item>

      <el-form-item label="启用状态">
        <el-switch v-model="form.enabled" />
      </el-form-item>

      <el-form-item v-if="form.reminderTargets.length > 0" label="已选通知对象">
        <div class="selected-targets">
          <el-tag
            v-for="target in form.reminderTargets"
            :key="target.userId"
            closable
            @close="removeTarget(target)"
          >
            {{ target.userName }}
          </el-tag>
        </div>
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

// UserPicker 使用 userId 数组，转换为 API 需要的格式
const selectedUserIds = ref([])

// 编辑时从 reminderTargets 恢复 userId 列表
watch(editingReminder, (reminder) => {
  if (reminder && reminder.reminderTargets) {
    selectedUserIds.value = reminder.reminderTargets.map(t => t.userId)
  } else {
    selectedUserIds.value = []
  }
})

function handleUsersSelected(users) {
  // 将 User 对象数组转换为 API 需要的格式
  form.reminderTargets = users.map(user => ({
    userId: user.id,
    userName: user.name,
    wecomUserId: user.wecomUserId || ''
  }))
}

function handleSave() {
  saveReminder().then(() => {
    emit('saved')
  })
}

function removeTarget(target) {
  const index = form.reminderTargets.findIndex(t => t.userId === target.userId)
  if (index > -1) {
    form.reminderTargets.splice(index, 1)
    selectedUserIds.value = selectedUserIds.value.filter(id => id !== target.userId)
  }
}

// 暴露方法供父组件调用
defineExpose({
  openCreateDialog,
  openEditDialog
})
</script>

<style scoped>
.selected-targets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
