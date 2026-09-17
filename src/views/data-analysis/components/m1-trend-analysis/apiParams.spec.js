import { describe, expect, it } from 'vitest'
import {
  countTreeNodes,
  getUniqueTreeValues,
  isAllDepartmentsSelected,
  normalizeDepartmentFilter,
  isAllPersonsSelected,
  normalizePersonFilter,
  buildApiParams
} from './apiParams.js'

describe('m1-trend-analysis/apiParams 防 414 URL 超长参数处理', () => {
  const mockDeptTree = [
    {
      label: '西域集团',
      value: '西域集团',
      children: [
        {
          label: '华东大区',
          value: '华东大区',
          children: [
            { label: '上海业务一部', value: '上海业务一部' },
            { label: '上海业务二部', value: '上海业务二部' }
          ]
        },
        {
          label: '华北大区',
          value: '华北大区',
          children: [
            { label: '北京办事处', value: '北京办事处' }
          ]
        }
      ]
    }
  ]
  // 总节点数：西域集团(1) + 华东大区(1) + 上海业务一部(1) + 上海业务二部(1) + 华北大区(1) + 北京办事处(1) = 6
  const totalCount = 6

  it('countTreeNodes 应正确递归计算树节点总数', () => {
    expect(countTreeNodes(mockDeptTree)).toBe(totalCount)
    expect(countTreeNodes([])).toBe(0)
    expect(countTreeNodes(null)).toBe(0)
  })

  it('isAllDepartmentsSelected 能准确识别全选与部分选', () => {
    const allVals = ['西域集团', '华东大区', '上海业务一部', '上海业务二部', '华北大区', '北京办事处']
    expect(isAllDepartmentsSelected(allVals, mockDeptTree)).toBe(true)

    // 部分选择
    expect(isAllDepartmentsSelected(['华东大区', '上海业务一部'], mockDeptTree)).toBe(false)
    // 未选择
    expect(isAllDepartmentsSelected([], mockDeptTree)).toBe(false)
    expect(isAllDepartmentsSelected(null, mockDeptTree)).toBe(false)
    // 树为空
    expect(isAllDepartmentsSelected(allVals, [])).toBe(false)
  })

  it('normalizeDepartmentFilter: 全选时返回空数组以防 URL 超长 414', () => {
    const allVals = ['西域集团', '华东大区', '上海业务一部', '上海业务二部', '华北大区', '北京办事处']
    // 全选时返回空数组，向接口请求全量无需带参数
    expect(normalizeDepartmentFilter(allVals, mockDeptTree)).toEqual([])

    // 部分选择时原样返回
    expect(normalizeDepartmentFilter(['华东大区'], mockDeptTree)).toEqual(['华东大区'])

    // 空选择时返回空数组
    expect(normalizeDepartmentFilter([], mockDeptTree)).toEqual([])
  })

  it('buildApiParams: 部门全选时不传 departmentIds，彻底避免 414 报错', () => {
    const allVals = ['西域集团', '华东大区', '上海业务一部', '上海业务二部', '华北大区', '北京办事处']
    const filters = {
      timeDimension: 'month',
      departments: allVals,
      persons: ['张三'],
      regions: [],
      customerTypes: [],
      projectTypes: [],
      projectStatuses: [],
      tenderSubjects: [],
      competitors: []
    }

    const params = buildApiParams(filters, 'person', ['2026-01-01', '2026-03-31'], mockDeptTree)

    expect(params.xAxis).toBe('person')
    expect(params.startDate).toBe('2026-01-01')
    expect(params.endDate).toBe('2026-03-31')
    expect(params.userIds).toBe('张三')
    // 关键断言：全选时不得出现 departmentIds
    expect(params.departmentIds).toBeUndefined()
  })

  it('buildApiParams: 部门部分选择时正常拼接 departmentIds', () => {
    const filters = {
      departments: ['华东大区', '上海业务一部'],
      persons: []
    }

    const params = buildApiParams(filters, 'dept', null, mockDeptTree)
    expect(params.departmentIds).toBe('华东大区,上海业务一部')
  })

  it('下钻与联动场景：部门全选时正规化为空列表，确保 GET 请求 URL 不超长', () => {
    const allVals = ['西域集团', '华东大区', '上海业务一部', '上海业务二部', '华北大区', '北京办事处']
    // 模拟下钻场景中的部门正规化
    const drillDepts = normalizeDepartmentFilter(allVals, mockDeptTree)
    expect(drillDepts).toEqual([])

    // 模拟部门-人员联动场景：全选时返回空数组，后端据此直接查询全量人员
    const personQueryDepts = normalizeDepartmentFilter(allVals, mockDeptTree)
    expect(personQueryDepts).toEqual([])
  })

  it('真实生产场景：组织树存在重名节点时（唯一部门数 < 总节点数），全选仍能准确识别', () => {
    const treeWithDuplicates = [
      {
        label: '西域集团',
        value: '西域集团',
        children: [
          {
            label: '华东大区',
            value: '华东大区',
            children: [
              { label: '综合部', value: '综合部' },
              { label: '项目部', value: '项目部' }
            ]
          },
          {
            label: '华北大区',
            value: '华北大区',
            children: [
              { label: '综合部', value: '综合部' }, // 与华东大区同名
              { label: '北京办事处', value: '北京办事处' }
            ]
          }
        ]
      }
    ]

    // 树总节点数 7，但去重后的唯一部门名称只有 6 个
    expect(countTreeNodes(treeWithDuplicates)).toBe(7)
    const uniqueValues = getUniqueTreeValues(treeWithDuplicates)
    expect(uniqueValues.size).toBe(6)

    // 用户全选时收集去重后的 6 个部门名称
    const selected = Array.from(uniqueValues)
    expect(selected.length).toBe(6)

    // 核心断言：必须准确识别为全选
    expect(isAllDepartmentsSelected(selected, treeWithDuplicates)).toBe(true)
    expect(normalizeDepartmentFilter(selected, treeWithDuplicates)).toEqual([])

    // 少选一项时不是全选
    expect(isAllDepartmentsSelected(selected.slice(1), treeWithDuplicates)).toBe(false)
    expect(normalizeDepartmentFilter(selected.slice(1), treeWithDuplicates)).toEqual(selected.slice(1))
  })

  it('人员全选正规化：全选人员时返回空数组，向接口请求全量无需传 userIds', () => {
    const mockPersonOptions = Array.from({ length: 50 }, (_, i) => ({ label: `员工${i}`, value: `员工${i}` }))
    const allPersons = mockPersonOptions.map(p => p.value)

    expect(isAllPersonsSelected(allPersons, mockPersonOptions)).toBe(true)
    expect(normalizePersonFilter(allPersons, mockPersonOptions)).toEqual([])

    // 部分选择
    expect(isAllPersonsSelected(['员工0', '员工1'], mockPersonOptions)).toBe(false)
    expect(normalizePersonFilter(['员工0', '员工1'], mockPersonOptions)).toEqual(['员工0', '员工1'])

    // buildApiParams 全选人员时不得包含 userIds
    const filters = {
      departments: [],
      persons: allPersons
    }
    const params = buildApiParams(filters, 'person', null, [], mockPersonOptions)
    expect(params.userIds).toBeUndefined()
  })
})

