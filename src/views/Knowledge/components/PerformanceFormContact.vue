<template>
  <div>
    <el-row>
      <el-col :span="12">
        <el-form-item label="客户联系人" prop="contactPerson">
          <el-input v-model="form.contactPerson" placeholder="姓名（职务）" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="联系方式" prop="contactInfo">
          <el-input v-model="form.contactInfo" placeholder="手机/座机/邮箱" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item label="属地(省/市/区)" prop="territory">
      <el-input v-model="form.territory" placeholder="例如：四川省成都市武侯区" />
    </el-form-item>
    <el-form-item label="客户详细地址" prop="customerAddress">
      <el-input v-model="form.customerAddress" placeholder="详细的联系寄送地址" />
    </el-form-item>
    <el-form-item label="西域项目负责人" prop="xiyuProjectManager">
      <el-input v-model="form.xiyuProjectManager" placeholder="西域对接本项目的负责人" />
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
