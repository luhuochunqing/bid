// Input: displayed tender rows and table ref
// Output: selection state and table selection actions
// Pos: src/views/Bidding/list/ - Tender selection composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'

export function useTenderSelection({ displayTenders }) {
  const tableRef = ref(null)
  const selectedTenders = ref([])
  const selectAllChecked = ref(false)
  const isIndeterminate = ref(false)

  const syncSelectionState = () => {
    selectAllChecked.value = selectedTenders.value.length > 0
    isIndeterminate.value =
      selectedTenders.value.length > 0
      && selectedTenders.value.length < displayTenders.value.length
  }

  const handleSelectionChange = (selection) => {
    selectedTenders.value = selection
    syncSelectionState()
  }

  const handleSelectAll = (val) => {
    if (val) {
      displayTenders.value.forEach((row) => {
        tableRef.value?.toggleRowSelection(row, true)
      })
    } else {
      tableRef.value?.clearSelection()
    }
  }

  const handleClearSelection = () => {
    tableRef.value?.clearSelection()
    selectedTenders.value = []
    selectAllChecked.value = false
    isIndeterminate.value = false
  }

  const selectSingleTender = (row) => {
    selectedTenders.value = [row]
    syncSelectionState()
  }

  return {
    tableRef,
    selectedTenders,
    selectAllChecked,
    isIndeterminate,
    handleSelectionChange,
    handleSelectAll,
    handleClearSelection,
    selectSingleTender,
  }
}
