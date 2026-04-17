<template>
  <el-container style="height: 100vh">
    <el-header style="text-align: right; font-size: 12px">
      <el-dropdown>
        <i class="el-icon-user"></i>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item>退出</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>
    <el-container>
      <el-aside width="200px">
        <el-menu
            router
            default-active="/"
            background-color="#2f4050"
            text-color="#fff"
            active-text-color="#ffd04b"
        >
          <el-menu-item index="/">
            <el-icon><HomeFilled /></el-icon>
            <span>首页</span>
          </el-menu-item>
          <el-menu-item index="/project">
            <el-icon><FolderOpened /></el-icon>
            <span>项目生成</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { HomeFilled, FolderOpened } from '@element-plus/icons-vue'
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
html, body {
  height: 100%;
}
</style>