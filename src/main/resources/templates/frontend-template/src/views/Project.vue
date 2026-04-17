<template>
  <div>
    <h2>项目生成</h2>
    <el-input
        v-model="prompt"
        type="textarea"
        :rows="5"
        placeholder="请输入你的项目需求，例如：生成一个SpringBoot+Vue的用户管理系统"
    />
    <div style="margin-top: 20px">
      <el-button type="primary" @click="generateProject">
        <el-icon><Download /></el-icon>
        一键生成项目
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'

const prompt = ref('')

const generateProject = () => {
  ElMessage.success('开始生成项目：' + prompt.value)
}
</script>