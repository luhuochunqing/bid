// Input: httpClient for task status dictionary requests
// Output: taskStatusDictApi - list enabled task statuses
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

async function list() {
  return httpClient.get('/api/task-status-dict')
}

export const taskStatusDictApi = { list }

export default taskStatusDictApi
