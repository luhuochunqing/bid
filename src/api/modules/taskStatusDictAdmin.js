// Input: httpClient for admin task status dict CRUD + reorder
// Output: taskStatusDictAdminApi - admin-only ops on task status dict
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const BASE = '/api/admin/task-status-dict'

async function list() {
  return httpClient.get(BASE)
}

async function create(dto) {
  return httpClient.post(BASE, dto)
}

async function update(code, dto) {
  return httpClient.put(`${BASE}/${code}`, dto)
}

async function disable(code) {
  return httpClient.patch(`${BASE}/${code}/disable`)
}

async function enable(code) {
  return httpClient.patch(`${BASE}/${code}/enable`)
}

async function reorder(items) {
  return httpClient.patch(`${BASE}/reorder`, { items })
}

export const taskStatusDictAdminApi = { list, create, update, disable, enable, reorder }
export default taskStatusDictAdminApi
