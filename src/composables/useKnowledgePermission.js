import { computed } from 'vue'
import { useUserStore } from '@/stores/user.js'
import { isBidManager, isBidAdminOrSenior } from '@/utils/permission'

export function useKnowledgePermission() {
  const userStore = useUserStore()
  const _role = () => userStore.userRole || (userStore.currentUser && userStore.currentUser.role) || ''
  const canManage = computed(() => isBidManager(_role()) || _role() === 'bid-administration')
  const canAdminAlert = computed(() => isBidAdminOrSenior(_role()))
  return { canManage, canAdminAlert }
}
