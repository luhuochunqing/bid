import { ref, watch } from 'vue'

/**
 * 任务交付物表单逻辑：交付物上传 + 完成情况说明。
 * 为 TaskForm.vue 瘦身，将新增的非核心表单字段逻辑提取到此 composable。
 */
export function useTaskDeliveryForm(localValue, _readonly) {
  // 使用普通 ref + diff watch 保持 fileList 引用稳定，
  // 避免每次 computed 重算都创建新数组导致 el-upload 闪烁
  const deliverableFileList = ref([])

  function rebuildFileList() {
    // 编辑/上传优先用 deliverableFiles (raw File objects)
    const files = localValue.deliverableFiles
    if (files?.length) {
      const list = files.map((file, i) => ({ name: file?.name || `交付物${i + 1}`, raw: file }))
      const oldJson = JSON.stringify(deliverableFileList.value)
      const newJson = JSON.stringify(list)
      if (oldJson !== newJson) deliverableFileList.value = list
      return
    }
    // 查看模式用 deliverables (backend DTO)
    const dels = localValue.deliverables
    if (dels?.length) {
      const list = dels.map((d, i) => ({ name: d?.name || `交付物${i + 1}`, url: d?.url, id: d?.id }))
      const oldJson = JSON.stringify(deliverableFileList.value)
      const newJson = JSON.stringify(list)
      if (oldJson !== newJson) deliverableFileList.value = list
      return
    }
    if (deliverableFileList.value.length) deliverableFileList.value = []
  }

  rebuildFileList()
  watch(() => [localValue.deliverableFiles, localValue.deliverables], rebuildFileList, { deep: true })

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
