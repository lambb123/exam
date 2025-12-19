import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', name: 'login', component: LoginView },
    {
      path: '/home',
      name: 'home',
      component: HomeView,
      redirect: '/dashboard',
      children: [
        { path: '/dashboard', name: 'dashboard', component: () => import('../views/dashboard/index.vue'), meta: { title: '仪表盘' } },
        // ... 业务管理 ...
        { path: '/education', name: 'education', component: () => import('../views/EducationManager.vue'), meta: { title: '教务管理' } },
        { path: '/question/list', name: 'questions', component: () => import('../views/question/QuestionList.vue'), meta: { title: '题库管理' } },
        { path: '/question/add', name: 'question-add', component: () => import('../views/question/QuestionAdd.vue'), meta: { title: '添加试题' } },
        { path: '/paper/list', name: 'paper-list', component: () => import('../views/paper/PaperList.vue'), meta: { title: '试卷管理' } },
        { path: '/paper/create', name: 'paper-create', component: () => import('../views/paper/PaperCreate.vue'), meta: { title: '组卷' } },
        { path: '/paper/detail/:id', name: 'paper-detail', component: () => import('../views/paper/PaperDetail.vue'), meta: { title: '试卷详情' } },

        // ... 考试相关 ...
        { path: '/exam/list', name: 'exam-list', component: () => import('../views/exam/ExamList.vue'), meta: { title: '在线考试' } },
        { path: '/exam/do/:id', name: 'exam-doing', component: () => import('../views/exam/ExamDoing.vue'), meta: { title: '正在考试' } },

        // ... 成绩相关 ...
        { path: '/score/manage', name: 'score-manage', component: () => import('../views/score/ScoreManage.vue'), meta: { title: '成绩管理' } },
        { path: '/score/my', name: 'score-my', component: () => import('../views/score/MyScore.vue'), meta: { title: '我的成绩' } },

        // 【新增】答卷详情页路由
        { path: '/score/detail/:id', name: 'score-detail', component: () => import('../views/score/ScoreDetail.vue'), meta: { title: '答卷详情' } },

        // ... 监控相关 ...
        { path: '/monitor', name: 'monitor', component: () => import('../views/SyncMonitor.vue'), meta: { title: '同步监控' } },
        { path: '/logs', name: 'logs', component: () => import('../views/LogViewer.vue'), meta: { title: '日志记录' } },
        { path: '/conflict', name: 'conflict', component: () => import('../views/ConflictResolver.vue'), meta: { title: '冲突处理' } },
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  const user = localStorage.getItem('user')
  if (to.path !== '/login' && !user) {
    next('/login')
  } else {
    next()
  }
})

export default router
