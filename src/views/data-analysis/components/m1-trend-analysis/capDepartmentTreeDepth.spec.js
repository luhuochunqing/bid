import { describe, expect, it } from 'vitest'
import { capDepartmentTreeDepth } from './capDepartmentTreeDepth.js'

describe('capDepartmentTreeDepth', () => {
  const tree = [
    {
      label: '销售部',
      value: '销售部',
      children: [
        {
          label: '销售一部',
          value: '销售一部',
          children: [{ label: '华东组', value: '华东组' }]
        },
        { label: '销售二部', value: '销售二部' }
      ]
    },
    { label: '财务部', value: '财务部' }
  ]

  it('只保留一级和二级，三级不再出现', () => {
    const capped = capDepartmentTreeDepth(tree, 2)
    expect(capped.map((n) => n.value)).toEqual(['销售部', '财务部'])
    expect(capped[0].children.map((n) => n.value)).toEqual(['销售一部', '销售二部'])
    expect(capped[0].children[0].children).toBeUndefined()
    expect(capped[1].children).toBeUndefined()
    expect(tree[0].children[0].children[0].value).toBe('华东组')
  })

  it('空输入返回空数组', () => {
    expect(capDepartmentTreeDepth(null)).toEqual([])
    expect(capDepartmentTreeDepth([])).toEqual([])
  })
})
