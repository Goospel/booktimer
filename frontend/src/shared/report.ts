import { getCsrfToken } from './follow';

/**
 * 신고 POST 헬퍼. follow.ts/block.ts 선례와 일관.
 * @returns reported — "내가 이 사람을 신고한 상태인가"(멱등).
 */
export async function report(loginId: string, reason: string, detail: string): Promise<boolean> {
    const res = await fetch('/api/report', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': getCsrfToken(),
        },
        body: JSON.stringify({ loginId, reason, detail }),
    });
    return res.ok ? (await res.json()).reported : false;
}
