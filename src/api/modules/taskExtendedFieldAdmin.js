// Input: httpClient for admin CRUD of task extended field schema
// Output: taskExtendedFieldAdminApi - list/create/update/disable/enable/reorder
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const BASE = '/api/admin/task-extended-fields'

async function list() { return httpClient.get(BASE) }
async function create(dto) { return httpClient.post(BASE, dto) }
async function update(key, dto) { return httpClient.put(`${BASE}/${key}`, dto) }
async function disable(key) { return httpClient.patch(`${BASE}/${key}/disable`) }
async function enable(key) { return httpClient.patch(`${BASE}/${key}/enable`) }
async function reorder(items) { return httpClient.patch(`${BASE}/reorder`, { items }) }

export const taskExtendedFieldAdminApi = { list, create, update, disable, enable, reorder }
export default taskExtendedFieldAdminApi
