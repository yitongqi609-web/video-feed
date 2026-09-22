import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  { path: '/', name: 'feed', component: () => import('../views/FeedView.vue') },
  { path: '/following', name: 'following', component: () => import('../views/FollowingView.vue') },
  { path: '/hot', name: 'hot', component: () => import('../views/HotView.vue') },
  { path: '/video/:id', name: 'video', component: () => import('../views/VideoDetailView.vue') }
]

const router = createRouter({ history: createWebHistory(), routes })

// 登录守卫：未登录一律去登录页
router.beforeEach((to) => {
  const hasToken = !!localStorage.getItem('vf.accessToken')
  if (!to.meta.public && !hasToken) return { name: 'login' }
  if (to.name === 'login' && hasToken) return { name: 'feed' }
})

export default router
