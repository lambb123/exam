<template>
  <div class="page-container">
    <div class="toolbar">
      <h2>试卷管理</h2>
      <el-button type="primary" @click="$router.push('/paper/create')">
        <el-icon><Plus /></el-icon> 智能组卷
      </el-button>
    </div>

    <el-card>
      <el-table :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" align="center" />
        <el-table-column prop="paperName" label="试卷名称" show-overflow-tooltip />
        <el-table-column prop="totalScore" label="总分" width="100" align="center">
          <template #default="scope">
            <span style="font-weight: bold; color: #f56c6c">{{ scope.row.totalScore }} 分</span>
          </template>
        </el-table-column>
        <el-table-column prop="teacher.realName" label="出卷教师" width="120" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" align="center">
          <template #default="scope">
            <el-button
              type="primary"
              link
              size="small"
              @click="$router.push(`/paper/detail/${scope.row.id}`)"
            >
              查看详情
            </el-button>

            <el-popconfirm
              title="确定要删除这张试卷吗？这将同时删除所有相关的考试成绩！"
              confirm-button-text="确定删除"
              cancel-button-text="取消"
              width="220"
              @confirm="handleDelete(scope.row)"
            >
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>

          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPaperList, deletePaper } from '@/api/paper' // 【修改】引入 deletePaper
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus' // 【新增】引入消息提示

const tableData = ref([])

const loadData = async () => {
  const res: any = await getPaperList()
  if (res.code === 200) {
    tableData.value = res.data
  }
}

// 【新增】删除处理逻辑
const handleDelete = async (row: any) => {
  try {
    const res: any = await deletePaper(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      // 删除成功后刷新列表
      loadData()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('删除请求失败')
  }
}

const formatTime = (timeStr: string) => {
  if(!timeStr) return ''
  return timeStr.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
</style>
