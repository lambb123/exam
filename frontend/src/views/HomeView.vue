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

        <div class="db-switcher-sidebar" v-if="user.role === 'ADMIN'">
          <div class="switcher-title">当前主写入库</div>
          <el-select
            v-model="dbStore.currentDbMode"
            @change="handleDbChange"
            class="db-select"
            placeholder="选择主库"
            size="default"
          >
            <template #prefix>
              <el-icon><Coin /></el-icon>
            </template>
            <el-option label="MySQL (默认)" value="MySQL" />
            <el-option label="Oracle (容灾)" value="Oracle" />
            <el-option label="SQL Server" value="SQLServer" />
          </el-select>
          <div class="status-dot">
            <span :class="['dot', dbStore.currentDbMode.toLowerCase()]"></span>
            {{ dbStore.currentDbMode }} Online
          </div>
        </div>

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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Odometer, School, User, Warning, Reading, DocumentCopy, Files,
  EditPen, DataAnalysis, Trophy, Monitor, DataLine, List, Fold,
  Notebook, TrendCharts, Coin // 引入新图标
} from '@element-plus/icons-vue'
import { useDbStore } from '@/stores/dbStore' // 引入 Store

const router = useRouter()
const dbStore = useDbStore()
const user = JSON.parse(localStorage.getItem('user') || '{}')

const logout = () => {
  localStorage.removeItem('user')
  router.push('/login')
}

const getRoleName = (role: string) => {
  const map: Record<string, string> = {
    ADMIN: '管理员',
    TEACHER: '教师',
    STUDENT: '学生'
  }
  return map[role] || role
}

// 处理切换
const handleDbChange = (val: string) => {
  dbStore.switchDbMode(val)
}

// 初始化加载状态
onMounted(() => {
  if (user.role === 'ADMIN') {
    dbStore.fetchDbMode()
  }
})
</script>

<style scoped>
.common-layout { height: 100vh; display: flex; }
.el-container { height: 100%; width: 100%; }

/* 侧边栏样式调整 */
.aside-menu {
  background-color: #304156;
  color: white;
  transition: width 0.3s;
  overflow-x: hidden;
  /* 【关键】改为 Flex 布局以支持底部固定 */
  display: flex;
  flex-direction: column;
}

.logo-box {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b2f3a;
  flex-shrink: 0; /* 防止Logo被压缩 */
}
.logo { width: 30px; margin-right: 10px; }
.title { font-weight: bold; font-size: 16px; color: white; }

.el-menu-vertical {
  border-right: none;
  /* 【关键】占据剩余空间，将底部开关推到底 */
  flex: 1;
  overflow-y: auto; /* 菜单内容多时滚动 */
}

/* 底部切换器样式 */
.db-switcher-sidebar {
  padding: 15px;
  background-color: #263445;
  border-top: 1px solid #1f2d3d;
  flex-shrink: 0;
}

.switcher-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  text-align: center;
}

.db-select {
  width: 100%;
}
/* 调整 Select 在深色背景下的显示 */
:deep(.el-input__wrapper) {
  background-color: #1f2d3d !important;
  box-shadow: none !important;
  border: 1px solid #3d4e60 !important;
}
:deep(.el-input__inner) {
  color: #fff !important;
}

.status-dot {
  margin-top: 10px;
  font-size: 12px;
  color: #e6a23c;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  background-color: #909399;
}
.dot.mysql { background-color: #409EFF; }
.dot.oracle { background-color: #F56C6C; }
.dot.sqlserver { background-color: #67C23A; }

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

.main-content { background-color: #f0f2f5; padding: 20px; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
