import { ref, watch } from 'vue'

/**
 * 任务交付物表单逻辑：交付物上传 + 完成情况说明。
 * 为 TaskForm.vue 瘦身，将新增的非核心表单字段逻辑提取到此 composable。
 */
export function useTaskDeliveryForm(localValue, _readonly) {
  // 使用普通 ref + deep watch 保持 fileList 引用稳定，
  // 避免每次 computed 重算都创建新数组导致 el-upload 闪烁
  const deliverableFileList = ref([])

  function rebuildFileList(files) {
    const list = (files || []).map((file, index) => ({
      name: file?.name || `交付物${index + 1}`,
      raw: file,
    }))
    // 只在内容实际变化时才更新 ref，维持稳定引用避免 el-upload 重绘
    const oldJson = JSON.stringify(deliverableFileList.value)
    const newJson = JSON.stringify(list)
    if (oldJson !== newJson) {
      deliverableFileList.value = list
    }
  }

  // 初始化 + 深度监听 localValue.deliverableFiles
  rebuildFileList(localValue.deliverableFiles)
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
