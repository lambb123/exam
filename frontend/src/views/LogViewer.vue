<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">📜 系统同步日志记录</span>
          <el-button type="primary" :loading="loading" @click="fetchLogs">
            <el-icon><Refresh /></el-icon> 刷新日志
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
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
            <el-tag :type="scope.row.status === 'SUCCESS' ? 'success' : (scope.row.status === 'FAILED' ? 'danger' : 'primary')">
              {{ scope.row.status === 'SUCCESS' ? '成功' : (scope.row.status === 'FAILED' ? '失败' : '进行中') }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="message" label="日志详情 / 报错信息">
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
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getSyncLogs } from '@/api/log'
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const allLogs = ref<any[]>([])
const currentPage = ref(1)
const pageSize = ref(10)

// 前端模拟分页（如果后端是分页接口，这里需要改写）
const tableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return allLogs.value.slice(start, start + pageSize.value)
})

const total = computed(() => allLogs.value.length)

const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').split('.')[0]
}

const fetchLogs = async () => {
  loading.value = true
  try {
    const res: any = await getSyncLogs()
    if (res.code === 200) {
      allLogs.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  currentPage.value = 1
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
}

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-weight: bold; font-size: 16px; }
.error-msg { color: #F56C6C; font-family: monospace; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
