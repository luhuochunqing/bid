import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import remindersApi from '@/api/modules/reminders.js'
import { usersApi } from '@/api/modules/users.js'

export function useReminderSettings(tenderId) {
  const loading = ref(false)
  const reminders = ref([])
  const users = ref([])
  const loadingUsers = ref(false)

  const form = reactive({
    reminderType: 'REGISTRATION_DEADLINE',
    remindBeforeHours: 24,
    reminderTargets: [],
    enabled: true
  })

  const dialogVisible = ref(false)
  const editingReminder = ref(null)
  const saving = ref(false)

  /**
   * 加载提醒设置列表
   */
  async function loadReminders() {
    if (!tenderId) return
    loading.value = true
    try {
      const response = await remindersApi.getReminders(tenderId)
      reminders.value = response.data || []
    } catch (error) {
      console.error('加载提醒设置失败:', error)
      reminders.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 加载可选通知用户列表
   */
  async function loadUsers() {
    loadingUsers.value = true
    try {
      const list = await usersApi.search('', 100)
      users.value = Array.isArray(list) ? list : []
    } catch (error) {
      console.error('加载用户列表失败:', error)
      users.value = []
    } finally {
      loadingUsers.value = false
    }
  }

  /**
   * 打开创建对话框
   */
  function openCreateDialog() {
    editingReminder.value = null
    form.reminderType = 'REGISTRATION_DEADLINE'
    form.remindBeforeHours = 24
    form.reminderTargets = []
    form.enabled = true
    dialogVisible.value = true
    loadUsers()
  }

  /**
   * 打开编辑对话框
   */
  function openEditDialog(reminder) {
    editingReminder.value = reminder
    form.reminderType = reminder.reminderType
    form.remindBeforeHours = reminder.remindBeforeHours
    form.reminderTargets = reminder.reminderTargets || []
    form.enabled = reminder.enabled
    dialogVisible.value = true
    loadUsers()
  }

  /**
   * 保存提醒设置
   */
  async function saveReminder() {
    if (!form.reminderTargets || form.reminderTargets.length === 0) {
      ElMessage.warning('请至少选择一个通知对象')
      return
    }

    saving.value = true
    try {
      if (editingReminder.value) {
        await remindersApi.updateReminder(tenderId, editingReminder.value.id, {
          remindBeforeHours: form.remindBeforeHours,
          reminderTargets: form.reminderTargets,
          enabled: form.enabled
        })
        ElMessage.success('提醒设置已更新')
      } else {
        await remindersApi.createReminder(tenderId, {
          reminderType: form.reminderType,
          remindBeforeHours: form.remindBeforeHours,
          reminderTargets: form.reminderTargets,
          enabled: form.enabled
        })
        ElMessage.success('提醒设置已创建')
      }
      dialogVisible.value = false
      await loadReminders()
    } catch (error) {
      console.error('保存提醒设置失败:', error)
    } finally {
      saving.value = false
    }
  }

  /**
   * 删除提醒设置
   */
  async function deleteReminder(reminder) {
    try {
      await ElMessageBox.confirm(
        `确定要删除"${reminder.reminderTypeDesc}"提醒吗？`,
        '删除确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )

      await remindersApi.deleteReminder(tenderId, reminder.id)
      ElMessage.success('提醒设置已删除')
      await loadReminders()
    } catch (error) {
      if (error !== 'cancel') {
        console.error('删除提醒设置失败:', error)
      }
    }
  }

  /**
   * 切换提醒状态
   */
  async function toggleReminder(reminder) {
    try {
      await remindersApi.toggleReminder(tenderId, reminder.id)
      ElMessage.success(`提醒已${reminder.enabled ? '禁用' : '启用'}`)
      await loadReminders()
    } catch (error) {
      console.error('切换提醒状态失败:', error)
    }
  }

  return {
    loading,
    reminders,
    users,
    loadingUsers,
    form,
    dialogVisible,
    editingReminder,
    saving,
    loadReminders,
    openCreateDialog,
    openEditDialog,
    saveReminder,
    deleteReminder,
    toggleReminder
  }
}
