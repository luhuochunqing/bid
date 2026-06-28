// Input: 总部所在地字符串（getter）+ 字符串写入器（setter）+ emptyValue 选项
// Output: el-cascader v-model 双向绑定 computed（路径 ↔ 归一字符串）
// Pos: src/composables/ - 总部所在地 cascader 通用绑定（被 ManualTenderDialog / TenderBasicInfoTab / TenderSearchCard 共 3 处复用）
// 维护声明: 若 cascader 组件或 chinaRegionData.js 工具函数接口变化，需同步更新本文件。

import { computed, ref } from 'vue'
import {
  normalizeHeadquartersRegionPath,
  regionValueToCascaderPath,
} from '@/components/common/chinaRegionData.js'

/**
 * 总部所在地（region）字符串与 el-cascader 选中路径之间的双向绑定。
 *
 * 用法示例：
 *   const regionCascaderValue = useRegionCascaderValue(
 *     () => props.form.region,
 *     (v) => { props.form.region = v },
 *     { emptyValue: '' }, // 搜索过滤场景传 null
 *   )
 *
 * - get: 字符串 → cascader path（via regionValueToCascaderPath，兼容历史"省+市" / 直辖市带区 / 纯省名）
 * - set: cascader path → 归一字符串（via normalizeHeadquartersRegionPath，直辖市/港澳台仅存本级行政区名）
 * - 空值时写入 emptyValue（默认 ''），用于 reset cascader
 *
 * 满足 RULES.md §2.6.1 提取条件（3 处引用、>80 行价值、消 39 行重复）。
 * 命名函数 export（避免 §2.6.2 Vite 异步 chunk 内联 + tree-shaking 截断函数体的问题）。

 * 配套导出 REGION_CASCADER_PROPS：3 处调用方共享同一 cascader 配置，
 * 避免 `:props="{ expandTrigger: 'hover', ... }"` 在模板里重复 3 次。
 */
export const REGION_CASCADER_PROPS = Object.freeze({
  expandTrigger: 'hover',
  label: 'name',
  value: 'name',
  checkStrictly: true,
  emitPath: true,
})

export function useRegionCascaderValue(getter, setter, options = {}) {
  const emptyValue = 'emptyValue' in options ? options.emptyValue : ''

  return computed({
    get: () => regionValueToCascaderPath(readValue(getter)),
    set: (val) => {
      if (!val) {
        setter(emptyValue)
        return
      }
      if (Array.isArray(val)) {
        setter(normalizeHeadquartersRegionPath(val))
      } else {
        setter(val)
      }
    },
  })
}

/**
 * CO-381: el-cascader 在 checkStrictly: true 下选中市级后自动关闭下拉框。
 *
 * 根因：checkStrictly 模式下，选中节点后 Element Plus 会同步调用 doExpand()
 * 展开下一级菜单（node.vue2.mjs handleSelectCheck），并触发 expandChange →
 * updatePopperPosition，导致 popper 保持可见。用 nextTick（微任务）关闭会被
 * 同步的 doExpand 覆盖；改用 setTimeout(0)（宏任务）确保在 doExpand 之后执行。
 *
 * 用法：
 *   const cascaderRef = ref(null)
 *   const onRegionChange = createRegionCascaderAutoClose(cascaderRef)
 *   <el-cascader ref="cascaderRef" @change="onRegionChange" />
 *
 * @param {import('vue').Ref} cascaderRef - el-cascader 组件实例的 ref
 * @returns {(val: any) => void} 绑定到 @change 的回调
 */
export function createRegionCascaderAutoClose(cascaderRef) {
  return (val) => {
    // 选中市级（路径长度 >= 2）或直辖市区级（路径长度 3）后关闭
    if (!Array.isArray(val) || val.length < 2) return
    setTimeout(() => {
      cascaderRef.value?.togglePopperVisible?.(false)
    }, 0)
  }
}

function readValue(getter) {
  // getter 由调用方提供，返回 string 或 null/undefined（未选）；统一归一为 string
  return getter() ?? ''
}
