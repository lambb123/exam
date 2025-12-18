<template>
  <div class="page-container">
    <h2>📊 成绩管理与分析</h2>
    <el-card>
      <el-table :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="60" />

        <el-table-column label="学生姓名" width="150">
          <template #default="scope">
            {{ scope.row.student.realName }} ({{ scope.row.student.username }})
          </template>
        </el-table-column>

        <el-table-column prop="paper.paperName" label="试卷名称" />

        <el-table-column label="得分" width="120" sortable :sort-method="(a,b) => a.score - b.score">
          <template #default="scope">
            <span style="font-weight: bold; color: #409EFF">{{ scope.row.score }}</span>
          </template>
        </el-table-column>

        <el-table-column label="提交时间" width="180">
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
import { getAllScores } from '@/api/exam'

const tableData = ref([])

onMounted(async () => {
  const res: any = await getAllScores()
  if(res.code === 200) {
    tableData.value = res.data
  }
})

const formatTime = (timeStr: string) => {
  if(!timeStr) return ''
  return timeStr.replace('T', ' ').substring(0, 19)
}
</script>
