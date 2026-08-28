/**
 * 数据分析部门树只保留一级和二级，更深节点不进入筛选下拉。
 */
export function capDepartmentTreeDepth(nodes, maxDepth = 2) {
  if (!Array.isArray(nodes) || maxDepth < 1) {
    return []
  }
  return nodes.map((node) => {
    const copy = { label: node.label, value: node.value }
    if (maxDepth > 1 && Array.isArray(node.children) && node.children.length > 0) {
      copy.children = capDepartmentTreeDepth(node.children, maxDepth - 1)
    }
    return copy
  })
}
