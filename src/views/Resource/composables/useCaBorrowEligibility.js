import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { isBidManager } from '@/utils/permission'

export const STATUS_TAG_TYPE = { ACTIVE: 'success', EXPIRING: 'warning', EXPIRED: 'danger', INACTIVE: 'info' }
export const BORROW_STATUS_TAG_TYPE = { IN_STOCK: 'info', BORROWED: 'primary', OVERDUE: 'danger' }
export const APPLICATION_STATUS_TAG_TYPE = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  RETURNED: 'info',
  CANCELLED: 'info'
}
export const EVENT_TYPE_COLOR = {
  CREATED: 'success',
  UPDATED: 'primary',
  BORROWED: 'primary',
  RETURNED: 'info',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'warning',
  DEACTIVATED: 'danger',
  ACTIVATED: 'success'
}

export function caStatusTagType(status) {
  return STATUS_TAG_TYPE[status] || 'info'
}

export function caBorrowStatusTagType(borrowStatus) {
  return BORROW_STATUS_TAG_TYPE[borrowStatus] || 'info'
}

export function caApplicationStatusTagType(status) {
  return APPLICATION_STATUS_TAG_TYPE[status] || 'info'
}

export function caEventTypeColor(eventType) {
  return EVENT_TYPE_COLOR[eventType] || 'info'
}

export function isCaBorrowableByStatus(ca) {
  if (!ca) return false
  return (
    ca.borrowStatus === 'IN_STOCK' &&
    ca.caType === 'ENTITY_CA' &&
    ca.status !== 'EXPIRED' &&
    ca.status !== 'INACTIVE'
  )
}

export function isCaReturnableByStatus(ca) {
  if (!ca) return false
  return ca.borrowStatus === 'BORROWED'
}

export function useCaBorrowEligibility() {
  const userStore = useUserStore()
  const currentUserId = computed(() => userStore.currentUser?.id ?? null)

  function canBorrow(ca) {
    if (!isCaBorrowableByStatus(ca)) return false
    return canBorrowByRole(ca)
  }

  function canBorrowByRole(ca) {
    if (!ca) return false
    const role = userStore.userRole
    if (isBidManager(role)) return true
    if (role === 'bid-Team') {
      return ca.custodianId == null || ca.custodianId !== currentUserId.value
    }
    return false
  }

  function canManage(ca) {
    if (!ca) return false
    const role = userStore.userRole
    if (isBidManager(role)) return true
    if (role === 'bid-Team') {
      return ca.custodianId != null && ca.custodianId === currentUserId.value
    }
    return false
  }

  function canReturn(ca) {
    if (!isCaReturnableByStatus(ca)) return false
    return canManage(ca)
  }

  return { canBorrow, canBorrowByRole, canManage, canReturn }
}
