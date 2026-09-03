package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 공부 일정 원장 유스케이스 — 조회·수동 추가·삭제, 그리고 「오늘 이후 전부 교체」.
 *
 * <p>교체 규칙(설계 §2.2 A안)이 이 클래스의 요점이다: 새 일정을 적용하면 <b>오늘(유저 tz) 이후</b> 항목을
 * 전부 지우고 새것을 넣는다. 규칙이 한 문장이라 화면이 「오늘 이후 N개가 바뀝니다」 한 줄로 설명된다 —
 * 「같은 과목만 교체」는 자유 제목의 문자열 비교가 필요하고, 세대(batch) 테이블은 엔티티를 하나 더 만든다.
 * 여러 시험을 병행하는 사용은 지금 요구에 없다.
 *
 * <p>과거 항목은 <b>어떤 경로로도 지워지지 않는다</b> — 지난 계획은 기록이다. 수동 추가는 교체가 아니라
 * 순수 추가라 이 규칙 밖이다(AI가 꺼져 있어도 화면이 쓰이는 폴백 경로).
 */
@Service
@Transactional
public class StudyPlanService {

    /** 한 번에 담을 수 있는 일정 일수 상한 — 1년 + 하루(윤년 여유). 그보다 먼 계획은 계획이 아니다. */
    public static final int MAX_DAYS = 366;

    /** 적용할 하루치 — 날짜와 그날 할 일 한 줄. */
    public record PlanDay(LocalDate date, String task) {
    }

    /**
     * @param applied 새로 넣은 항목 수
     * @param removed 교체로 지운 「오늘 이후」 항목 수 — 화면이 적용 전 확인 문구에 쓴다
     */
    public record ApplyResult(int applied, int removed) {
    }

    private final StudyPlanItemRepository planItemRepository;

    public StudyPlanService(StudyPlanItemRepository planItemRepository) {
        this.planItemRepository = planItemRepository;
    }

    /** 그 달의 일정(날짜 오름차순). 빈 달은 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<StudyPlanItem> month(User user, YearMonth month) {
        return planItemRepository.findByUserAndPlanDateBetweenOrderByPlanDateAsc(
                user, month.atDay(1), month.atEndOfMonth());
    }

    /**
     * 일정 한 줄을 <b>추가</b>한다(교체가 아니다). 미래 날짜가 정상이고, 과거에도 하한을 두지 않는다 —
     * 지난 주를 나중에 정리하는 것은 정당한 사용이다({@code StudyCalendarService.setCheck}와 같은 규율).
     *
     * @throws IllegalArgumentException 과목·할 일이 비었거나 길이를 넘는 경우(문구가 그대로 400 본문)
     */
    public StudyPlanItem add(User user, LocalDate date, StudyBook book, String subject, String task) {
        return planItemRepository.save(StudyPlanItem.of(user, date, book, subject, task));
    }

    /**
     * 내 일정 한 줄을 지운다.
     *
     * @return 지웠으면 {@code true}, <b>없거나 남의 것이면</b> {@code false}(존재 비노출 — 컨트롤러가 404로 옮긴다)
     */
    public boolean delete(User user, Long id) {
        return planItemRepository.findByIdAndUser(id, user)
                .map(item -> {
                    planItemRepository.delete(item);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 「오늘 이후 전부 교체」 — 오늘(포함) 이후 항목을 지우고 새 일정을 넣는다. 과거는 손대지 않는다.
     *
     * <p><b>검증이 삭제보다 먼저다</b> — 순서가 반대면 잘못된 입력 하나에 남은 일정이 통째로 사라지고
     * 복구할 길이 없다(빈 목록 거부도 같은 이유의 가드다: 「적용」이 조용한 전체 삭제가 되면 안 된다).
     *
     * @throws IllegalArgumentException 빈 목록 · 오늘보다 이른 날짜 · 같은 날짜 중복 · {@value #MAX_DAYS}일 초과
     *                                  · 과목·할 일 검증 위반 — 문구가 그대로 400 본문이 된다
     */
    public ApplyResult applyReplacingFuture(User user, LocalDate today, String subject, StudyBook book,
                                            List<PlanDay> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("담을 일정이 없어요");
        }
        if (days.size() > MAX_DAYS) {
            throw new IllegalArgumentException("일정은 " + MAX_DAYS + "일까지만 담을 수 있어요");
        }
        Set<LocalDate> seen = new HashSet<>();
        for (PlanDay day : days) {
            if (day.date() == null) {
                throw new IllegalArgumentException("날짜가 없어요");
            }
            if (day.date().isBefore(today)) {
                throw new IllegalArgumentException("오늘보다 이른 날짜는 담을 수 없어요");
            }
            if (!seen.add(day.date())) {
                throw new IllegalArgumentException("같은 날짜가 두 번 들어 있어요");
            }
        }
        // 전부 만들어 본 뒤에 지운다 — 과목·할 일 검증이 여기서 터지면 기존 일정은 그대로 남는다.
        List<StudyPlanItem> items = days.stream()
                .map(day -> StudyPlanItem.of(user, day.date(), book, subject, day.task()))
                .toList();

        int removed = planItemRepository.deleteByUserAndPlanDateGreaterThanEqual(user, today);
        planItemRepository.saveAll(items);
        return new ApplyResult(items.size(), removed);
    }
}
