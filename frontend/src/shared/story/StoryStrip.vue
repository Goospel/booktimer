<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AuthorAvatar from '../AuthorAvatar.vue'
import StoryViewer from './StoryViewer.vue'
import StoryComposer from './StoryComposer.vue'
import { stripRings, viewerGroups, type StoryFeedResponse, type StripRing } from './storyFeed'
import { fetchStoryFeed } from './storyApi'

// 홈 상단 스토리 스트립 (sns-design §13.7) — 가로 스크롤 아바타 링. 내 링 맨 앞(+ 배지 = 작성 진입,
// 스토리 0이어도 표시), 미열람 링 강조는 서버 allViewed 기준(기기 무관). 뷰어·작성 모달도 여기서 소유.
const props = defineProps<{
    myLoginId: string
    myNickname: string
    myProfileCharacterCode: string | null
}>()

const feed = ref<StoryFeedResponse | null>(null)
const viewerOpen = ref(false)
const viewerStart = ref(0)
const composerOpen = ref(false)
// 뷰어가 재생 중일 때 feed를 통째로 갈아끼우면 g/i 인덱스가 다른 작성자를 가리킬 수 있어(리뷰 파인딩)
// 삭제로 인한 새로고침은 뷰어가 닫힐 때로 미룬다
const needsRefresh = ref(false)

const me = computed(() => ({
    loginId: props.myLoginId,
    nickname: props.myNickname,
    profileCharacterCode: props.myProfileCharacterCode,
}))
const rings = computed(() => (feed.value ? stripRings(feed.value, me.value) : []))
const groups = computed(() => (feed.value ? viewerGroups(feed.value) : []))

async function refresh() {
    try {
        feed.value = await fetchStoryFeed()
    } catch {
        feed.value = null // 로드 실패 — 스트립 자체를 숨겨 홈을 막지 않는다
    }
}

function onRingClick(ring: StripRing) {
    // 로컬 삭제 직후 refresh 전 잠깐의 빈 그룹 방어 — 빈 뷰어(검은 오버레이) 방지
    if (ring.groupIndex >= 0 && (groups.value[ring.groupIndex]?.stories.length ?? 0) > 0) {
        viewerStart.value = ring.groupIndex
        viewerOpen.value = true
    } else if (ring.mine) {
        composerOpen.value = true // 내 링인데 스토리 없음 — 작성 진입
    }
}

function onViewerChanged() {
    needsRefresh.value = true
}

function onViewerClose() {
    viewerOpen.value = false
    if (needsRefresh.value) {
        needsRefresh.value = false
        refresh()
    }
}

onMounted(refresh)
</script>

<template>
    <div v-if="feed" class="story-strip" aria-label="스토리">
        <!-- + 올리기: 맨 앞 독립 타일(발견 8) — 아바타 겹침 배지 대신 별도 타일로 터치 타깃을 키운다.
             내 스토리 유무와 무관하게 항상 작성 진입. -->
        <div class="story-add-tile">
            <button type="button" class="story-add-btn" aria-label="스토리 올리기"
                    @click="composerOpen = true">+</button>
            <span class="story-add-label">올리기</span>
        </div>
        <!-- 내 스토리가 없는 빈 내 링은 그리지 않는다(작성은 위 + 타일로 일원화). 뷰잉 링만 표시. -->
        <template v-for="ring in rings" :key="ring.loginId">
            <div v-if="!(ring.mine && !ring.hasStories)" class="story-ring-item">
                <button type="button" class="story-ring-btn"
                        :aria-label="ring.mine ? '내 스토리 보기' : ring.nickname + ' 스토리 보기'"
                        @click="onRingClick(ring)">
                    <span class="dash-header-avatar story-ring"
                          :class="{ 'story-ring-unviewed': ring.unviewed }"
                          aria-hidden="true">
                        <AuthorAvatar :code="ring.profileCharacterCode" :fallback-text="ring.loginId" />
                    </span>
                </button>
                <span class="story-ring-name">{{ ring.mine ? '내 스토리' : ring.nickname }}</span>
            </div>
        </template>

        <StoryViewer v-if="viewerOpen && groups.length" :groups="groups" :start-group="viewerStart"
                     :my-login-id="myLoginId" @close="onViewerClose" @changed="onViewerChanged" />
        <StoryComposer v-if="composerOpen" @close="composerOpen = false" @created="refresh" />
    </div>
</template>
