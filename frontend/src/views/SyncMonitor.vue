<template>
  <div class="monitor-container">
    <div class="page-header">
      <h2>🔄 同步监控中心</h2>
      <el-button type="primary" size="small" @click="fetchAllData" :loading="loading">刷新状态</el-button>
    </div>

    <div class="status-banner">
      <el-alert
        v-if="globalStatus === 'error'"
        title="数据不一致 (Critical)"
        type="error"
        effect="dark"
        show-icon
        :closable="false"
      >
        <template #default>
          检测到 MySQL / Oracle / SQL Server 数据行数不匹配，请检查下方“实时核对”表格。
        </template>
      </el-alert>

      <el-alert
        v-else-if="globalStatus === 'warning'"
        title="同步任务存在异常 (Warning)"
        type="warning"
        effect="dark"
        show-icon
        :closable="false"
      >
        <template #default>
          数据目前一致，但近期日志中存在失败记录，请关注服务稳定性。
        </template>
      </el-alert>

      <el-alert
        v-else
        title="系统运行健康 (Healthy)"
        type="success"
        effect="dark"
        show-icon
        :closable="false"
      >
        <template #default>
          所有数据库数据强一致，且近期无同步异常。
        </template>
      </el-alert>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :sm="16">
        <el-card class="chart-card">
          <template #header><span>📅 近7天同步趋势</span></template>
          <div ref="lineChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card class="chart-card margin-top-mobile">
          <template #header><span>📊 成功率分布</span></template>
          <div ref="pieChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>📚 多数据库实时核对 (MySQL vs Oracle vs SQLServer)</span>
          <el-tag v-if="globalStatus === 'error'" type="danger" effect="dark">数据差异</el-tag>
          <el-tag v-else type="success" effect="dark">数据一致</el-tag>
        </div>
      </template>

      <el-table :data="tableStatusList" border stripe style="width: 100%">
        <el-table-column prop="tableName" label="数据库表名" min-width="160" />
        <el-table-column prop="mysqlCount" label="MySQL (主)" align="center" width="120">
          <template #default="{ row }"><b>{{ row.mysqlCount }}</b></template>
        </el-table-column>
        <el-table-column prop="oracleCount" label="Oracle (备)" align="center" width="120" />
        <el-table-column prop="sqlServerCount" label="SQLServer (备)" align="center" width="130" />
        <el-table-column label="同步状态" align="center" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SYNCED' ? 'success' : 'danger'" effect="light">
              <el-icon v-if="row.status === 'SYNCED'"><Check /></el-icon>
              <el-icon v-else><Close /></el-icon>
              {{ row.status === 'SYNCED' ? '已同步' : '有差异' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <span style="color: #F56C6C; font-weight: bold;">🚨 最新异常日志</span>
      </template>
      <el-table :data="recentErrors" style="width: 100%" size="small" empty-text="暂无异常，系统运行良好">
        <el-table-column prop="createTime" label="发生时间" width="180">
          <template #default="{row}">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="message" label="异常详情" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { Check, Close } from '@element-plus/icons-vue' // 引入图标

const loading = ref(false)
const lineChartRef = ref()
const pieChartRef = ref()

const tableStatusList = ref<any[]>([])
const recentErrors = ref<any[]>([])
const distribution = ref({ success: 0, fail: 0 })

// ✅ 计算全局状态
const globalStatus = computed(() => {
  if (tableStatusList.value.some((row: any) => row.status === 'DIFF')) {
    return 'error' // 数据不一致
  }
  if (recentErrors.value.length > 0) {
    return 'warning' // 数据一致但有报错
  }
  return 'success' // 完美
})

const fetchAllData = async () => {
  loading.value = true
  try {
    const [resTable, resDash] = await Promise.all([
      request.get('/api/monitor/table-status'),
      request.get('/api/monitor/dashboard')
    ])

    if (resTable.code === 200) tableStatusList.value = resTable.data

    if (resDash.code === 200) {
      const { trend, distribution: dist, recentErrors: errors } = resDash.data
      distribution.value = dist
      recentErrors.value = errors
      nextTick(() => initCharts(trend, dist))
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const initCharts = (trendData: any[], distData: any) => {
  if (lineChartRef.value) {
    const myChart = echarts.init(lineChartRef.value)
    myChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: trendData.map(i => i.logDate) },
      yAxis: { type: 'value' },
      series: [
        { name: '成功', type: 'line', smooth: true, data: trendData.map(i => i.successCount), itemStyle: { color: '#67C23A' } },
        { name: '失败', type: 'line', smooth: true, data: trendData.map(i => i.failCount), itemStyle: { color: '#F56C6C' } }
      ]
    })
    window.addEventListener('resize', () => myChart.resize())
  }

  if (pieChartRef.value) {
    const myPie = echarts.init(pieChartRef.value)
    myPie.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { value: distData.success, name: '成功', itemStyle: { color: '#67C23A' } },
          { value: distData.fail, name: '失败', itemStyle: { color: '#F56C6C' } }
        ]
      }]
    })
    window.addEventListener('resize', () => myPie.resize())
  }
}

const formatTime = (isoStr: string) => {
  if (!isoStr) return ''
  return isoStr.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  fetchAllData()
})
</script>

<style scoped>
.monitor-container { padding: 15px; background: #f5f7fa; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }
.status-banner { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.chart-card { min-height: 350px; }
.chart-box { width: 100%; height: 300px; }

/* 响应式样式 */
.margin-top-mobile { margin-top: 0; }
@media screen and (max-width: 768px) {
  .margin-top-mobile { margin-top: 20px; }
}
</style>
