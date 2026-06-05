一旦我所属的文件夹有所变化，请更新我。

# qualification components

资质知识库页面的组件和组合逻辑。页面仍走真实后端 API，资质借阅申请入口通过流程表单中心提交 `QUALIFICATION_BORROW` 表单实例，再由后端触发 OA。

## 文件清单

| 文件 | 功能 |
|------|------|
| `QualificationBorrowDialog.vue` | 资质借阅动态表单弹窗 |
| `useQualificationBorrowWorkflow.js` | OA 流程表单借阅申请的前端编排 |
| `useQualificationPage.js` | 资质页列表、上传、归还、下载等页面状态和事件 |
