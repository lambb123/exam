<template>
  <div class="dashboard-container">
    <div class="welcome-card">
      <div class="welcome-text">
        <h2>👋 欢迎回来，{{ user.realName }}！</h2>
        <p>今天是 {{ currentDate }}，祝你拥有高效的一天。</p>
      </div>
      <img src="https://img.freepik.com/free-vector/exams-concept-illustration_114360-2754.jpg" class="welcome-img" alt="bg"/>
    </div>

    <el-row :gutter="20" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card color-1">
          <div class="stat-content">
            <div class="icon-box"><el-icon><User /></el-icon></div>
            <div class="info">
              <div class="label">总用户数</div>
              <div class="number">{{ stats.userCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card color-2">
          <div class="stat-content">
            <div class="icon-box"><el-icon><DocumentCopy /></el-icon></div>
            <div class="info">
              <div class="label">题库试题</div>
              <div class="number">{{ stats.questionCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card color-3">
          <div class="stat-content">
            <div class="icon-box"><el-icon><Files /></el-icon></div>
            <div class="info">
              <div class="label">试卷总数</div>
              <div class="number">{{ stats.paperCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card color-4">
          <div class="stat-content">
            <div class="icon-box"><el-icon><Trophy /></el-icon></div>
            <div class="info">
              <div class="label">考试人次</div>
              <div class="number">{{ stats.examCount }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="intro-card">
      <template #header>
        <div class="card-header">
          <span>🚀 系统功能概览</span>
        </div>
      </template>
      <div class="feature-list">
        <p>✅ <b>角色管理</b>：支持管理员、教师、学生三种角色登录。</p>
        <p>✅ <b>题库管理</b>：支持单选、多选、判断等多种题型录入。</p>
        <p>✅ <b>智能组卷</b>：一键随机抽题，自动生成试卷并计算总分。</p>
        <p>✅ <b>在线考试</b>：学生在线答题，提交后自动判分。</p>
        <p>✅ <b>成绩分析</b>：可视化展示考试成绩，支持导出和排名。</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboardStats } from '@/api/dashboard'
import { User, DocumentCopy, Files, Trophy } from '@element-plus/icons-vue'

const user = JSON.parse(localStorage.getItem('user') || '{}')
const stats = ref({
  userCount: 0,
  questionCount: 0,
  paperCount: 0,
  examCount: 0
})

const currentDate = new Date().toLocaleDateString()

onMounted(async () => {
  const res: any = await getDashboardStats()
  if(res.code === 200) {
    stats.value = res.data
  }
})
</script>

<style scoped>
.welcome-card {
  background: white;
  padding: 20px 40px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}
.welcome-text h2 { margin: 0 0 10px 0; color: #303133; }
.welcome-text p { color: #909399; margin: 0; }
.welcome-img { height: 100px; object-fit: contain; }

.stat-row { margin-bottom: 20px; }
.stat-card { border: none; }
.stat-content { display: flex; align-items: center; }
.icon-box {
  width: 60px; height: 60px;
  border-radius: 50%;
  display: flex; justify-content: center; align-items: center;
  font-size: 24px; color: white;
  margin-right: 15px;
}
.info .number { font-size: 24px; font-weight: bold; color: #303133; }
.info .label { font-size: 12px; color: #909399; }

/* 卡片颜色配色 */
.color-1 .icon-box { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.color-2 .icon-box { background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 99%, #fecfef 100%); }
.color-3 .icon-box { background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%); }
.color-4 .icon-box { background: linear-gradient(135deg, #84fab0 0%, #8fd3f4 100%); }

.intro-card { min-height: 200px; }
.feature-list p { line-height: 2; color: #606266; }
</style>
