// Input: 章节树（[{label|value, children}]）、默认章节文本
// Output: findSectionPathByChapter - 返回级联选择器可用的节点路径数组
// Pos: src/views/Bidding/components/ - AI 案例复用章节树匹配工具
// PRD 4.2：复用定位弹窗章节树默认选中当前评分项对应章节（可修改）。
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 广度优先在章节树中查找与默认章节匹配的路径：
 * 1) 完整路径拼接（' / '）相等 2) 节点 label 相等 3) 包含关系（最长匹配优先）
 * @param {Array} tree 章节树节点数组
 * @param {string} chapter 默认章节文本
 * @returns {string[]} 命中的节点路径（从根到命中节点）；无匹配返回空数组
 */
export function findSectionPathByChapter(tree, chapter) {
  if (!chapter || !Array.isArray(tree) || !tree.length) return []
  const target = String(chapter).trim()
  if (!target) return []
  const queue = tree.map(node => ({ node, path: [node.label ?? node.value] }))
  let fuzzyPath = []
  let fuzzyLen = 0
  while (queue.length) {
    const { node, path } = queue.shift()
    const label = String(node.label ?? node.value ?? '')
    const joined = path.join(' / ')
    if (joined === target || label === target) return path
    if ((label.includes(target) || target.includes(label)) && label.length > fuzzyLen) {
      fuzzyPath = path
      fuzzyLen = label.length
    }
    if (Array.isArray(node.children)) {
      for (const child of node.children) {
        queue.push({ node: child, path: [...path, child.label ?? child.value] })
      }
    }
  }
  return fuzzyPath
}
