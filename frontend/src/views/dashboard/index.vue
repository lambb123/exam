<template>
  <div class="dashboard-container">
    <div class="welcome-card">
      <div class="welcome-text">
        <h2>{{ timeGreetings }}，{{ userInfo.realName || userInfo.username }}</h2>
        <p>{{ welcomeMessage }}</p>
      </div>
      <div class="welcome-img">
        <el-icon :size="80" color="#e6f7ff">
          <component :is="roleIcon" />
        </el-icon>
      </div>
    </div>

    <el-row :gutter="20" class="stat-row" v-if="userRole !== 'student'">
      <el-col :xs="24" :sm="12" :md="6" v-for="(item, index) in statCards" :key="index">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '20px' }">
          <div class="stat-icon" :style="{ background: item.color }">
            <component :is="item.icon" />
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="section-title" style="margin-top: 10px;">
      {{ userRole === 'student' ? '考试及格小贴士' : '系统概况' }}
    </div>
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card shadow="never" class="info-card">
          <div v-if="userRole === 'student'" class="student-tips">
            <p>1. 考试期间请保持网络畅通，切勿频繁刷新页面。</p>
            <p>2. 交卷后请及时在“我的成绩”中查看结果。</p>
            <p>3. 遇到系统问题，请及时联系管理员或任课老师。</p>
          </div>
          <el-descriptions v-else title="平台运行参数" :column="3" border>
            <el-descriptions-item label="系统状态">
              <el-tag type="success">运行中</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="数据库架构">MySQL + Oracle + SQLServer</el-descriptions-item>
            <el-descriptions-item label="当前版本">V1.0.0 (Release)</el-descriptions-item>
            <el-descriptions-item label="同步策略">实时触发 + 定时兜底</el-descriptions-item>
            <el-descriptions-item label="登录角色">{{ roleName }}</el-descriptions-item>
            <el-descriptions-item label="技术栈">Vue3 + SpringBoot</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getDashboardStats } from '@/api/dashboard'
import {
  User, Document, Files,
  DataAnalysis, Edit, Trophy, Reading
} from '@element-plus/icons-vue'

// === 1. 获取用户信息与角色 ===
const userInfo = ref<any>({})
const userRole = ref<string>('student') // 默认 student，防止权限泄露

const initUser = () => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      userInfo.value = JSON.parse(userStr)
      userRole.value = (userInfo.value.role || 'student').toLowerCase()
    } catch (e) {
      console.error('用户信息解析失败', e)
    }
  }
}

// === 2. 动态文案配置 ===
const timeGreetings = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const roleName = computed(() => {
  const map: Record<string, string> = { admin: '系统管理员', teacher: '教师', student: '学生' }
  return map[userRole.value] || '访客'
})

const welcomeMessage = computed(() => {
  const map: Record<string, string> = {
    admin: '欢迎回到控制台。系统运行平稳，所有数据库节点连接正常。',
    teacher: '传道授业解惑，辛苦了！您可以前往“题库管理”或“发布试卷”。',
    student: '学而不思则罔，思而不学则殆。准备好开始今天的考试了吗？'
  }
  return map[userRole.value] || '欢迎使用考试系统。'
})

const roleIcon = computed(() => {
  const map: any = { admin: DataAnalysis, teacher: Reading, student: Trophy }
  return map[userRole.value] || User
})

// === 3. 统计数据 (后端接口获取) ===
const stats = ref({
  userCount: 0,
  paperCount: 0,
  questionCount: 0,
  examCount: 0
})

// === 4. 动态卡片配置 (核心逻辑) ===
const statCards = computed(() => {
  // 管理员看全局
  if (userRole.value === 'admin') {
    return [
      { label: '注册用户', value: stats.value.userCount, icon: User, color: 'linear-gradient(135deg, #69c0ff 0%, #1890ff 100%)' },
      { label: '试卷总数', value: stats.value.paperCount, icon: Files, color: 'linear-gradient(135deg, #95de64 0%, #52c41a 100%)' },
      { label: '题库总量', value: stats.value.questionCount, icon: Document, color: 'linear-gradient(135deg, #ffc069 0%, #fa8c16 100%)' },
      { label: '考试场次', value: stats.value.examCount, icon: Trophy, color: 'linear-gradient(135deg, #ff85c0 0%, #eb2f96 100%)' }
    ]
  }
  // 教师看教学相关 (复用全局数据，或后续改成教师专属数据)
  if (userRole.value === 'teacher') {
    return [
      { label: '试卷库', value: stats.value.paperCount, icon: Files, color: 'linear-gradient(135deg, #95de64 0%, #52c41a 100%)' },
      { label: '题库总量', value: stats.value.questionCount, icon: Document, color: 'linear-gradient(135deg, #ffc069 0%, #fa8c16 100%)' },
      { label: '待批改', value: 0, icon: Edit, color: 'linear-gradient(135deg, #ff85c0 0%, #eb2f96 100%)' },
      { label: '学生总数', value: stats.value.userCount, icon: User, color: 'linear-gradient(135deg, #69c0ff 0%, #1890ff 100%)' }
    ]
  }
  return [] // 学生不显示顶部统计卡片
})

const loadData = async () => {
  // 只有管理员和教师需要加载统计数据
  if (userRole.value !== 'student') {
    try {
      const res: any = await getDashboardStats()
      if (res.code === 200) {
        stats.value = res.data
      }
    } catch (e) {
      console.error(e)
    }
  }
}

onMounted(() => {
  initUser()
  loadData()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

/* 1. 欢迎卡片 */
.welcome-card {
  background: white;
  padding: 30px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 25px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
  background-image: linear-gradient(to right, #ffffff, #f0f9ff);
}
.welcome-text h2 { margin: 0 0 10px 0; font-size: 24px; color: #303133; }
.welcome-text p { margin: 0; color: #606266; font-size: 14px; max-width: 600px; line-height: 1.6; }
.welcome-img {
  background: linear-gradient(135deg, #409EFF, #69c0ff);
  width: 80px; height: 80px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 15px rgba(64, 158, 255, 0.3);
}

/* 2. 统计卡片 */
.stat-row { margin-bottom: 25px; }
.stat-card {
  border: none;
  border-radius: 12px;
  transition: all 0.3s;
  cursor: default;
}
.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}
.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
}
.stat-icon {
  width: 56px; height: 56px;
  border-radius: 16px;
  display: flex; align-items: center; justify-content: center;
  margin-right: 15px;
  color: white;
  font-size: 24px;
  box-shadow: 0 4px 10px rgba(0,0,0,0.1);
}
.stat-info { display: flex; flex-direction: column; }
.stat-value { font-size: 24px; font-weight: bold; color: #303133; line-height: 1.2; }
.stat-label { font-size: 13px; color: #909399; margin-top: 5px; }

/* 3. 标题与信息卡片 */
.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  position: relative;
  padding-left: 12px;
}
.section-title::before {
  content: "";
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 4px;
  background: #409EFF;
  border-radius: 2px;
}

.info-card { border-radius: 8px; }

/* 学生端的提示文字 */
.student-tips {
  padding: 10px;
  color: #606266;
  font-size: 14px;
  line-height: 2;
}
</style>
