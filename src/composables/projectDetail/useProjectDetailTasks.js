import { useProjectDetailTaskActions } from './useProjectDetailTaskActions.js'

export function useProjectDetailTasks(context) {
  const state = {
    project: context.project,
    activities: context.activities,
    scoreDraftDialogVisible: context.scoreDraftDialogVisible,
    currentTask: context.currentTask,
  }
  const workflow = {
    handleInitiateProcess: context.handleInitiateProcess,
  }
  const actions = useProjectDetailTaskActions({ ...context, state, workflow })

  return {
    deliverableTypeMap: context.deliverableTypeMap,
    ...actions,
  }
}
