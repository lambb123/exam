<template>
  <div class="page-container">
    <div class="toolbar">
      <h2>试题库管理</h2>
      <el-button type="primary" @click="$router.push('/question/add')">添加试题</el-button>
    </div>

    <el-card>
      <el-table :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="content" label="题目内容" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="scope">
            <el-tag>{{ scope.row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.difficulty === '困难' ? 'danger' : 'success'">
              {{ scope.row.difficulty }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="knowledgePoint" label="知识点" width="150" />

        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button type="danger" size="small" @click="handleDelete(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getQuestionList, deleteQuestion } from '@/api/question'
import { ElMessage, ElMessageBox } from 'element-plus'

const tableData = ref([])

const loadData = async () => {
  const res: any = await getQuestionList()
  if (res.code === 200) {
    tableData.value = res.data
  }
}

const handleDelete = (id: number) => {
  ElMessageBox.confirm('确定要删除这道题吗？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteQuestion(id)
    ElMessage.success('删除成功')
    loadData() // 刷新列表
  })
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
