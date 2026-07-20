// Input: 用户的姓名与工号字段
// Output: "姓名 (工号)" 格式的统一显示字符串；工号缺失时仅显示姓名
// Pos: src/utils/ - 共享用户显示格式化工具
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 格式化用户显示："姓名 (工号)"，工号为空时仅显示姓名。
 * @param {string|null|undefined} name - 姓名
 * @param {string|null|undefined} employeeNumber - 工号
 * @returns {string} 格式化后的字符串；姓名为空返回 '-'
 */
export function formatUserWithNameAndNumber(name, employeeNumber) {
  if (!name) return '-'
  if (!employeeNumber) return name
  return `${name} (${employeeNumber})`
}
