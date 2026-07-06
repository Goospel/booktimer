// 온보딩 강화 아일랜드 (발견 10·12) — SSR 폼을 소스로 두고, Vue는 두 조력자만 얹는다:
//   ① #onboarding-preview  → OnboardingPreview: 아이디·닉네임 실시간 미리보기(소셜 경로만 아이디칸 존재)
//   ② #onboarding-goal-presets → GoalPresets: 하루 목표 프리셋 칩(실제 목표 input을 세팅)
// 값의 단일 소스는 여전히 SSR input(th:field·검증 에러 재렌더와 충돌하지 않게 함). 아일랜드는
// input 이벤트를 읽어 미리보기를 갱신하고, 아이디는 정제값을 input에 되써 미리보기=제출값을 일치시킨다.
import { createApp, reactive, h } from 'vue';
import OnboardingPreview from './OnboardingPreview.vue';
import GoalPresets from './GoalPresets.vue';
import { sanitizeLoginId } from './loginIdPreview';

const loginInput = document.getElementById('loginId') as HTMLInputElement | null;
const nickInput = document.getElementById('nickname') as HTMLInputElement | null;
const goalInput = document.getElementById('incrementMinutes') as HTMLInputElement | null;

// ① 실시간 미리보기 ------------------------------------------------------------
const previewEl = document.getElementById('onboarding-preview');
if (previewEl) {
    const needsLoginId = previewEl.dataset.needsLoginId === 'true';
    const state = reactive({
        loginId: loginInput?.value ?? '',
        nickname: nickInput?.value ?? '',
    });

    if (loginInput) {
        loginInput.addEventListener('input', () => {
            // 정제값을 input에 되써 "보이는 핸들 = 실제 제출값"을 항상 일치시킨다(서버 normalizeLoginId의 거울).
            const cleaned = sanitizeLoginId(loginInput.value);
            if (loginInput.value !== cleaned) loginInput.value = cleaned;
            state.loginId = cleaned;
        });
    }
    nickInput?.addEventListener('input', () => { state.nickname = nickInput.value; });

    createApp({
        render: () => h(OnboardingPreview, {
            loginId: state.loginId,
            nickname: state.nickname,
            needsLoginId,
        }),
    }).mount(previewEl);
}

// ② 목표 프리셋 칩 -------------------------------------------------------------
const presetEl = document.getElementById('onboarding-goal-presets');
if (presetEl && goalInput) {
    const initial = Number(goalInput.value) || 30;
    createApp(GoalPresets, {
        initial,
        onPick: (minutes: number | null) => {
            if (minutes !== null) {
                goalInput.value = String(minutes);
            } else {
                // '직접' — 사용자가 값을 채우도록 포커스를 준다(현재 값은 유지).
                goalInput.focus();
                goalInput.select();
            }
        },
    }).mount(presetEl);
}
