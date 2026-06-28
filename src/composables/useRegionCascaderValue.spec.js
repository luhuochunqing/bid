// Input: useRegionCascaderValue(getter, setter, options) 双向绑定 composable
// Output: 单测覆盖归一、空值、字符串/数组输入、emptyValue 选项
// Pos: src/composables/ - 总部所在地 cascader 通用绑定测试

import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useRegionCascaderValue, REGION_CASCADER_PROPS } from './useRegionCascaderValue.js'

describe('REGION_CASCADER_PROPS', () => {
  it('是冻结对象，含总部所在地 cascader 标准配置', () => {
    expect(Object.isFrozen(REGION_CASCADER_PROPS)).toBe(true)
    expect(REGION_CASCADER_PROPS).toEqual({
      expandTrigger: 'hover',
      label: 'name',
      value: 'name',
      checkStrictly: true,
      emitPath: true,
    })
  })
})

describe('useRegionCascaderValue', () => {
  it('get: 字符串 → cascader 路径', () => {
    const r = ref('广东省深圳市')
    const v = useRegionCascaderValue(() => r.value, (x) => { r.value = x })
    expect(v.value).toEqual(['广东省', '深圳市'])
  })

  it('get: 直辖市单名 → [市名]', () => {
    const r = ref('北京市')
    const v = useRegionCascaderValue(() => r.value, (x) => { r.value = x })
    expect(v.value).toEqual(['北京市'])
  })

  it('get: 直辖市带区历史值 → 回退 [市名]', () => {
    const r = ref('北京市东城区')
    const v = useRegionCascaderValue(() => r.value, (x) => { r.value = x })
    expect(v.value).toEqual(['北京市'])
  })

  it('set: cascader 路径 → 归一字符串', () => {
    const r = ref('')
    const v = useRegionCascaderValue(() => r.value, (x) => { r.value = x })
    v.value = ['广东省', '深圳市']
    expect(r.value).toBe('广东省深圳市')
  })

  it('set: 直辖市路径 → 一级+二级格式（CO-381）', () => {
    const r = ref('')
    const v = useRegionCascaderValue(() => r.value, (x) => { r.value = x })
    v.value = ['北京市', '北京市']
    expect(r.value).toBe('北京市北京市')
  })

  it('set: 空值/清空 → 写入 emptyValue（默认 ""）', () => {
    const r = ref('北京市')
    const v = useRegionCascaderValue(() => r.value, (x) => { r.value = x })
    v.value = null
    expect(r.value).toBe('')
    v.value = []
    expect(r.value).toBe('')
  })

  it('set: emptyValue 选项为 null（搜索场景）', () => {
    const r = ref('北京市')
    const v = useRegionCascaderValue(() => r.value, (x) => { r.value = x }, { emptyValue: null })
    v.value = null
    expect(r.value).toBeNull()
  })

  it('set: 字符串透传（异常输入容错）', () => {
    const r = ref('')
    const v = useRegionCascaderValue(() => r.value, (x) => { r.value = x })
    v.value = '北京市'
    expect(r.value).toBe('北京市')
  })

  it('get: 空字符串 → null（cascader 清空）', () => {
    const r = ref('')
    const v = useRegionCascaderValue(() => r.value, (x) => { r.value = x })
    expect(v.value).toBeNull()
  })
})
