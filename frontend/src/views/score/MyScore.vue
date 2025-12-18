<template>
  <div class="page-container">
    <h2>🏆 我的战绩</h2>
    <el-card>
      <el-table :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="考试编号" width="100" />

        <el-table-column label="试卷名称">
          <template #default="scope">
            <span style="font-weight: bold">{{ scope.row.paper.paperName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="paper.totalScore" label="试卷总分" width="120" />

        <el-table-column label="我的得分" width="120">
          <template #default="scope">
            <el-tag :type="getScoreColor(scope.row.score, scope.row.paper.totalScore)" size="large" effect="dark">
              {{ scope.row.score }} 分
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="考试时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.examTime) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMyScore } from '@/api/exam'

const tableData = ref([])

onMounted(async () => {
  // 获取当前登录用户的 ID
  const userStr = localStorage.getItem('user')
  if(userStr) {
    const user = JSON.parse(userStr)
    // 调用接口获取成绩
    const res: any = await getMyScore(user.id)
    if(res.code === 200) {
      tableData.value = res.data
    }
  }
})

// 根据分数显示不同颜色
const getScoreColor = (score: number, total: number) => {
  const rate = score / total
  if (rate >= 0.9) return 'success' // 优秀（绿色）
  if (rate >= 0.6) return 'warning' // 及格（黄色）
  return 'danger' // 不及格（红色）
}

// 简单的日期格式化
const formatTime = (timeStr: string) => {
  if(!timeStr) return ''
  return timeStr.replace('T', ' ').substring(0, 19)
}
</script>
