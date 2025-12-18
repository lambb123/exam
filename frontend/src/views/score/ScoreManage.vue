<template>
  <div class="page-container">
    <h2>📊 成绩管理</h2>

    <el-card>
      <el-table :data="tableData" border stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" align="center" />

        <el-table-column prop="paper.paperName" label="试卷名称" min-width="150" show-overflow-tooltip />

        <el-table-column label="考生姓名" width="120" align="center">
          <template #default="scope">
            {{ scope.row.student ? (scope.row.student.realName || scope.row.student.username) : '未知' }}
          </template>
        </el-table-column>

        <el-table-column prop="score" label="得分" width="100" align="center">
          <template #default="scope">
            <span style="font-weight: bold; color: #409EFF">{{ scope.row.score }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="提交时间" width="180" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" align="center">
          <template #default="scope">
            <el-button type="primary" size="small" link @click="viewDetail(scope.row.id)">
              查看答卷
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAllScores } from '@/api/exam'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getAllScores()
    if (res.code === 200) {
      tableData.value = res.data
    }
  } finally {
    loading.value = false
  }
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').split('.')[0]
}

// 跳转详情
const viewDetail = (resultId: number) => {
  router.push(`/score/detail/${resultId}`)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-container { padding: 20px; }
</style>
