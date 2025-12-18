<template>
  <div class="dashboard-container">
    <div class="welcome-card">
      <div class="welcome-text">
        <h2>👋 欢迎回来，{{ user.realName }}！</h2>
        <p>今天是 {{ currentDate }}，系统运行平稳。</p>
      </div>
      <img src="https://img.freepik.com/free-vector/exams-concept-illustration_114360-2754.jpg" class="welcome-img" alt="bg"/>
    </div>

    <el-card class="db-status-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>📡 多数据源实时监控</span>
          <el-button link type="primary" @click="fetchDbStatus">刷新状态</el-button>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="8">
          <div class="db-item" :class="{ 'is-active': dbStatus.mysql }">
            <div class="db-icon mysql">🐬</div>
            <div class="db-info">
              <div class="db-name">MySQL (主源)</div>
              <div class="db-state">
                <span class="dot"></span> {{ dbStatus.mysql ? '运行正常' : '连接断开' }}
              </div>
            </div>
          </div>
        </el-col>

        <el-col :span="8">
          <div class="db-item" :class="{ 'is-active': dbStatus.oracle }">
            <div class="db-icon oracle">O</div>
            <div class="db-info">
              <div class="db-name">Oracle (备份1)</div>
              <div class="db-state">
                <span class="dot"></span> {{ dbStatus.oracle ? '同步正常' : '连接异常' }}
              </div>
            </div>
          </div>
        </el-col>

        <el-col :span="8">
          <div class="db-item" :class="{ 'is-active': dbStatus.sqlserver }">
            <div class="db-icon sqlserver">S</div>
            <div class="db-info">
              <div class="db-name">SQL Server (备份2)</div>
              <div class="db-state">
                <span class="dot"></span> {{ dbStatus.sqlserver ? '同步正常' : '连接异常' }}
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

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
        <p>✅ <b>多源同步</b>：MySQL 主库实时自动同步至 Oracle 和 SQL Server 备库。</p>
        <p>✅ <b>故障报警</b>：同步异常自动邮件通知管理员。</p>
        <p>✅ <b>智能组卷</b>：一键随机抽题，自动生成试卷并计算总分。</p>
        <p>✅ <b>在线考试</b>：学生在线答题，提交后自动判分。</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboardStats, getDbStatus } from '@/api/dashboard'
import { User, DocumentCopy, Files, Trophy } from '@element-plus/icons-vue'

const user = JSON.parse(localStorage.getItem('user') || '{}')
const stats = ref({
  userCount: 0,
  questionCount: 0,
  paperCount: 0,
  examCount: 0
})

const dbStatus = ref({
  mysql: false,
  oracle: false,
  sqlserver: false
})

const currentDate = new Date().toLocaleDateString()

const fetchDbStatus = async () => {
  const res: any = await getDbStatus()
  if(res.code === 200) {
    dbStatus.value = res.data
  }
}

onMounted(async () => {
  // 加载统计数据
  const res: any = await getDashboardStats()
  if(res.code === 200) {
    stats.value = res.data
  }
  // 加载数据库状态
  fetchDbStatus()
})
</script>

<style scoped>
.dashboard-container { padding: 20px; }

/* 欢迎卡片 */
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

/* 数据库监控卡片 */
.db-status-card { margin-bottom: 20px; }
.db-item {
  display: flex;
  align-items: center;
  padding: 15px;
  border-radius: 8px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  transition: all 0.3s;
}
.db-item.is-active {
  background: #f0f9eb;
  border-color: #67c23a;
}
.db-icon {
  width: 40px; height: 40px;
  border-radius: 50%;
  display: flex; justify-content: center; align-items: center;
  font-weight: bold; font-size: 18px; color: white;
  margin-right: 12px;
}
.db-icon.mysql { background: #00758f; }
.db-icon.oracle { background: #f80000; }
.db-icon.sqlserver { background: #a9a9a9; } /* SQL Server 灰色，连上变亮 */

.is-active .db-icon.sqlserver { background: #333; }

.db-info .db-name { font-weight: bold; font-size: 14px; color: #606266; }
.db-state { font-size: 12px; color: #909399; display: flex; align-items: center; margin-top: 4px; }
.is-active .db-state { color: #67c23a; }

/* 呼吸灯效果 */
.dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  margin-right: 6px;
  display: inline-block;
}
.is-active .dot {
  background: #67c23a;
  box-shadow: 0 0 8px #67c23a;
  animation: breathe 2s infinite ease-in-out;
}

@keyframes breathe {
  0% { opacity: 0.6; transform: scale(0.9); }
  50% { opacity: 1; transform: scale(1.1); }
  100% { opacity: 0.6; transform: scale(0.9); }
}

/* 统计卡片 */
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

.color-1 .icon-box { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.color-2 .icon-box { background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 99%, #fecfef 100%); }
.color-3 .icon-box { background: linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%); }
.color-4 .icon-box { background: linear-gradient(135deg, #84fab0 0%, #8fd3f4 100%); }

.intro-card { min-height: 200px; }
.feature-list p { line-height: 2; color: #606266; }
</style>
