<template>
  <el-dialog v-model="ctx.resultDialogVisible" title="投标结果录入" width="750px"><el-form :model="ctx.resultForm" label-width="120px"><el-form-item label="投标结果"><el-radio-group v-model="ctx.resultForm.result"><el-radio value="won">中标</el-radio><el-radio value="lost">未中标</el-radio></el-radio-group></el-form-item><el-form-item v-if="ctx.resultForm.result === 'won'" label="中标金额"><el-input-number v-model="ctx.resultForm.amount" :min="0" :precision="2" :max="99999" /></el-form-item><el-form-item label="竞争对手信息"><el-table :data="ctx.resultForm.competitors" size="small" border style="width: 100%;"><el-table-column prop="name" label="公司名称" width="140" /><el-table-column prop="skuCount" label="SKU数量" width="100" /><el-table-column prop="category" label="品类" width="120" /><el-table-column prop="discount" label="折扣" width="90" /><el-table-column prop="payment" label="账期" width="90" /><el-table-column label="操作" width="70"><template #default="{ $index }"><el-button link type="danger" size="small" :icon="Delete" @click="ctx.removeCompetitor($index)">删除</el-button></template></el-table-column></el-table><el-button type="primary" :icon="Plus" size="small" plain class="top-gap" @click="ctx.addCompetitor">添加竞争对手</el-button></el-form-item><el-form-item label="技术亮点"><el-input v-model="ctx.resultForm.techHighlights" type="textarea" :rows="3" /></el-form-item><el-form-item label="报价策略"><el-input v-model="ctx.resultForm.priceStrategy" type="textarea" :rows="3" /></el-form-item><el-form-item label="客户反馈"><el-input v-model="ctx.resultForm.customerFeedback" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="ctx.resultDialogVisible = false">取消</el-button><el-button type="primary" @click="ctx.handleSaveResult">提交结果</el-button></template></el-dialog>
  <el-dialog v-model="ctx.competitorDialogVisible" title="添加竞争对手" width="500px"><el-form :model="ctx.competitorForm" label-width="100px"><el-form-item label="公司名称" required><el-input v-model="ctx.competitorForm.name" /></el-form-item><el-form-item label="SKU数量"><el-input v-model="ctx.competitorForm.skuCount" /></el-form-item><el-form-item label="品类"><el-input v-model="ctx.competitorForm.category" /></el-form-item><el-form-item label="折扣"><el-input v-model="ctx.competitorForm.discount" /></el-form-item><el-form-item label="账期"><el-input v-model="ctx.competitorForm.payment" /></el-form-item></el-form><template #footer><el-button @click="ctx.competitorDialogVisible = false">取消</el-button><el-button type="primary" @click="ctx.confirmAddCompetitor">确定</el-button></template></el-dialog>
</template>

<script setup>
import { Delete, Plus } from '@element-plus/icons-vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'

const ctx = useProjectDetailContext()
</script>

<style scoped>
.top-gap { margin-top: 8px; }
</style>
