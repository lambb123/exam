<template>
  <div class="page-container">
    <div class="header">
      <h2>添加新试题</h2>
    </div>

    <el-card style="max-width: 800px;">
      <el-form :model="form" label-width="100px">

        <el-form-item label="题目内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            placeholder="请输入详细的题目描述"
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="题目类型">
              <el-select v-model="form.type" placeholder="请选择" style="width: 100%">
                <el-option label="单选题" value="单选" />
                <el-option label="多选题" value="多选" />
                <el-option label="判断题" value="判断" />
                <el-option label="简答题" value="简答" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度等级">
              <el-select v-model="form.difficulty" placeholder="请选择" style="width: 100%">
                <el-option label="简单" value="简单" />
                <el-option label="中等" value="中等" />
                <el-option label="困难" value="困难" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="知识点" required>
          <el-input v-model="form.knowledgePoint" placeholder="例如：Java基础, Spring Boot, 数据库索引" />
        </el-form-item>

        <el-form-item label="正确答案" required>
          <el-input v-model="form.answer" placeholder="例如：A, B, 对, 错" />
        </el-form-item>


        <el-form-item>
          <el-button type="primary" size="large" @click="onSubmit">立即创建</el-button>
          <el-button size="large" @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { addQuestion } from '@/api/question' // 这个接口之前已经定义过了
import { ElMessage } from 'element-plus'

const router = useRouter()

const form = reactive({
  content: '',
  type: '单选',
  difficulty: '简单',
  knowledgePoint: '',
  answer: ''
})

const onSubmit = async () => {
  if (!form.content || !form.knowledgePoint) {
    return ElMessage.warning('请填写题目内容和知识点')

  }

  if (!form.content || !form.knowledgePoint || !form.answer) {
    return ElMessage.warning('请填写完整（包括正确答案）')
  }

  try {
    const res: any = await addQuestion(form)
    if (res.code === 200) {
      ElMessage.success('添加成功')
      router.push('/question/list') // 添加完跳回列表
    }
  } catch (e) {
    // 错误在 request.ts 里统一处理了
  }
}
</script>

<style scoped>
.header { margin-bottom: 20px; }
</style>
