// Input: template-library collection and action composables
// Output: lightweight template page orchestration facade
// Pos: src/views/Knowledge/components/template/ - template page composable layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, onMounted } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { useTemplateLibraryActions } from './useTemplateLibraryActions.js'
import { useTemplateLibraryCollection } from './useTemplateLibraryCollection.js'

export function useTemplateLibraryPage() {
  const projectStore = useProjectStore()
  const userStore = useUserStore()

  const collection = useTemplateLibraryCollection()
  const inProgressProjects = computed(() => projectStore.inProgressProjects)
  const actions = useTemplateLibraryActions({
    activeCategory: collection.activeCategory,
    templates: collection.templates,
    userStore,
    loadTemplates: collection.loadTemplates
  })

  onMounted(collection.loadTemplates)

  return {
    inProgressProjects,
    ...collection,
    ...actions
  }
}
