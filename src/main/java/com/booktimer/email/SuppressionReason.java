package com.booktimer.email;

/**
 * 이메일 억제 사유 — SES 피드백 종류에 대응한다.
 *
 * <ul>
 *   <li>{@code BOUNCE} — 영구 반송(존재하지 않는 주소 등). SES가 permanent bounce로 통지한 경우만 억제한다
 *       (일시 반송은 억제하지 않음 — 재시도로 성공할 수 있으므로).</li>
 *   <li>{@code COMPLAINT} — 수신자가 스팸으로 신고. 발신 평판에 가장 치명적이라 즉시 억제하고 마케팅 동의도 철회한다.</li>
 * </ul>
 */
public enum SuppressionReason {
    BOUNCE,
    COMPLAINT
}
