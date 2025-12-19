<template>
  <div class="common-layout">
    <el-container>
      <el-aside width="220px" class="aside-menu">
        <div class="logo-box">
          <img src="@/assets/logo.svg" alt="Logo" class="logo" />
          <span class="title">考试同步系统</span>
        </div>

        <el-menu
          :default-active="$route.path"
          class="el-menu-vertical"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          router
          unique-opened
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>

          <el-sub-menu index="2" v-if="user.role === 'ADMIN'">
            <template #title>
              <el-icon><School /></el-icon>
              <span>教务管理</span>
            </template>
            <el-menu-item index="/education">
              <el-icon><User /></el-icon>
              <span>人员管理</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="3">
            <template #title>
              <el-icon><Reading /></el-icon>
              <span>考试中心</span>
            </template>

            <el-menu-item index="/question/list" v-if="['ADMIN', 'TEACHER'].includes(user.role)">
              <el-icon><DocumentCopy /></el-icon>
              <span>题库管理</span>
            </el-menu-item>

            <el-menu-item index="/paper/list" v-if="['ADMIN', 'TEACHER'].includes(user.role)">
              <el-icon><Files /></el-icon>
              <span>试卷管理</span>
            </el-menu-item>

            <el-menu-item index="/exam/list" v-if="user.role === 'STUDENT'">
              <el-icon><EditPen /></el-icon>
              <span>在线考试</span>
            </el-menu-item>

            <el-menu-item index="/score/manage" v-if="['ADMIN', 'TEACHER'].includes(user.role)">
              <el-icon><DataAnalysis /></el-icon>
              <span>成绩管理</span>
            </el-menu-item>

            <el-menu-item index="/score/my" v-if="user.role === 'STUDENT'">
              <el-icon><Trophy /></el-icon>
              <span>我的成绩</span>
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="4" v-if="user.role === 'ADMIN'">
            <template #title>
              <el-icon><Monitor /></el-icon>
              <span>系统监控</span>
            </template>
            <el-menu-item index="/monitor">
              <el-icon><DataLine /></el-icon>
              <span>同步监控</span>
            </el-menu-item>
            <el-menu-item index="/logs">
              <el-icon><List /></el-icon>
              <span>日志记录</span>
            </el-menu-item>
            <el-menu-item index="/conflict">
              <el-icon><Warning /></el-icon>
              <span>冲突处理</span>
            </el-menu-item>
            <el-menu-item index="/analysis">
              <el-icon><TrendCharts /></el-icon>
              <span>复杂统计分析</span>
            </el-menu-item>
            <el-menu-item index="/dictionary">
              <el-icon><Notebook /></el-icon>
              <span>数据字典</span>
            </el-menu-item>
          </el-sub-menu>

        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="header">
          <div class="breadcrumb">
            <el-icon class="fold-btn"><Fold /></el-icon>
            <span>当前位置：{{ $route.meta.title || '首页' }}</span>
          </div>
          <div class="user-info">
            <el-avatar :size="32" :src="'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
            <span class="username">
              {{ user.realName || user.username }}
              <el-tag size="small" effect="plain" style="margin-left:5px">
                {{ getRoleName(user.role) }}
              </el-tag>
            </span>
            <el-button type="danger" link size="small" @click="logout" style="margin-left: 15px;">退出</el-button>
          </div>
        </el-header>

        <el-main class="main-content">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Odometer, School, User, Warning, Reading, DocumentCopy, Files,
  EditPen, DataAnalysis, Trophy, Monitor, DataLine, List, Fold
} from '@element-plus/icons-vue'

const router = useRouter()
// 获取用户信息，如果没有则默认为空对象
const user = JSON.parse(localStorage.getItem('user') || '{}')

const logout = () => {
  localStorage.removeItem('user')
  router.push('/login')
}

// 辅助函数：显示中文角色名
const getRoleName = (role: string) => {
  const map: Record<string, string> = {
    ADMIN: '管理员',
    TEACHER: '教师',
    STUDENT: '学生'
  }
  return map[role] || role
}
</script>

<style scoped>
.common-layout { height: 100vh; display: flex; }
.el-container { height: 100%; width: 100%; }

/* 侧边栏样式 */
.aside-menu {
  background-color: #304156;
  color: white;
  transition: width 0.3s;
  overflow-x: hidden;
}
.logo-box {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b2f3a;
}
.logo { width: 30px; margin-right: 10px; }
.title { font-weight: bold; font-size: 16px; color: white; }
.el-menu-vertical { border-right: none; }

/* 头部样式 */
.header {
  background-color: white;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 60px;
}
.breadcrumb { display: flex; align-items: center; color: #606266; }
.fold-btn { margin-right: 10px; font-size: 20px; cursor: pointer; }
.user-info { display: flex; align-items: center; }
.username { margin-left: 8px; font-size: 14px; color: #303133; display: flex; align-items: center;}

/* 内容区动画 */
.main-content { background-color: #f0f2f5; padding: 20px; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
