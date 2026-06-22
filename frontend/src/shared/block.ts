import { getCsrfToken } from './follow';

/** 차단 토글 — true=차단, false=해제. 액션 후 "내가 차단 중인가"를 반환. */
export async function setBlock(loginId: string, block: boolean): Promise<boolean> {
    const res = await fetch(block ? '/api/block' : '/api/unblock', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': getCsrfToken(),
        },
        body: JSON.stringify({ loginId }),
    });
    return res.ok ? (await res.json()).blocked : block;
}
