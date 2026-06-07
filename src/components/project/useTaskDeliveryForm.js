import { computed } from 'vue'

/**
 * 任务交付物表单逻辑：交付物上传 + 完成情况说明。
 * 为 TaskForm.vue 瘦身，将新增的非核心表单字段逻辑提取到此 composable。
 */
export function useTaskDeliveryForm(localValue, _readonly) {
  const deliverableFileList = computed(() => (localValue.deliverableFiles || []).map((file, index) => ({
    name: file?.name || `交付物${index + 1}`,
    raw: file,
  })))

  function handleDeliverableChange(file, fileList = []) {
    localValue.deliverableFiles = (Array.isArray(fileList) ? fileList : [fileList])
      .map((item) => item?.raw || item)
      .filter(Boolean)
  }

  function handleDeliverableRemove(_file, fileList = []) {
    localValue.deliverableFiles = (Array.isArray(fileList) ? fileList : [])
      .map((item) => item?.raw || item)
      .filter(Boolean)
  }

  return {
    deliverableFileList,
    handleDeliverableChange,
    handleDeliverableRemove,
  }
}
