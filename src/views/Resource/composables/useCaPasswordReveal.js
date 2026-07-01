import { ref, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { isBidManager } from '@/utils/permission'
import { caApi } from '@/api/modules/ca.js'

/**
 * CA 密码显示/隐藏逻辑（从 CAFormDialog 提取，保持组件行数 < 300）
 *
 * @param {import('vue').ComputedRef<boolean>} isEdit - 是否编辑模式
 * @param {import('vue').Ref<object|null>} caRef - CA 对象 ref
 * @param {object} form - 表单响应式对象（需含 caPassword 字段）
 */
export function useCaPasswordReveal(isEdit, caRef, form) {
  const passwordRevealed = ref(false)
  const passwordLoading = ref(false)
  const userStore = useUserStore()

  const canViewPassword = computed(() => {
    if (!isEdit.value) return false
    if (isBidManager(userStore.userRole)) return true
    const currentId = userStore.currentUser?.id
    const custodianId = caRef.value?.custodianId
    return currentId != null && custodianId != null && String(currentId) === String(custodianId)
  })

  function resetPasswordState() {
    passwordRevealed.value = false
  }

  async function handleRevealPassword() {
    if (passwordRevealed.value) {
      passwordRevealed.value = false
      form.caPassword = ''
      return
    }
    passwordLoading.value = true
    try {
      const res = await caApi.getPassword(caRef.value.id)
      if (res?.success && res?.data?.caPassword) {
        form.caPassword = res.data.caPassword
        passwordRevealed.value = true
      }
    } finally {
      passwordLoading.value = false
    }
  }

  return {
    passwordRevealed,
    passwordLoading,
    canViewPassword,
    resetPasswordState,
    handleRevealPassword
  }
}
