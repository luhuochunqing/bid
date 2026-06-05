<!-- 蓝图 §3.3.1.1 客户信息表格（15列 × 14行） -->
<template>
  <el-card class="section-card" shadow="never">
    <template #header><span>客户信息</span></template>
    <div class="customer-table-wrapper">
      <el-table :data="rows" border style="min-width:2800px" height="500">
        <el-table-column label="客户角色" width="160" fixed="left">
          <template #default="{row}"><span class="role-label">{{ row.role }}</span></template>
        </el-table-column>
        <el-table-column label="姓名" width="100">
          <template #default="{row}"><el-input v-model="row.name" size="small" :disabled="disabled" /></template>
        </el-table-column>
        <el-table-column label="职位（集团/二级公司/电商公司）" width="180">
          <template #default="{row}"><el-input v-model="row.position" size="small" :disabled="disabled" /></template>
        </el-table-column>
        <el-table-column label="西域项目负责人" width="140">
          <template #default="{row}"><el-input v-model="row.xiyuContact" size="small" :disabled="disabled" /></template>
        </el-table-column>
        <el-table-column label="是否触达" width="200">
          <template #default="{row}"><el-input v-model="row.reached" size="small" :disabled="disabled" placeholder="有联系方式/协会活动中交流过等" /></template>
        </el-table-column>
        <el-table-column label="触达方式" width="120">
          <template #default="{row}"><el-input v-model="row.reachMethod" size="small" :disabled="disabled" /></template>
        </el-table-column>
        <el-table-column label="对我司的倾向性" width="140">
          <template #default="{row}">
            <el-select v-model="row.preference" size="small" :disabled="disabled">
              <el-option label="支持" value="SUPPORT" /><el-option label="中立" value="NEUTRAL" /><el-option label="反对" value="OPPOSE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="倾向性评估依据" width="160">
          <template #default="{row}"><el-input v-model="row.preferenceBasis" size="small" :disabled="disabled" /></template>
        </el-table-column>
        <el-table-column label="是否有正式的高层交流" width="100">
          <template #default="{row}">
            <el-select v-model="row.hasHighLevelMeeting" size="small" :disabled="disabled"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="是否向此人引导标书" width="100">
          <template #default="{row}">
            <el-select v-model="row.guideBid" size="small" :disabled="disabled"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="是否可以通过此人获取标书关键信息" width="120">
          <template #default="{row}">
            <el-select v-model="row.canGetKeyInfo" size="small" :disabled="disabled"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="是否可以通过此人将标书中对我司不利项删除" width="140">
          <template #default="{row}">
            <el-select v-model="row.canRemoveAdverse" size="small" :disabled="disabled"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="是否为重点攻克对象" width="100">
          <template #default="{row}">
            <el-select v-model="row.isKeyTarget" size="small" :disabled="disabled"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="是否可以在评标期间实时同步评标信息" width="140">
          <template #default="{row}">
            <el-select v-model="row.canSyncEval" size="small" :disabled="disabled"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="是否给出明确我司可以中标的信息" width="140">
          <template #default="{row}">
            <el-select v-model="row.canConfirmWin" size="small" :disabled="disabled"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>
<script setup>
defineProps({
  rows: { type: Array, required: true },
  disabled: { type: Boolean, default: false }
})
</script>
<style scoped>
.customer-table-wrapper { overflow-x: auto; }
</style>
