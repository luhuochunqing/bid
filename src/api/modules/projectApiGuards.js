// Input: frontend route/entity IDs
// Output: API-mode guard decisions for project-scoped requests
// Pos: src/api/modules/ - API guard helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export function isNumericId(id) {
  return /^-?\d+$/.test(String(id))
}

export function isDemoEntityId(id) {
  return Number(id) < 0
}

export function apiModeFailure(entityName) {
  return {
    success: false,
    message: `Current backend only supports numeric ${entityName} IDs in API mode`
  }
}

export function demoReadonlyFailure() {
  return {
    success: false,
    message: 'Demo records are read-only in e2e mode'
  }
}
