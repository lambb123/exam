<template>
  <div class="page-container">
    <h2>🏆 我的成绩单</h2>

    <el-card>
      <el-table :data="tableData" border stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="paper.paperName" label="试卷名称" show-overflow-tooltip />

        <el-table-column label="得分" width="120" align="center">
          <template #default="scope">
            <span style="font-size: 16px; font-weight: bold; color: #F56C6C">
              {{ scope.row.score }} 分
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="考试时间" width="180" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" align="center">
          <template #default="scope">
            <el-button type="primary" size="small" link @click="viewDetail(scope.row.id)">
              查看错题 / 详情
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
import { getMyScores } from '@/api/exam'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])

const loadData = async () => {
  loading.value = true
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  try {
    const res: any = await getMyScores(user.id)
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
