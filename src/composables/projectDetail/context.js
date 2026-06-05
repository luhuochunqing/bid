import { inject } from 'vue'

export const projectDetailKey = Symbol('projectDetail')

export function useProjectDetailContext() {
  const context = inject(projectDetailKey, null)
  if (!context) {
    throw new Error('Project detail context is not available')
  }
  return context
}
