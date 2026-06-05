// Input: httpClient for public read of task extended field schema
// Output: taskExtendedFieldApi - list enabled extended fields for TaskForm rendering
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

async function list() {
  return httpClient.get('/api/task-extended-fields')
}

export const taskExtendedFieldApi = { list }
export default taskExtendedFieldApi
