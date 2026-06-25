// Input: onSave async callback receiving (task, { name, type, file })
// Output: reactive dialog state + openDialog/handleFileChange/save handlers
// Pos: src/composables/ - Deliverable upload composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

export function useDeliverableUpload({ onSave }) {
  const dialogVisible = ref(false)
  const currentTask = ref(null)
  const fileList = ref([])
  const form = ref({ name: '', type: 'document', file: null })
  const saving = ref(false)

  function openDialog(task) {
    currentTask.value = task
    form.value = { name: '', type: 'document', file: null }
    fileList.value = []
    dialogVisible.value = true
  }

  function handleFileChange(file) {
    form.value.file = file.raw
  }

  async function save() {
    if (!currentTask.value || !form.value.name) {
      ElMessage.warning('请填写交付物名称')
      return
    }
    saving.value = true
    try {
      await onSave(currentTask.value, { ...form.value })
      ElMessage.success('交付物已保存')
      dialogVisible.value = false
      form.value = { name: '', type: 'document', file: null }
      fileList.value = []
    } catch (error) {
      ElMessage.error(error?.message || '交付物上传失败')
    } finally {
      saving.value = false
    }
  }

  return { dialogVisible, currentTask, fileList, form, saving, openDialog, handleFileChange, save }
}