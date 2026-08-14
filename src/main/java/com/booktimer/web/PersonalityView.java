package com.booktimer.web;

import com.booktimer.personality.PersonalityHistoryEntry;
import com.booktimer.personality.ReadingPersonality;
import com.booktimer.personality.ReadingProfile;
import com.booktimer.personality.ReadingTagger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 책BTI 화면 표시 모델(책BTI Phase 5) — 분석 결과를 화면이 그릴 3가지 상태로 분류하고, 히스토리(최대 3개)를 싣는다.
 *
 * <p>같은 "서술 없음"이라도 이유가 다르면 화면 문구가 다르다:
 * <ul>
 *   <li>{@code READY} — 대표 서술 있음(정상 노출). {@link #entries()}에 최대 3개의 과거 분석이 최신순으로 담긴다.</li>
 *   <li>{@code COLD_START} — 책이 임계 미만이라 분석 보류("조금 더 읽으면 성향이 보여요")</li>
 *   <li>{@code FALLBACK} — 책은 충분하나 LLM 실패/지연("잠시 후 다시 분석")</li>
 * </ul>
 * COLD_START·FALLBACK이면 아직 저장된 분석이 없어 {@link #entries()}는 비어 있다.
 *
 * @param state             화면 상태
 * @param narrative         대표 성향 서술(READY일 때만, 아니면 null)
 * @param tags              대표 태그(v1은 화면 비노출 — 저장만)
 * @param profile           집계된 사실(항상 존재 — 콜드스타트·폴백에서도 요약 표시)
 * @param coldStartMinBooks 콜드스타트 임계(안내 문구의 "최소 N권"에 쓰임)
 * @param entries           분석 히스토리(최신순, 최대 3개) — 각 카드에 대표 여부·stale 표시
 * @param zone              분석 시각을 표시할 사용자 타임존 — {@link #formatTime}이 UTC 저장값을 사용자 시각으로 변환
 */
public record PersonalityView(State state, String narrative, List<String> tags,
                              ReadingProfile profile, int coldStartMinBooks,
                              List<PersonalityHistoryEntry> entries, ZoneId zone) {

    /** 분석 시각 표시 포맷 — 날짜+시·분(초는 생략, 카드가 과거 분석을 구분할 정도면 충분). */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public PersonalityView {
        entries = (entries == null) ? List.of() : List.copyOf(entries);
        zone = (zone == null) ? ZoneId.of("UTC") : zone; // 방어적 기본값(정상 경로는 항상 사용자 타임존 주입)
    }

    /**
     * 분석 생성 시각({@link Instant}, UTC 저장)을 <b>사용자 타임존</b>으로 변환해 "yyyy-MM-dd HH:mm"으로 포맷한다.
     *
     * <p>그동안 템플릿이 {@code #temporals.format(Instant,…)}로 찍던 값은 서버 JVM 기본 타임존(컨테이너=UTC)
     * 기준이라, 한국 사용자에게 9시간 어긋난 시각(예: 17:43 → 08:43)으로 보였다. 변환 지점을 뷰 모델로 옮겨
     * 사용자 타임존을 명시적으로 적용한다.
     */
    public String formatTime(Instant instant) {
        return format(instant, zone);
    }

    /**
     * 뷰 인스턴스 없이 시각만 포맷하는 진입 — {@code GET /api/personality/status}처럼 <b>히스토리만</b> 싣는
     * 응답이 쓴다(그 GET은 분석 결과를 만들지 않으므로 뷰를 세울 수 없다). 인스턴스 {@link #formatTime}이
     * 이걸 위임하므로 두 경로의 표기는 구조적으로 갈릴 수 없다.
     */
    public static String format(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "";
        }
        return TIME_FORMAT.format(instant.atZone(zone));
    }

    public enum State { READY, COLD_START, FALLBACK }

    public boolean isReady() {
        return state == State.READY;
    }

    public boolean isColdStart() {
        return state == State.COLD_START;
    }

    public boolean isFallback() {
        return state == State.FALLBACK;
    }

    /**
     * 화면 표시 순서: 대표(selected) 먼저, 그 뒤 나머지를 최신순.
     * entries()는 최신순 원본을 그대로 유지(저장·테스트 계약 보존) — 이건 "표시"만의 파생.
     */
    public List<PersonalityHistoryEntry> displayEntries() {
        return entries().stream()
                .sorted(Comparator.comparing(PersonalityHistoryEntry::selected).reversed()
                        .thenComparing(PersonalityHistoryEntry::generatedAt, Comparator.reverseOrder()))
                .toList();
    }

    /** 분석 결과 + 히스토리 + 콜드스타트 임계 + 사용자 타임존으로 화면 표시 모델을 만든다. */
    public static PersonalityView from(ReadingPersonality result, List<PersonalityHistoryEntry> entries,
                                       int coldStartMinBooks, ZoneId zone) {
        ReadingProfile profile = result.profile();
        List<String> tags = ReadingTagger.tagsOf(profile); // 결정적 태그 — LLM 아님
        if (result.hasNarration()) {
            return new PersonalityView(State.READY, result.narration().narrative(),
                    tags, profile, coldStartMinBooks, entries, zone);
        }
        // 서술 없음 — 완독 책 부족이면 콜드스타트, 충분하면 LLM 실패(폴백). 성향은 완독 책에서만 뽑으므로 완독 권수로 판정.
        State state = profile.finishedBooks() < coldStartMinBooks ? State.COLD_START : State.FALLBACK;
        return new PersonalityView(state, null, tags, profile, coldStartMinBooks, List.of(), zone);
    }
}
