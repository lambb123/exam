<template>
  <div class="dashboard-container">
    <div class="welcome-card">
      <div class="welcome-text">
        <h2>👋 欢迎回来，{{ user.realName || user.username }}！</h2>
        <p>今天是 {{ currentDate }}，愿您通过系统高效完成同步工作。</p>
      </div>
      <img src="https://img.freepik.com/free-vector/exams-concept-illustration_114360-2754.jpg" class="welcome-img" alt="bg"/>
    </div>

    <el-row :gutter="20" class="mb-20">
      <el-col :span="6" v-for="(item, index) in statItems" :key="item.key">
        <div class="stat-card" :class="'style-' + (index + 1)">
          <div class="stat-icon-bg">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">{{ item.label }}</div>
            <div class="stat-value">
              <span class="num">{{ stats[item.key] || 0 }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-card class="db-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="title">📡 数据库集群状态监控</span>
          <el-button text type="primary" :loading="dbLoading" @click="fetchDbStatus">
            <el-icon class="mr-1"><Refresh /></el-icon> 刷新状态
          </el-button>
        </div>
      </template>

      <el-row :gutter="40" class="db-row">
        <el-col :span="8">
          <div class="db-status-item" :class="{ 'online': dbStatus.mysql }">
            <div class="status-icon">
              <span class="db-type">MySQL</span>
              <span class="role-tag main">主库</span>
            </div>
            <div class="status-detail">
              <div class="status-text">
                <div class="dot"></div>
                {{ dbStatus.mysql ? '运行正常' : '连接断开' }}
              </div>
              <div class="desc">源数据中心</div>
            </div>
          </div>
        </el-col>

        <el-col :span="8">
          <div class="db-status-item" :class="{ 'online': dbStatus.oracle }">
            <div class="status-icon">
              <span class="db-type">Oracle</span>
              <span class="role-tag backup">备库</span>
            </div>
            <div class="status-detail">
              <div class="status-text">
                <div class="dot"></div>
                {{ dbStatus.oracle ? '运行正常' : '连接断开' }}
              </div>
              <div class="desc">异地灾备节点</div>
            </div>
          </div>
        </el-col>

        <el-col :span="8">
          <div class="db-status-item" :class="{ 'online': dbStatus.sqlserver }">
            <div class="status-icon">
              <span class="db-type">SQL Server</span>
              <span class="role-tag backup">备库</span>
            </div>
            <div class="status-detail">
              <div class="status-text">
                <div class="dot"></div>
                {{ dbStatus.sqlserver ? '运行正常' : '连接断开' }}
              </div>
              <div class="desc">数据分析节点</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { getDashboardStats, getDbStatus } from '@/api/dashboard'
import { User, DocumentCopy, Files, EditPen, Refresh } from '@element-plus/icons-vue'

const user = JSON.parse(localStorage.getItem('user') || '{}')
const currentDate = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const dbLoading = ref(false)
const stats = ref<Record<string, number>>({})
const dbStatus = ref<Record<string, boolean>>({ mysql: false, oracle: false, sqlserver: false })

// 定义统计项配置，方便循环渲染
const statItems = [
  { key: 'userCount', label: '注册用户总数', icon: User },
  { key: 'questionCount', label: '题库试题数量', icon: DocumentCopy },
  { key: 'paperCount', label: '已组试卷总数', icon: Files },
  { key: 'examCount', label: '在线考试人次', icon: EditPen }
]

// === 数据请求 ===
const loadDashboardData = async () => {
  try {
    const sRes: any = await getDashboardStats()
    if (sRes.code === 200) stats.value = sRes.data
  } catch (e) {}

  fetchDbStatus()
}

const fetchDbStatus = async () => {
  dbLoading.value = true
  try {
    const res: any = await getDbStatus()
    if (res.code === 200) dbStatus.value = res.data
  } catch (e) {
  } finally {
    dbLoading.value = false
  }
}

onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped>
.dashboard-container { padding: 24px; background-color: #f5f7fa; min-height: 100vh; }
.mb-20 { margin-bottom: 24px; }
.mr-1 { margin-right: 4px; }

/* 1. 欢迎卡片美化 */
.welcome-card {
  background: linear-gradient(135deg, #ffffff 0%, #f0f7ff 100%);
  padding: 30px 40px;
  border-radius: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
  border: 1px solid #eef2f6;
}
.welcome-text h2 { margin: 0 0 12px 0; color: #1a1a1a; font-size: 24px; font-weight: 600; }
.welcome-text p { color: #606266; margin: 0; font-size: 14px; }
.welcome-img { height: 100px; object-fit: contain; }

/* 2. 统计卡片美化 */
.stat-card {
  background: white;
  border-radius: 12px;
  padding: 25px;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  border: 1px solid transparent;
  height: 120px;
  position: relative;
  overflow: hidden;
}
.stat-card:hover { transform: translateY(-5px); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08); }

/* 不同的卡片风格 */
.style-1 { border-bottom: 3px solid #409eff; }
.style-1 .stat-icon-bg { background: rgba(64, 158, 255, 0.1); color: #409eff; }
.style-2 { border-bottom: 3px solid #67c23a; }
.style-2 .stat-icon-bg { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
.style-3 { border-bottom: 3px solid #e6a23c; }
.style-3 .stat-icon-bg { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.style-4 { border-bottom: 3px solid #f56c6c; }
.style-4 .stat-icon-bg { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }

.stat-icon-bg {
  width: 60px; height: 60px; border-radius: 50%;
  display: flex; justify-content: center; align-items: center;
  font-size: 28px; margin-right: 20px;
}
.stat-info { flex: 1; }
.stat-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.stat-value .num { font-size: 32px; font-weight: bold; color: #303133; font-family: 'DIN Alternate', sans-serif; }

/* 3. 数据库状态卡片 */
.db-card { border-radius: 12px; border: none; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header .title { font-size: 16px; font-weight: bold; color: #303133; }
.db-row { padding: 20px 0; }

.db-status-item {
  background: #f8f9fa; border-radius: 8px; padding: 20px;
  border: 1px solid #ebeef5; display: flex; flex-direction: column;
  align-items: center; justify-content: center; text-align: center;
  transition: all 0.3s;
}
.db-status-item.online { background: #f0f9eb; border-color: #e1f3d8; }
.db-status-item:hover { transform: scale(1.02); }

.status-icon { margin-bottom: 15px; position: relative; }
.db-type { font-weight: bold; font-size: 18px; color: #303133; display: block; margin-bottom: 5px; }
.role-tag { font-size: 10px; padding: 2px 6px; border-radius: 4px; color: white; text-transform: uppercase; }
.role-tag.main { background: #409eff; }
.role-tag.backup { background: #909399; }

.status-text { display: flex; align-items: center; justify-content: center; font-size: 14px; color: #909399; margin-bottom: 4px; }
.online .status-text { color: #67c23a; font-weight: bold; }
.dot { width: 8px; height: 8px; background: #f56c6c; border-radius: 50%; margin-right: 6px; }
.online .dot { background: #67c23a; box-shadow: 0 0 0 2px rgba(103, 194, 58, 0.2); }

.desc { font-size: 12px; color: #c0c4cc; }
</style>
