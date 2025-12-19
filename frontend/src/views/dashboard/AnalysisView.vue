<template>
  <div class="analysis-container">
    <div class="header">
      <h2>📊 试卷质量分析 (基于复杂 SQL 统计)</h2>
      <p class="subtitle">
        数据来源：通过多表连接 (JOIN) 与聚合函数 (AVG, MAX) 实时计算
      </p>
    </div>

    <div class="chart-box" ref="chartRef"></div>

    <div class="table-box">
      <table class="data-table">
        <thead>
        <tr>
          <th>试卷名称</th>
          <th>出卷教师</th>
          <th>参考人数</th>
          <th>平均分</th>
          <th>最高分</th>
          <th>难度评级</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(item, index) in statsData" :key="index">
          <td>{{ item.paperName }}</td>
          <td>{{ item.teacherName || '未知' }}</td>
          <td>{{ item.studentCount }}</td>
          <td :class="getScoreClass(item.avgScore)">
            {{ formatNumber(item.avgScore) }}
          </td>
          <td>{{ item.maxScore }}</td>
          <td>
              <span class="badge" :class="getDifficultyClass(item.avgScore)">
                {{ getDifficultyLabel(item.avgScore) }}
              </span>
          </td>
        </tr>
        <tr v-if="statsData.length === 0">
          <td colspan="6" style="text-align: center; color: #999;">暂无考试数据</td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { getPaperStats } from '@/api/dashboard'
import * as echarts from 'echarts' // 确保你安装了 echarts: npm install echarts

const statsData = ref<any[]>([])
const chartRef = ref<HTMLElement>()

// 获取数据
const fetchData = async () => {
  try {
    const res = await getPaperStats()
    if (res.code === 200) {
      statsData.value = res.data
      initChart(res.data)
    }
  } catch (error) {
    console.error("获取统计数据失败", error)
  }
}

// 初始化图表
const initChart = (data: any[]) => {
  if (!chartRef.value || data.length === 0) return

  const myChart = echarts.init(chartRef.value)

  const option = {
    title: { text: '各试卷平均分对比', left: 'center' },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.map(item => item.paperName),
      axisLabel: { interval: 0, rotate: 30 } // 防止名字太长重叠
    },
    yAxis: { type: 'value', name: '分数' },
    series: [
      {
        data: data.map(item => item.avgScore),
        type: 'bar',
        itemStyle: { color: '#5470C6' },
        label: { show: true, position: 'top', formatter: '{c}分' }
      },
      {
        data: data.map(item => item.maxScore),
        type: 'line',
        name: '最高分',
        itemStyle: { color: '#91CC75' }
      }
    ]
  }
  myChart.setOption(option)
}

// 辅助函数
const formatNumber = (num: number) => Number(num).toFixed(1)

const getScoreClass = (score: number) => {
  return score < 60 ? 'score-fail' : 'score-pass'
}

const getDifficultyLabel = (avg: number) => {
  if (avg > 85) return '简单'
  if (avg > 70) return '适中'
  return '困难'
}

const getDifficultyClass = (avg: number) => {
  if (avg > 85) return 'badge-easy'
  if (avg > 70) return 'badge-medium'
  return 'badge-hard'
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.analysis-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 80vh;
}
.header { margin-bottom: 20px; }
.subtitle { color: #666; font-size: 14px; margin-top: 5px; }

.chart-box {
  width: 100%;
  height: 400px;
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1);
}

.table-box {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th, .data-table td {
  padding: 12px 15px;
  text-align: left;
  border-bottom: 1px solid #ebeef5;
}

.data-table th {
  background-color: #fafafa;
  font-weight: 600;
  color: #333;
}

.score-fail { color: #f56c6c; font-weight: bold; }
.score-pass { color: #67c23a; font-weight: bold; }

.badge {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  color: #fff;
}
.badge-easy { background-color: #67c23a; }
.badge-medium { background-color: #409eff; }
.badge-hard { background-color: #f56c6c; }
</style>
