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
    // 仅填充待上传的新文件（raw File objects）。
    // 已保存的交付物（deliverables）交给 TaskForm 模板的 .deliverable-list 渲染为可下载链接，
    // 不再塞入 el-upload 的 file-list —— 否则只读模式下同一交付物会同时出现在
    // el-upload 的禁用条目（不可下载）和 deliverable-list（可下载）里，重复且令人困惑。
    const files = localValue.deliverableFiles
    if (files?.length) {
      const list = files.map((file, i) => ({ name: file?.name || `交付物${i + 1}`, raw: file }))
      const oldJson = JSON.stringify(deliverableFileList.value)
      const newJson = JSON.stringify(list)
      if (oldJson !== newJson) deliverableFileList.value = list
      return
    }
    if (deliverableFileList.value.length) deliverableFileList.value = []
  }

  rebuildFileList()
  watch(() => localValue.deliverableFiles, rebuildFileList, { deep: true })

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
