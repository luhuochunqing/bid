// Input: User objects from UserPicker @select event
// Output: common conversion helpers consumed by parent components
// Pos: src/utils/ - shared user picker utility
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 从用户对象数组提取 ID 数组
 * 适用于 v-model 绑定的 source of truth 更新
 */
export function toUserIds(users) {
  return (users || []).map(u => u.id)
}

/**
 * 从单个用户对象获取显示名称
 */
export function toUserName(user) {
  return user?.name || user?.fullName || ''
}

/**
 * 将 UserPicker 返回的用户对象转换为提醒目标格式
 * @param {Array} users - UserPicker @select 返回的用户对象数组
 * @returns {Array<{userId, userName, wecomUserId}>}
 */
export function toReminderTargets(users) {
  return (users || []).map(user => ({
    userId: user.id,
    userName: user.name,
    wecomUserId: user.wecomUserId || ''
  }))
}

/**
 * 将提醒目标格式转换回 UserPicker initialOptions 格式
 * @param {Array} targets - reminderTargets 数组
 * @returns {Array<{id, name}>}
 */
export function fromReminderTargets(targets) {
  return (targets || []).map(t => ({
    id: t.userId,
    name: t.userName,
    wecomUserId: t.wecomUserId || ''
  }))
}