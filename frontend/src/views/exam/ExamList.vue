<template>
  <div class="page-container">
    <h2>📝 在线考试大厅</h2>
    <el-card>
      <el-table :data="tableData" border stripe>
        <el-table-column prop="paperName" label="试卷名称" />
        <el-table-column prop="totalScore" label="总分" width="100" />
        <el-table-column prop="teacher.realName" label="出卷人" width="120" />
        <el-table-column label="操作" width="150">
          <template #default="scope">
            <el-button type="success" size="small" @click="startExam(scope.row.id)">
              开始答题
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
import { getPaperList } from '@/api/paper'

const router = useRouter()
const tableData = ref([])

onMounted(async () => {
  const res: any = await getPaperList()
  if (res.code === 200) tableData.value = res.data
})

const startExam = (id: number) => {
  router.push(`/exam/do/${id}`) // 跳转到答题页
}
</script>
