/**
 * 共享的 ElDialog stub：捕获 v-model 传入的 modelValue，转成 data-model-value 属性，
 * 用于回归测试 v-model 是否正确绑定 ref 字段（reactive unwrap 行为）。
 *
 * 历史 bug：模板里写 v-model="ctx.xxx.value" 在 reactive unwrap 后失效，dialog 永不弹出。
 * 该 stub 让测试可以通过 [data-model-value] 断言 dialog 的可见状态。
 *
 * 用法：
 *   import { ElDialogStub } from './__test-utils__/ElDialogStub.js'
 *   stubs: { ElDialog: ElDialogStub }
 */
export const ElDialogStub = {
  name: 'ElDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
  },
  template: `
    <div :data-title="title" :data-model-value="String(modelValue)">
      <slot />
      <slot name="footer" />
    </div>
  `,
}
