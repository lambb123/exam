import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/',
      name: 'home',
      component: HomeView,
      redirect: '/dashboard', // 默认跳到仪表盘
      children: [
        {
          path: 'dashboard',
          component: () => import('../views/dashboard/index.vue')
        },
        {
          path: 'question/list',
          component: () => import('../views/question/QuestionList.vue')
        },
        {
          path: 'question/add',
          component: () => import('../views/question/QuestionAdd.vue')
        },
        {
          path: 'paper/list',
          component: () => import('../views/paper/PaperList.vue')
        },
        {
          path: 'paper/create',
          component: () => import('../views/paper/PaperCreate.vue')
        },
        {
          path: 'exam/list',
          component: () => import('../views/exam/ExamList.vue')
        },
        {
          path: 'exam/do/:id', // :id 是参数，代表试卷ID
          component: () => import('../views/exam/ExamDoing.vue')
        },
        {
          path: 'score/my',
          component: () => import('../views/score/MyScore.vue')
        },
        // 教师管理分
        {
          path: 'score/manage',
          component: () => import('../views/score/ScoreManage.vue')
        }
        // 其他页面后续再加

      ]
    }
  ]
})

// 路由守卫：没登录不准进首页
router.beforeEach((to, from, next) => {
  const user = localStorage.getItem('user')
  if (to.path !== '/login' && !user) {
    next('/login')
  } else {
    next()
  }
})

export default router
