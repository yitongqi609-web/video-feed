<template>
  <div class="card" @click="$router.push('/video/' + item.video.id)">
    <div class="cover-wrap">
      <img v-if="item.video.coverUrl" class="cover" :src="item.video.coverUrl" loading="lazy" alt="" />
      <div v-else class="cover cover-fallback"><span>{{ item.video.title }}</span></div>
      <div class="play-badge">▶</div>
      <div class="stats">
        <span>❤ {{ formatCount(item.video.likeCount) }}</span>
        <span>👁 {{ formatCount(item.video.viewCount) }}</span>
      </div>
    </div>
    <div class="info">
      <div class="title">{{ item.video.title }}</div>
      <div class="author">
        <el-avatar :size="22" :src="item.video.author?.avatarUrl" />
        <span>{{ item.video.author?.nickname || item.video.author?.username || '未知作者' }}</span>
        <span
          class="like-btn"
          :class="{ liked: item.liked }"
          @click.stop="$emit('toggle-like', item)"
        >{{ item.liked ? '❤' : '♡' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { formatCount } from '../utils/format'

defineProps({ item: { type: Object, required: true } })
defineEmits(['toggle-like'])
</script>

<style scoped>
.card { background: #fff; border-radius: 10px; overflow: hidden; cursor: pointer; box-shadow: 0 1px 3px rgba(0,0,0,.05); transition: transform .15s; }
.card:hover { transform: translateY(-2px); }
.cover-wrap { position: relative; aspect-ratio: 16/9; background: #ddd; }
.cover { width: 100%; height: 100%; object-fit: cover; display: block; }
.cover-fallback { background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; display: flex; align-items: center; justify-content: center; text-align: center; font-size: 15px; padding: 10px; }
.play-badge { position: absolute; left: 50%; top: 50%; transform: translate(-50%,-50%); width: 44px; height: 44px; border-radius: 50%; background: rgba(0,0,0,.45); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 18px; }
.stats { position: absolute; right: 8px; bottom: 8px; display: flex; gap: 10px; color: #fff; font-size: 12px; text-shadow: 0 1px 2px rgba(0,0,0,.6); }
.info { padding: 10px 12px 12px; }
.title { font-size: 14px; line-height: 1.4; height: 2.8em; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; color: #222; }
.author { display: flex; align-items: center; gap: 6px; margin-top: 8px; font-size: 12px; color: #888; }
.like-btn { margin-left: auto; font-size: 18px; cursor: pointer; color: #999; user-select: none; }
.like-btn.liked { color: #e6544a; }
</style>
