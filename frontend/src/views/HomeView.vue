<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span>🎓 考试系统</span>
      </div>
      <el-menu
        active-text-color="#409EFF"
        background-color="#304156"
        text-color="#bfcbd9"
        :default-active="activePath"
        router
        class="el-menu-vertical"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>首页仪表盘</span>
        </el-menu-item>

        <el-sub-menu index="1" v-if="userRole !== 'STUDENT'">
          <template #title>
            <el-icon><DocumentCopy /></el-icon>
            <span>题库管理</span>
          </template>
          <el-menu-item index="/question/list">试题列表</el-menu-item>
          <el-menu-item index="/question/add">添加试题</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="2" v-if="userRole !== 'STUDENT'">
          <template #title>
            <el-icon><Files /></el-icon>
            <span>试卷管理</span>
          </template>
          <el-menu-item index="/paper/list">试卷列表</el-menu-item>
          <el-menu-item index="/paper/create">智能组卷</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/exam/list" v-if="userRole === 'STUDENT'">
          <el-icon><EditPen /></el-icon>
          <span>在线考试</span>
        </el-menu-item>

        <el-menu-item index="/score/my" v-if="userRole === 'STUDENT'">
          <el-icon><Trophy /></el-icon>
          <span>我的成绩</span>
        </el-menu-item>

        <el-menu-item index="/score/manage" v-if="userRole !== 'STUDENT'">
          <el-icon><TrendCharts /></el-icon>
          <span>成绩分析</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="breadcrumb">
          欢迎使用考试组卷管理系统
        </div>
        <div class="user-info">
          <span class="username">{{ user.realName || user.username }}</span>
          <el-tag size="small" effect="dark" class="role-tag">{{ roleName }}</el-tag>
          <el-button type="danger" link size="small" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Odometer, DocumentCopy, Files, EditPen, Trophy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()

const user = ref<any>({})
const activePath = computed(() => route.path)

// 从缓存读取用户信息
onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    user.value = JSON.parse(userStr)
  } else {
    router.push('/login')
  }
})

const userRole = computed(() => user.value.role)

const roleName = computed(() => {
  const map: any = { 'ADMIN': '管理员', 'TEACHER': '教师', 'STUDENT': '学生' }
  return map[user.value.role] || '未知'
})

const handleLogout = () => {
  localStorage.removeItem('user')
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout-container { height: 100vh; }
.aside { background-color: #304156; color: #fff; }
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 20px;
  font-weight: bold;
  background-color: #2b2f3a;
  color: #fff;
}
.el-menu-vertical { border-right: none; }
.header {
  background-color: #fff;
  border-bottom: 1px solid #ddd;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}
.user-info { display: flex; align-items: center; gap: 10px; }
.role-tag { margin-left: 5px; }
.main-content { background-color: #f0f2f5; padding: 20px; }
</style>
