<template>
  <AppLayout>
    <div v-if="detail" class="detail">
      <div class="player-panel">
        <video class="player" :src="detail.video.videoUrl" :poster="detail.video.coverUrl" controls autoplay />
        <div class="video-meta">
          <h2>{{ detail.video.title }}</h2>
          <div class="desc">{{ detail.video.description }}</div>
          <div class="action-row">
            <div class="author" @click="goAuthor">
              <el-avatar :size="36" :src="detail.video.author?.avatarUrl" />
              <div>
                <div class="name">{{ detail.video.author?.nickname || detail.video.author?.username }}</div>
                <div class="counts">👁 {{ formatCount(detail.video.viewCount) }} 浏览</div>
              </div>
            </div>
            <el-button
              v-if="!isMine"
              :type="detail.followedAuthor ? 'default' : 'primary'"
              round
              @click="toggleFollow"
            >{{ detail.followedAuthor ? '已关注' : '+ 关注' }}</el-button>
          </div>
          <div class="interact">
            <span class="like-big" :class="{ liked: detail.liked }" @click="toggleLike">
              {{ detail.liked ? '❤' : '♡' }} {{ formatCount(detail.video.likeCount) }}
            </span>
            <span class="cmt-count">💬 {{ formatCount(detail.video.commentCount) }} 条评论</span>
          </div>
        </div>
      </div>

      <div class="comments-panel">
        <div class="cmt-input">
          <el-input v-model="commentText" placeholder="友善发言，宽容以待…" maxlength="500" />
          <el-button type="primary" :loading="posting" @click="postComment">发送</el-button>
        </div>
        <div v-for="c in comments" :key="c.id" class="cmt">
          <el-avatar :size="30" :src="c.author?.avatarUrl" />
          <div class="cmt-body">
            <div class="cmt-author">{{ c.author?.nickname || c.author?.username }}</div>
            <div class="cmt-content">{{ c.content }}</div>
            <div class="cmt-time">{{ c.createdAt }}</div>
          </div>
        </div>
        <div v-if="comments.length === 0" class="cmt-empty">还没有评论，来抢沙发</div>
        <div v-if="commentHasMore" class="cmt-more" @click="loadComments">加载更多评论</div>
      </div>
    </div>
    <div v-else class="loading">加载中…</div>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import { formatCount } from '../utils/format'
import http from '../api/request'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const detail = ref(null)
const comments = ref([])
const commentCursor = ref(0)
const commentHasMore = ref(false)
const commentText = ref('')
const posting = ref(false)
const isMine = ref(false)

onMounted(async () => {
  const id = route.params.id
  try {
    detail.value = await http.get(`/videos/${id}`)
    isMine.value = detail.value.video.userId === auth.user?.id
  } catch (e) {
    ElMessage.error(e.message)
    return
  }
  await loadComments()
})

async function loadComments() {
  const page = await http.get(`/videos/${route.params.id}/comments`, {
    params: { cursor: commentCursor.value, limit: 10 }
  })
  comments.value.push(...page.list)
  commentCursor.value = page.nextCursor
  commentHasMore.value = page.hasMore
}

async function postComment() {
  if (!commentText.value.trim()) return
  posting.value = true
  try {
    const c = await http.post(`/videos/${route.params.id}/comments`, { content: commentText.value.trim() })
    comments.value.unshift(c)
    commentText.value = ''
    if (detail.value) detail.value.video.commentCount++
    ElMessage.success('评论成功')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    posting.value = false
  }
}

async function toggleLike() {
  const was = detail.value.liked
  detail.value.liked = !was
  try {
    if (was) {
      await http.delete(`/videos/${route.params.id}/like`)
      detail.value.video.likeCount--
    } else {
      await http.post(`/videos/${route.params.id}/like`)
      detail.value.video.likeCount++
    }
  } catch (e) {
    detail.value.liked = was
    ElMessage.error(e.message)
  }
}

async function toggleFollow() {
  const authorId = detail.value.video.userId
  const was = detail.value.followedAuthor
  detail.value.followedAuthor = !was
  try {
    if (was) await http.delete(`/users/${authorId}/follow`)
    else await http.post(`/users/${authorId}/follow`)
  } catch (e) {
    detail.value.followedAuthor = was
    ElMessage.error(e.message)
  }
}

function goAuthor() {
  // 简化处理：无独立主页，仅提示
}
</script>

<style scoped>
.detail { display: grid; grid-template-columns: 1fr 340px; gap: 16px; align-items: start; }
.player-panel { background: #fff; border-radius: 10px; overflow: hidden; }
.player { width: 100%; aspect-ratio: 16/9; background: #000; display: block; }
.video-meta { padding: 16px 18px; }
h2 { margin: 0 0 8px; font-size: 18px; color: #222; }
.desc { font-size: 13px; color: #888; margin-bottom: 14px; }
.action-row { display: flex; align-items: center; justify-content: space-between; }
.author { display: flex; gap: 10px; align-items: center; cursor: pointer; }
.name { font-size: 14px; font-weight: 600; color: #333; }
.counts { font-size: 12px; color: #999; margin-top: 2px; }
.interact { display: flex; align-items: center; gap: 18px; margin-top: 16px; padding-top: 14px; border-top: 1px solid #f2f2f2; }
.like-big { font-size: 22px; color: #999; cursor: pointer; user-select: none; }
.like-big.liked { color: #e6544a; }
.cmt-count { font-size: 13px; color: #666; }
.comments-panel { background: #fff; border-radius: 10px; padding: 14px 16px; }
.cmt-input { display: flex; gap: 8px; margin-bottom: 12px; }
.cmt { display: flex; gap: 10px; padding: 10px 0; border-bottom: 1px solid #f5f5f5; }
.cmt-author { font-size: 13px; font-weight: 600; color: #444; }
.cmt-content { font-size: 13px; color: #333; margin: 4px 0; word-break: break-all; }
.cmt-time { font-size: 11px; color: #aaa; }
.cmt-empty { color: #999; font-size: 13px; text-align: center; padding: 18px 0; }
.cmt-more { text-align: center; color: #5b8cff; font-size: 13px; padding: 12px 0; cursor: pointer; }
.loading { text-align: center; color: #888; padding: 100px 0; }
</style>
