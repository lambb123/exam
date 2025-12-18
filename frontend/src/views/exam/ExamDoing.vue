<template>
  <div class="exam-container">
    <div class="header">
      <h2>正在考试：{{ paperInfo.paperName }}</h2>
      <div class="timer">倒计时功能后续可加</div>
    </div>

    <el-card class="question-card" v-for="(item, index) in questionList" :key="item.question.id">
      <template #header>
        <span class="q-title">第 {{ index + 1 }} 题：{{ item.question.content }}</span>
        <el-tag size="small" style="margin-left:10px">{{ item.score }} 分</el-tag>
      </template>

      <el-radio-group v-model="answers[item.question.id]">
        <el-radio label="A">A</el-radio>
        <el-radio label="B">B</el-radio>
        <el-radio label="C">C</el-radio>
        <el-radio label="D">D</el-radio>
      </el-radio-group>
    </el-card>

    <div class="footer-btn">
      <el-button type="primary" size="large" @click="handleSubmit" :loading="loading">
        交卷并查看分数
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExamDetail, submitExam } from '@/api/exam'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const paperId = route.params.id
const loading = ref(false)

const paperInfo = ref<any>({})
const questionList = ref<any[]>([])
const answers = reactive<any>({}) // 存放学生答案 {问题ID: 答案}

onMounted(async () => {
  const res: any = await getExamDetail(Number(paperId))
  if (res.code === 200) {
    // 【修改点 1】获取试卷基本信息
    paperInfo.value = res.data.paperInfo

    // 【修改点 2】获取题目列表 (后端现在直接返回 questionList 了)
    questionList.value = res.data.questionList

    // 调试一下：看看控制台有没有打印出题目数据
    console.log('题目数据:', questionList.value)
  }
})

const handleSubmit = () => {
  ElMessageBox.confirm('确定要提交试卷吗？提交后将自动判分。', '提示', {
    confirmButtonText: '交卷',
    cancelButtonText: '检查一下',
    type: 'warning'
  }).then(async () => {
    loading.value = true
    const userStr = localStorage.getItem('user')
    const user = JSON.parse(userStr || '{}')

    const submitData = {
      studentId: user.id,
      paperId: Number(paperId),
      answers: answers
    }

    try {
      const res: any = await submitExam(submitData)
      if (res.code === 200) {
        const score = res.data.score
        ElMessageBox.alert(`你的得分是：${score} 分`, '考试结束', {
          confirmButtonText: '查看详情',
          callback: () => {
            router.push('/score/my') // 跳到成绩单页面
          }
        })
      }
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.exam-container { max-width: 800px; margin: 0 auto; padding-bottom: 50px; }
.header { text-align: center; margin-bottom: 20px; }
.question-card { margin-bottom: 20px; }
.q-title { font-weight: bold; font-size: 16px; }
.footer-btn { text-align: center; margin-top: 30px; }
</style>
