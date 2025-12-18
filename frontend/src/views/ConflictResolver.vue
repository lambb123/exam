<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">⚔️ 数据同步冲突处理</span>
          <div class="header-actions">
            <el-button type="primary" plain :loading="loading" @click="fetchConflicts">
              <el-icon style="margin-right:5px"><Refresh /></el-icon> 刷新列表
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        title="操作提示：此处列出的是主库与备库同步过程中因主键重复、数据版本不一致或外键约束导致的冲突记录。"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 20px;"
      />

      <el-table :data="conflictList" border stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" align="center" />

        <el-table-column prop="tableName" label="涉及数据表" width="150" align="center">
          <template #default="scope">
            <el-tag effect="plain">{{ scope.row.tableName }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="conflictType" label="冲突类型" width="140" align="center">
          <template #default="scope">
            <el-tag type="danger" v-if="scope.row.conflictType === 'DUPLICATE_KEY'">主键冲突</el-tag>
            <el-tag type="warning" v-else-if="scope.row.conflictType === 'DATA_MISMATCH'">数据不一致</el-tag>
            <el-tag type="info" v-else>其他错误</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="description" label="冲突详情 / 差异描述" min-width="300">
          <template #default="scope">
            <div>{{ scope.row.description }}</div>
            <div style="font-size: 12px; color: #909399;">Source ID: {{ scope.row.sourceId }}</div>
          </template>
        </el-table-column>

        <el-table-column label="发生时间" width="180" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="scope">
            <el-button type="primary" size="small" @click="handleResolve(scope.row, 'force')">
              <el-icon><Check /></el-icon> 强制同步
            </el-button>
            <el-button type="danger" size="small" link @click="handleResolve(scope.row, 'ignore')">
              忽略
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 20px; text-align: right;">
        <el-pagination layout="total, prev, pager, next" :total="conflictList.length" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh, Check } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request' // 引入封装好的 axios

const loading = ref(false)
const conflictList = ref<any[]>([])

// 时间格式化工具：2023-12-18T09:30:12 -> 2023-12-18 09:30:12
const formatTime = (time: string | Date) => {
  if (!time) return '-'
  const d = new Date(time)
  return d.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

// 获取冲突列表（对接真实后端）
const fetchConflicts = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/api/conflict/list')
    if (res.code === 200) {
      conflictList.value = res.data || []
      // 只有手动刷新时才提示，避免页面加载时太吵，这里可以根据需要保留
      // ElMessage.success('冲突列表已刷新')
    } else {
      ElMessage.warning(res.msg || '获取数据异常')
    }
  } catch (e) {
    ElMessage.error('无法连接到冲突检测接口')
  } finally {
    loading.value = false
  }
}

// 处理冲突逻辑
const handleResolve = (row: any, action: string) => {
  const actionText = action === 'force' ? '强制覆盖（以主库为准）' : '忽略此冲突'
  const confirmType = action === 'force' ? 'warning' : 'info'

  ElMessageBox.confirm(`确定要对记录 [${row.tableName}] 执行 ${actionText} 操作吗？此操作不可撤销。`, '冲突处理确认', {
    confirmButtonText: '确定处理',
    cancelButtonText: '取消',
    type: confirmType
  }).then(() => {
    // 调用后端处理接口
    request.post('/api/conflict/resolve', {
      tableName: row.tableName,
      sourceId: row.sourceId,
      action: action
    }).then((res: any) => {
      if(res.code === 200) {
        ElMessage.success('操作成功')
        // 移除已处理的行
        conflictList.value = conflictList.value.filter(item => item.id !== row.id)
      } else {
        ElMessage.error(res.msg || '操作失败')
      }
    }).catch((err: any) => {
      console.error(err)
      ElMessage.error('请求失败')
    })
  }).catch(() => {
    // 取消操作
  })
}

onMounted(() => {
  fetchConflicts()
})
</script>

<style scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-weight: bold; font-size: 16px; color: #303133; display: flex; align-items: center; gap: 8px; }
</style>
