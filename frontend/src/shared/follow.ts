export function getCsrfToken(): string {
    return (document.querySelector('meta[name="_csrf"]') as HTMLMetaElement)?.content ?? '';
}

export interface UserRowData {
    loginId: string;
    nickname: string;
    publicBookCount: number;
    following: boolean;
    self: boolean;
}

/** 친구 추천 한 줄 — 사용자 행 + "추천 이유" 칩(예: "공통 친구 3명", "같이 읽은 책 2권"). 폴백 후보는 빈 배열. */
export interface RecommendedUser extends UserRowData {
    reasons: string[];
}

/** 팔로우 토글 — user.following을 갱신(반응형 객체 직접 변이). */
export async function toggleFollow(user: UserRowData): Promise<void> {
    const endpoint = user.following ? '/api/unfollow' : '/api/follow';
    const res = await fetch(endpoint, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': getCsrfToken(),
        },
        body: JSON.stringify({ loginId: user.loginId }),
    });
    if (res.ok) {
        user.following = (await res.json()).following;
    }
}
