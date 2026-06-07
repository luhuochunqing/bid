import { computed } from 'vue'

const EMPTY_SUMMARY = {
  totalAmount: 0,
  paidAmount: 0,
  pendingAmount: 0,
  returnedAmount: 0,
}

export function useProjectExpenseAggregation({ projectStore, project, isApiProject }) {
  const projectExpenses = computed(() => projectStore.currentProjectExpenses || [])
  const expenseSummary = computed(() => projectStore.currentProjectExpenseSummary)
  const expenseLoading = computed(() => projectStore.expenseLoading)
  const expenseError = computed(() => projectStore.expenseError)

  const resetProjectExpenseAggregation = () => {
    projectStore.currentProjectExpenses = []
    projectStore.currentProjectExpenseSummary = { ...EMPTY_SUMMARY }
    projectStore.expenseError = ''
  }

  const loadProjectExpenseAggregation = async (projectId) => {
    if (!projectId || !isApiProject.value) {
      resetProjectExpenseAggregation()
      return
    }

    await projectStore.getProjectExpenses(projectId, {
      projectName: project.value?.name || '',
    })
  }

  return {
    projectExpenses,
    expenseSummary,
    expenseLoading,
    expenseError,
    loadProjectExpenseAggregation,
    resetProjectExpenseAggregation,
  }
}
