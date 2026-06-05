<template>
  <div>
    <el-form-item label="合同名称" prop="contractName">
      <el-input v-model="form.contractName" placeholder="请录入完整的合同名称" />
    </el-form-item>
    <el-form-item label="签约单位" prop="signingEntity">
      <el-input v-model="form.signingEntity" placeholder="请输入签约单位全称" />
    </el-form-item>
    <el-form-item label="集团公司名称" prop="groupCompany">
      <el-input v-model="form.groupCompany" placeholder="请输入集团公司名称，如中铁/中建等" />
    </el-form-item>
    <el-row>
      <el-col :span="12">
        <el-form-item label="客户类型" prop="customerType">
          <el-select v-model="form.customerType" placeholder="请选择" style="width: 100%">
            <el-option label="政府机关/事业单位" value="GOVERNMENT_INSTITUTION" />
            <el-option label="央企" value="CENTRAL_SOE" />
            <el-option label="地方国企" value="LOCAL_SOE" />
            <el-option label="民企" value="PRIVATE_ENTERPRISE" />
            <el-option label="港澳台/外企" value="FOREIGN_HK_MACAO_TW" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="所属行业" prop="industry">
          <el-input v-model="form.industry" placeholder="请输入所属行业，例如：电子制造/电力/烟草" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-row>
      <el-col :span="12">
        <el-form-item label="项目类型" prop="projectType">
          <el-select v-model="form.projectType" placeholder="请选择" style="width: 100%">
            <el-option label="办公" value="OFFICE" />
            <el-option label="综合" value="COMPREHENSIVE" />
            <el-option label="集采" value="CENTRALIZED" />
            <el-option label="工业品" value="INDUSTRIAL" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="对接方式" prop="dockingMethod">
          <el-select v-model="form.dockingMethod" placeholder="请选择" style="width: 100%">
            <el-option label="Emall" value="EMALL" />
            <el-option label="Punch-out" value="PUNCH_OUT" />
            <el-option label="API" value="API" />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item label="客户级别" prop="customerLevel">
      <el-radio-group v-model="form.customerLevel">
        <el-radio value="GROUP">集团</el-radio>
        <el-radio value="SUBSIDIARY">二级单位</el-radio>
      </el-radio-group>
    </el-form-item>
  </div>
</template>

<script setup>
defineProps({
  form: {
    type: Object,
    required: true
  }
})
</script>
