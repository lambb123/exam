<template>
  <div class="page-container" v-loading="loading">
    <div class="header">
      <el-page-header @back="$router.back()" title="返回">
        <template #content>
          <span class="text-large font-600 mr-3"> 答卷详情 </span>
        </template>
      </el-page-header>
    </div>

    <div v-if="paperInfo" class="content-box">
      <el-card class="score-card" shadow="hover">
        <div class="score-summary">
          <div class="score-item">
            <div class="label">试卷名称</div>
            <div class="value">{{ paperInfo.paperName }}</div>
          </div>
          <div class="score-item">
            <div class="label">考生姓名</div>
            <div class="value">{{ studentName }}</div>
          </div>
          <div class="score-item highlight">
            <div class="label">最终得分</div>
            <div class="value">{{ examScore }} 分</div>
          </div>
        </div>
      </el-card>

      <el-card class="question-list-card" shadow="never">
        <div v-for="(item, index) in questionList" :key="item.question.id" class="question-item">
          <div class="q-header">
            <el-tag size="small" effect="dark" :type="getTypeTag(item.question.type)">
              {{ item.question.type }}
            </el-tag>
            <span class="q-index">第 {{ index + 1 }} 题</span>
            <span class="q-score">({{ item.score }} 分)</span>

            <el-tag v-if="item.isCorrect" type="success" effect="dark" class="status-tag">正确</el-tag>
            <el-tag v-else type="danger" effect="dark" class="status-tag">错误</el-tag>
          </div>

          <div class="q-content">{{ item.question.content }}</div>

          <div class="answer-box" :class="{ 'wrong-bg': !item.isCorrect }">
            <el-row :gutter="20">
              <el-col :span="12">
                <div class="ans-label">考生答案：</div>
                <div class="ans-text" :class="item.isCorrect ? 'green' : 'red'">
                  {{ item.studentAnswer || '(未作答)' }}
                </div>
              </el-col>

              <el-col :span="12" v-if="showCorrectAnswer || !item.isCorrect">
                <div class="ans-label">正确答案：</div>
                <div class="ans-text green">{{ item.question.answer }}</div>
              </el-col>
            </el-row>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getExamResultDetail } from '@/api/exam'
import { ElMessage } from 'element-plus'

const route = useRoute()
const loading = ref(false)
const paperInfo = ref<any>(null)
const questionList = ref<any[]>([])
const examScore = ref(0)
const studentName = ref('')
const currentUser = JSON.parse(localStorage.getItem('user') || '{}')

// 权限控制：是否总是显示正确答案
// 现在的逻辑是：如果是管理员或老师，总是显示；如果是学生，只有错题才显示（在模板v-if里控制了）
const showCorrectAnswer = computed(() => {
  return ['ADMIN', 'TEACHER'].includes(currentUser.role)
})

const getTypeTag = (type: string) => {
  const map: any = { '单选': '', '多选': 'success', '判断': 'warning', '填空': 'primary' }
  return map[type] || 'info'
}

const loadData = async () => {
  const id = Number(route.params.id)
  if (!id) return

  loading.value = true
  try {
    const res: any = await getExamResultDetail(id)
    if (res.code === 200) {
      paperInfo.value = res.data.paperInfo
      questionList.value = res.data.questions
      examScore.value = res.data.examResult.score

      const student = res.data.examResult.student
      studentName.value = student.realName || student.username
    } else {
      ElMessage.error(res.msg || '获取详情失败')
    }
  } catch (e) {
    ElMessage.error('无法连接服务器')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-container { padding: 20px; }
.header { margin-bottom: 20px; }
.score-card { margin-bottom: 20px; background: linear-gradient(135deg, #fdfbfb 0%, #ebedee 100%); }
.score-summary { display: flex; justify-content: space-around; align-items: center; padding: 10px; }
.score-item { text-align: center; }
.score-item .label { font-size: 14px; color: #909399; margin-bottom: 5px; }
.score-item .value { font-size: 18px; font-weight: bold; color: #303133; }
.score-item.highlight .value { color: #f56c6c; font-size: 24px; }

.question-item { padding: 20px; border-bottom: 1px dashed #eee; }
.question-item:last-child { border-bottom: none; }
.q-header { display: flex; align-items: center; margin-bottom: 12px; }
.q-index { font-weight: bold; margin: 0 10px; }
.q-score { color: #999; font-size: 12px; }
.status-tag { margin-left: auto; }

.q-content { font-size: 16px; margin-bottom: 15px; line-height: 1.6; white-space: pre-wrap; color: #303133; }

.answer-box { background: #f8f9fa; padding: 15px; border-radius: 6px; border-left: 4px solid #e4e7ed; transition: all 0.3s; }
.answer-box.wrong-bg { background: #fef0f0; border-left-color: #f56c6c; }

.ans-label { font-size: 13px; color: #909399; margin-bottom: 4px; }
.ans-text { font-size: 15px; font-weight: bold; }
.green { color: #67c23a; }
.red { color: #f56c6c; }
</style>
