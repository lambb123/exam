<template>
  <div class="monitor-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="title">
            <el-icon class="icon"><Monitor /></el-icon>
            <span>表级数据同步监控</span>
          </div>
          <div class="actions">
            <el-button type="primary" :loading="loading" @click="fetchData">
              <el-icon style="margin-right:5px"><Refresh /></el-icon> 立即检查
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="tableData"
        border
        stripe
        style="width: 100%"
        v-loading="loading"
        :row-class-name="tableRowClassName"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />

        <el-table-column prop="tableName" label="数据表名称" min-width="180">
          <template #default="scope">
            <span style="font-weight: bold;">{{ scope.row.tableName }}</span>
          </template>
        </el-table-column>

        <el-table-column label="MySQL (主库)" align="center" min-width="140">
          <template #default="scope">
            <el-tag type="primary" effect="plain">{{ scope.row.mysqlCount }} 条</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Oracle (备库)" align="center" min-width="140">
          <template #default="scope">
            <el-tag :type="scope.row.mysqlCount === scope.row.oracleCount ? 'success' : 'danger'">
              {{ scope.row.oracleCount }} 条
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="SQL Server (备库)" align="center" min-width="140">
          <template #default="scope">
            <el-tag :type="scope.row.mysqlCount === scope.row.sqlServerCount ? 'success' : 'danger'">
              {{ scope.row.sqlServerCount }} 条
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="同步状态" align="center" width="150">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 'SYNCED'" type="success" effect="dark">
              <el-icon><Check /></el-icon> 数据一致
            </el-tag>
            <el-tag v-else type="danger" effect="dark">
              <el-icon><Warning /></el-icon> 数据冲突
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="tip-box">
        <p><el-icon><InfoFilled /></el-icon> 说明：系统会自动对比主库与两个备库的记录总数。若出现“数据冲突”，请检查后端日志或手动触发全量同步。</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getTableSyncStatus } from '@/api/syncApi'
import { Monitor, Refresh, Check, Warning, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await getTableSyncStatus()
    if (res.code === 200) {
      tableData.value = res.data
      ElMessage.success('同步状态检查完成')
    } else {
      ElMessage.error('获取数据失败')
    }
  } catch (error) {
    ElMessage.error('无法连接到监控接口')
  } finally {
    loading.value = false
  }
}

// 针对有差异的行，给表格加个背景色高亮
const tableRowClassName = ({ row }: { row: any }) => {
  if (row.status === 'DIFF') {
    return 'warning-row'
  }
  return ''
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.monitor-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 18px; font-weight: bold; display: flex; align-items: center; gap: 8px; color: #303133; }
.icon { color: #409eff; font-size: 20px; }

.tip-box { margin-top: 20px; padding: 10px 15px; background: #ecf5ff; border-left: 5px solid #409eff; border-radius: 4px; }
.tip-box p { margin: 0; color: #606266; font-size: 13px; display: flex; align-items: center; gap: 5px; }

/* 错误行高亮样式 */
:deep(.el-table .warning-row) { --el-table-tr-bg-color: #fef0f0; }
</style>
