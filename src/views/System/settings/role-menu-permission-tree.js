// Input: flat role menu permission arrays and grouped menu metadata
// Output: pure helpers for parent-child permission selection state
// Pos: src/views/System/settings/ - System settings role permission editor helpers

import { roleMenuGroups } from '@/config/sidebar-menu'

const valuesForGroup = (group = {}) => [
  group.value,
  ...(Array.isArray(group.children) ? group.children.map((child) => child.value) : [])
].filter(Boolean)

const uniquePermissions = (permissions = []) => [...new Set(
  permissions.filter((permission) => typeof permission === 'string' && permission.trim())
)]

export function normalizeRoleMenuPermissions(permissions = [], groups = roleMenuGroups) {
  const selected = new Set(uniquePermissions(permissions))
  const normalized = []

  groups.forEach((group) => {
    const children = Array.isArray(group.children) ? group.children : []
    const hasSelectedChild = children.some((child) => selected.has(child.value))
    if ((selected.has(group.value) || hasSelectedChild) && group.value) {
      normalized.push(group.value)
    }
    children.forEach((child) => {
      if (selected.has(child.value)) {
        normalized.push(child.value)
      }
    })
  })

  selected.forEach((permission) => {
    if (!normalized.includes(permission)) {
      normalized.push(permission)
    }
  })

  return uniquePermissions(normalized)
}

export function setRolePermissionGroup(permissions = [], group = {}, checked = false) {
  const selected = new Set(uniquePermissions(permissions))
  valuesForGroup(group).forEach((value) => {
    if (checked) {
      selected.add(value)
    } else {
      selected.delete(value)
    }
  })
  return uniquePermissions([...selected])
}

export function setRolePermissionChild(permissions = [], group = {}, child = {}, checked = false) {
  const selected = new Set(uniquePermissions(permissions))
  if (checked) {
    selected.add(group.value)
    selected.add(child.value)
  } else {
    selected.delete(child.value)
  }
  return uniquePermissions([...selected])
}

export function isRolePermissionGroupChecked(permissions = [], group = {}) {
  return uniquePermissions(permissions).includes(group.value)
}

export function isRolePermissionGroupIndeterminate(permissions = [], group = {}) {
  const children = Array.isArray(group.children) ? group.children : []
  if (children.length === 0) {
    return false
  }
  const selected = new Set(uniquePermissions(permissions))
  const selectedCount = children.filter((child) => selected.has(child.value)).length
  return selectedCount > 0 && selectedCount < children.length
}
