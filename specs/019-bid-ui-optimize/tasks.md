# 任务清单：标讯UI操作按钮一致性优化

## Task 1: actionMatrix.js 纯核心增加创建人感知

**文件**: `src/views/Bidding/detail/actionMatrix.js`

**子任务**:

1.1 `HEADER_MATRIX.PENDING_ASSIGNMENT.sales` 改为函数 `({ currentUserId, creatorId }) => currentUserId === creatorId ? ['edit', 'delete'] : []`
1.2 `HEADER_MATRIX.PENDING_ASSIGNMENT.bid_specialist` 同上
1.3 `getHeaderActions` 新增 `currentUserId` 和 `creatorId` 参数，在 `keys` 为函数时调用之
1.4 `getBottomActions` 同理新增参数（预留，当前 BOTTOM_MATRIX 暂不需创建人感知）
1.5 `getHeaderActions` 保持向后兼容（旧签名用 `undefined` 做默认值）

**验证**: `npx vitest run src/views/Bidding/detail/actionMatrix.spec.js`

---

## Task 2: useDetailActions.js 透传 creatorId

**文件**: `src/views/Bidding/detail/useDetailActions.js`

**子任务**:

2.1 `useDetailActions` 签名新增 `currentUserId` 和 `creatorId` 参数
2.2 `headerActions` computed 调用 `getHeaderActions` 时传入 `currentUserId` 和 `creatorId`
2.3 `bottomActions` computed 调用 `getBottomActions` 时同理传入

**验证**: `npx vitest run src/views/Bidding/detail/useDetailActions.spec.js`

---

## Task 3: DetailPage.vue 传入创建人ID

**文件**: `src/views/Bidding/detail/DetailPage.vue`

**子任务**:

3.1 从 `userStore` 和 `tender` 获取 `currentUserId` 和 `creatorId`
3.2 调用 `useDetailActions` 时传入

**验证**: 手动确认或 E2E 测试

---

## Task 4: TenderCreatePage.vue 修复底部按钮

**文件**: `src/views/Bidding/TenderCreatePage.vue`

**子任务**:

4.1 `canProceedToNext` 中移除 `if (isAdminOrLead.value) return true`
4.2 仅保留 `projectLeaderId && currentUserId === projectLeaderId` 的判断
4.3 确保非 admin_lead/sales 角色的创建人在 TRACKING 状态下不显示下一步/提交

**验证**: `npx vitest run src/views/Bidding/TenderCreatePage.spec.js`

---

## Task 5: TenderCreatePage.vue 保存后跳转详情页

**文件**: `src/views/Bidding/TenderCreatePage.vue`

**子任务**:

5.1 `handleSave` 成功后调用 `router.push('/bidding/' + createdTenderId)` 而非停留在创建页
5.2 确保编辑模式（isEditMode）下保存后也跳转详情页

---

## Task 6: 更新 actionMatrix 测试用例

**文件**: `src/views/Bidding/detail/actionMatrix.spec.js`

**子任务**:

6.1 新增创建人场景测试组 `getHeaderActions with creator context`
6.2 测试 sales/specialist 作为创建人在 PENDING_ASSIGNMENT 下看到编辑+删除
6.3 测试 sales 非创建人在 PENDING_ASSIGNMENT 下无按钮
6.4 测试 admin/lead 不受 creatorId 影响

**验证**: `npx vitest run src/views/Bidding/detail/actionMatrix.spec.js` 全部通过
