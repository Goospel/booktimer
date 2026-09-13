package com.booktimer.study;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 백지복습 채점의 <b>정답지</b> — 그 책의 필기를 최근순으로 상한까지 담은 것.
 *
 * <p>이 기능 전체의 목적이 여기서 완성된다. 구멍 판정의 울타리가 사용자가 손으로 친 「범위」 한 줄뿐일
 * 때 그것은 사실상 추측이다 — 모델은 「무엇이 빠졌는지」를 알 근거가 없어 자기 세계지식에서 만들어 낸다.
 * 그 책의 필기가 들어오면 판정이 <b>대조</b>가 된다: 「필기엔 있는데 오늘 쓴 글엔 없거나 틀린 것」.
 *
 * <p><b>자르기는 장 단위다.</b> 글자 단위로 자르면 정답지가 문장 중간에서 끊겨 두 종류의 거짓을 만든다 —
 * 잘려 나간 뒤를 「안 배운 것」으로 보는 <b>거짓 구멍</b>과, 잘린 문맥을 잘못 이어 붙여 빠진 것을 못 잡는
 * <b>거짓 통과</b>. 장 단위면 잃는 것이 「이 장은 통째로 빠졌다」라는 <b>말할 수 있는 사실</b>이 되고,
 * 그래서 {@link #excluded()}가 화면 경고 카드의 근거가 된다(조용한 누락 금지 — 사용자 결정 5).
 *
 * <p>한 장의 최대 비용이 {@link StudyNote#BODY_MAX}(8000) + 헤더(제목 200자까지라 최대 224)라
 * {@link #MAX_CHARS}의 절반도 안 된다 — <b>필기가 있으면 최소 한 장은 반드시 들어간다</b>(「포함 0장」이라는
 * 상태가 존재하지 않는다). 「최근 3장은 반드시」까지는 보장하지 않는다: 세 장이 모두 최대 크기면 헤더까지
 * 24,672자라 상한을 넘는다.
 *
 * <p><b>{@link #chars}의 단위는 「모델이 받는 글자 수」다</b> — 헤더를 포함한다. 본문만 세면 상한이
 * 프롬프트 크기의 상한이 아니게 되고(장 수엔 상한이 없다), 화면이 「1,000장, 1,000자」라 말하는 동안
 * 모델은 225,000자를 받는다. 이 기능이 막으려는 어긋남이 바로 그것이라 두 수치를 같은 단위로 묶는다.
 *
 * <p>화면 카드와 분석이 <b>같은 함수</b>를 부른다({@code StudyNoteService.reference}) — 카드가 보여준
 * 것과 모델이 실제로 본 것이 다를 수 없게.
 *
 * @param included 정답지에 실리는 장 — 넘겨받은 순서(최근순) 그대로
 * @param excluded 상한에 걸려 빠진 장 — 화면이 「N장이 빠졌어요」로 드러낸다
 * @param chars    <b>모델이 받는 글자 수</b> — 헤더까지 센 값이라 {@code text(zone).length()}와 같다
 */
public record NoteReference(List<StudyNote> included, List<StudyNote> excluded, int chars) {

    /**
     * 정답지 상한 — 8000 × 3.
     *
     * <p>더 키우지 않는 이유는 비용보다 <b>판정 품질</b>이다. 한 학기치 필기 전부는 「오늘 복습」의
     * 정답지가 아니다 — 좁히는 일은 사용자가 적는 「범위」 칸이 한다.
     */
    public static final int MAX_CHARS = 24_000;

    /**
     * 앞에서부터 상한에 닿을 때까지 담는다 — <b>넘치는 장이 나오면 거기서 멈춘다</b>.
     *
     * <p>넘친 뒤에 오는 작은 장을 「빈틈에 끼워 넣지」 않는 것이 의도다. 그렇게 하면 정답지가 최근순이
     * 아니라 「크기순으로 뒤죽박죽 섞인 것」이 되어, 화면이 말하는 「최근 N장」과 실제가 어긋난다.
     *
     * @param newestFirst 최근 고친 순 — 순서를 여기서 다시 정하지 않는다(리포지터리가 정한 그대로)
     */
    public static NoteReference select(List<StudyNote> newestFirst, int max) {
        List<StudyNote> included = new ArrayList<>();
        List<StudyNote> excluded = new ArrayList<>();
        int chars = 0;
        for (StudyNote note : newestFirst == null ? List.<StudyNote>of() : newestFirst) {
            int cost = cost(note);
            if (!excluded.isEmpty() || chars + cost > max) {
                excluded.add(note);
                continue;
            }
            included.add(note);
            chars += cost;
        }
        return new NoteReference(List.copyOf(included), List.copyOf(excluded), chars);
    }

    /**
     * 한 장이 프롬프트에서 <b>실제로</b> 차지하는 글자 수 — 헤더·구분줄까지.
     *
     * <p>본문({@link StudyNote#chars})만 세면 {@link #MAX_CHARS}가 프롬프트 크기의 상한이 아니게 된다.
     * 헤더는 장마다 붙고 장 수엔 상한이 없어서다 — 제목 200자짜리 짧은 장 1,000개면 본문 합이 1,000자여도
     * 조립 결과는 225,000자다.
     *
     * <p>{@link #header}를 <b>같이</b> 지나는 것이 요점이다. 헤더 길이를 여기에 숫자로 적으면
     * {@code text}의 문구가 바뀌는 날 조용히 어긋난다(테스트가 {@code chars == text().length()}로 잠근다).
     */
    private static int cost(StudyNote note) {
        return header(note, "0000-00-00").length() + note.chars() + SEPARATOR.length();
    }

    /** @param date {@code yyyy-MM-dd} — 길이가 상수(10)라 비용 계산은 자리표시 날짜로 같은 문을 지난다. */
    private static String header(StudyNote note, String date) {
        // 제목 파생은 화면 몫이라 서버는 저장값만 본다 — 없으면 「제목 없음」이 그대로 헤더다.
        return "### 필기: " + (note.getTitle() == null ? "제목 없음" : note.getTitle())
                + " (" + date + ")\n";
    }

    /** 장과 장 사이 — 비용 계산과 조립이 같은 값을 본다. */
    private static final String SEPARATOR = "\n\n";

    /**
     * 프롬프트에 실리는 문자열 — 장마다 제목·날짜 헤더 + 본문 그대로.
     *
     * <p>본문은 마크다운째로 넘긴다(백지복습 본문과 같은 규율) — 여기서 문법을 벗기면 무엇을 체크했고
     * 무엇을 강조했는지가 모델에게서 사라진다.
     *
     * @param zone 헤더 날짜를 읽을 타임존 — <b>유저 타임존</b>이다. UTC로 찍으면 아침에 쓴 필기가
     *             전날로 보여, 모델이 받는 「언제 적은 것인가」가 하루씩 어긋난다
     */
    public String text(ZoneId zone) {
        StringBuilder text = new StringBuilder();
        for (StudyNote note : included) {
            text.append(header(note, LocalDate.ofInstant(note.getUpdatedAt(), zone).toString()))
                    .append(note.getBody()).append(SEPARATOR);
        }
        return text.toString();
    }
}
