// Input: governed file content (string)
// Output: { violations: string[] } — header missing/malformed violations
// Pos: scripts/lib/ - extracted from check-doc-governance.mjs for unit testability
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export function normalizeComment(line) {
  return line.replace(/^\s*(\/\/|#)\s?/, '').trim()
}

/**
 * 检查 governed file 的 header（前 20 行注释）是否符合契约：
 *  - 至少含 Input/Output/Pos 三个前缀
 *  - 至少含「维护声明:」行
 *  - 空行 + shebang 行 跳过；非注释非空行 break 扫描
 *
 * 修复前的 bug：>= 4 个注释行就 break，丢了 L11/12 的 Pos + 维护声明
 * （当中间夹多行 list 注释如「- foo: bar」时，commentLines 提前凑满 4 break）
 *
 * 修复后：去掉 >= 4 早 break；只遇「非 shebang / 非空行 / 非 #// 注释」时 break
 * （即真正的代码起始 = 第 1 个 content 行）
 *
 * 额外兼容：block-comment 风格 `*` 行（多行 list 注释的延续）也视作注释
 * ——这是合理的，因为很多项目用 `* - foo: bar` 表示 list items。
 */
export function checkGovernedFileContent(content) {
  const lines = content.split('\n').slice(0, 20)
  const commentLines = []
  const isJavaFile = false // 简化（spec 走 .mjs，Java 路径不覆盖）
  for (const line of lines) {
    if (line.startsWith('#!')) continue
    if (isJavaFile && /^package\s+.+;$/.test(line.trim())) continue
    // 修复：原 `^\s*(\/\/|#)` 改成同时认 `*` 起头（多行 list 注释延续）
    if (/^\s*(\/\/|#|\*)/.test(line)) {
      commentLines.push(normalizeComment(line))
      continue
    }
    if (line.trim() === '') continue
    break
  }
  const violations = []
  for (const prefix of ['Input:', 'Output:', 'Pos:']) {
    if (!commentLines.some((line) => line.startsWith(prefix))) {
      violations.push(`missing header line "${prefix}"`)
    }
  }
  if (!commentLines.includes('一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。')
    && !commentLines.some((line) => line.startsWith('维护声明:'))) {
    violations.push('missing maintenance declaration')
  }
  return { violations, commentLines }
}
