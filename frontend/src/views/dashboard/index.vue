<template>
  <div class="dashboard-container">
    <div class="welcome-card">
      <div class="welcome-text">
        <h2>👋 欢迎回来，{{ user.realName }}！</h2>
        <p>今天是 {{ currentDate }}，系统运行平稳。</p>
      </div>
      <img src="https://img.freepik.com/free-vector/exams-concept-illustration_114360-2754.jpg" class="welcome-img" alt="bg"/>
    </div>

    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="14">
        <el-card class="status-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📡 数据库连接状态</span>
              <el-button link type="primary" @click="fetchDbStatus">刷新状态</el-button>
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="8" v-for="(status, name) in dbStatus" :key="name">
              <div class="db-item" :class="{ 'is-active': status }">
                <div class="db-icon" :class="String(name)">
                  {{ String(name).toUpperCase().substring(0, 1) }}
                </div>
                <div class="db-info">
                  <div class="db-name">{{ getDbName(String(name)) }}</div>
                  <div class="db-state">
                    <span class="dot"></span> {{ status ? '连接正常' : '连接断开' }}
                  </div>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-row :gutter="15">
          <el-col :span="12" v-for="(value, key) in stats" :key="key" style="margin-bottom: 15px;">
            <el-card shadow="hover" class="stat-card" :class="'color-' + getKeyIndex(String(key))">
              <div class="stat-content">
                <div class="number">{{ value }}</div>
                <div class="label">{{ getStatLabel(String(key)) }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-col>
    </el-row>

    <el-card class="log-table-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>📜 系统同步日志记录</span>
          <el-button type="primary" size="small" @click="fetchLogs" :loading="logLoading">
            刷新日志
          </el-button>
        </div>
      </template>

      <el-table
        :data="paginatedLogs"
        style="width: 100%"
        border
        stripe
        v-loading="logLoading"
        max-height="500"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />

        <el-table-column label="开始时间" width="180" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.startTime) }}
          </template>
        </el-table-column>

        <el-table-column label="结束时间" width="180" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.endTime) }}
          </template>
        </el-table-column>

        <el-table-column prop="status" label="同步状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getLogStatusType(scope.row.status)" effect="dark">
              {{ scope.row.status === 'SUCCESS' ? '成功' : (scope.row.status === 'FAILED' ? '失败' : '进行中') }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="message" label="日志详情 / 报错信息" min-width="400">
          <template #default="scope">
            <span :class="{ 'error-msg': scope.row.status === 'FAILED' }">
              {{ scope.row.message }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[5, 10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="allLogs.length"
        />
      </div>
    </el-card>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getDashboardStats, getDbStatus } from '@/api/dashboard'
import { getSyncLogs } from '@/api/log'

const user = JSON.parse(localStorage.getItem('user') || '{}')
const currentDate = new Date().toLocaleDateString()

// === 数据定义 ===
const stats = ref<Record<string, number>>({})
const dbStatus = ref<Record<string, boolean>>({ mysql: false, oracle: false, sqlserver: false })

const allLogs = ref<any[]>([]) // 所有日志数据
const logLoading = ref(false)

// === 分页配置 ===
const currentPage = ref(1)
const pageSize = ref(10) // 默认每页10条

// 前端分页计算
const paginatedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return allLogs.value.slice(start, start + pageSize.value)
})

// === 辅助方法 ===
const getDbName = (key: string) => {
  const map: Record<string, string> = { mysql: 'MySQL (主)', oracle: 'Oracle (备)', sqlserver: 'SQL Server (备)' }
  return map[key] || key.toUpperCase()
}

const getKeyIndex = (key: string) => {
  const keys = Object.keys(stats.value)
  return (keys.indexOf(key) % 4) + 1
}

const getStatLabel = (key: string) => {
  const map: Record<string, string> = {
    userCount: '用户总数', questionCount: '题库数量',
    paperCount: '试卷总数', examCount: '考试人次'
  }
  return map[key] || key
}

const getLogStatusType = (status: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'primary'
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').split('.')[0]
}

// === 数据请求 ===
const loadDashboardData = async () => {
  // 1. 获取统计
  try {
    const sRes: any = await getDashboardStats()
    if (sRes.code === 200) stats.value = sRes.data
  } catch (e) {}

  // 2. 获取状态
  fetchDbStatus()

  // 3. 获取日志
  fetchLogs()
}

const fetchDbStatus = async () => {
  try {
    const res: any = await getDbStatus()
    if (res.code === 200) dbStatus.value = res.data
  } catch (e) {}
}

const fetchLogs = async () => {
  logLoading.value = true
  try {
    const res: any = await getSyncLogs()
    if (res.code === 200 && res.data) {
      allLogs.value = res.data
    }
  } catch (e) {
    console.error(e)
  } finally {
    logLoading.value = false
  }
}

onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped>
.dashboard-container { padding: 20px; background-color: #f0f2f5; min-height: 100vh; }

/* 欢迎栏 */
.welcome-card {
  background: white; padding: 20px 40px; border-radius: 8px;
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
}
.welcome-text h2 { margin: 0 0 10px 0; color: #303133; }
.welcome-text p { color: #909399; margin: 0; }
.welcome-img { height: 80px; object-fit: contain; }

/* 状态卡片 */
.status-card { height: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }

.db-item {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 15px; border-radius: 6px; background: #f5f7fa; border: 1px solid #EBEEF5;
  transition: all 0.3s; margin-bottom: 5px;
}
.db-item.is-active { background: #f0f9eb; border-color: #67c23a; }

.db-icon {
  width: 48px; height: 48px; border-radius: 50%; color: white;
  display: flex; justify-content: center; align-items: center; font-weight: bold; font-size: 20px;
  margin-bottom: 10px;
}
.db-icon.mysql { background: #00758f; }
.db-icon.oracle { background: #f80000; }
.db-icon.sqlserver { background: #666; }
.is-active .db-icon.sqlserver { background: #333; }

.db-name { font-weight: bold; font-size: 14px; color: #606266; margin-bottom: 5px; }
.db-state { font-size: 12px; color: #909399; display: flex; align-items: center; }
.is-active .db-state { color: #67c23a; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #f56c6c; margin-right: 5px; display: inline-block; }
.is-active .dot { background: #67c23a; animation: breathe 2s infinite; }
@keyframes breathe { 0% { opacity: 0.5; } 50% { opacity: 1; } 100% { opacity: 0.5; } }

/* 统计卡片 */
.stat-card { text-align: center; cursor: pointer; transition: transform 0.2s; border: none; height: 100px; display: flex; align-items: center; justify-content: center; }
.stat-card:hover { transform: translateY(-3px); }
.stat-content .number { font-size: 28px; font-weight: bold; color: #303133; margin-bottom: 5px; }
.stat-content .label { font-size: 13px; color: #909399; }
.color-1 { background: #e8f3ff; } .color-2 { background: #f0f9eb; }
.color-3 { background: #fdf6ec; } .color-4 { background: #fef0f0; }

/* 日志卡片 */
.log-table-card { margin-top: 0; }
.error-msg { color: #F56C6C; font-family: monospace; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
