package com.booktimer.web.api;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.security.CurrentUserService;
import com.booktimer.study.ClaudeStudyAssistant;
import com.booktimer.study.StudyRecall;
import com.booktimer.study.StudyRecallService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 백지복습의 문 — 쓰기(저장)와 읽기(조회), 그리고 분석 요청.
 *
 * <p><b>저장과 분석의 권한이 다르다</b>: 저장은 누구나(글쓰기니까), 분석은 관리자가 승인한 사용자만.
 * 그 경계는 {@code StudyRecallService.analyze} 첫 줄의 게이트가 들고, 여기선 상태 코드로만 드러난다.
 *
 * <p>에러 계약은 {@link StudyApiController}·{@link StudyPlanApiController}와 같다 — IAE → 400(문구가 그대로
 * 본문), 없는 것 → 404. 그 밖의 상태(403·409·429·503)는 서비스가 {@link ResponseStatusException}으로 던진다.
 */
@RestController
public class StudyRecallApiController {

    private final CurrentUserService currentUserService;
    private final StudyRecallService recallService;
    private final StudyBookRepository studyBookRepository;

    public StudyRecallApiController(CurrentUserService currentUserService,
                                    StudyRecallService recallService,
                                    StudyBookRepository studyBookRepository) {
        this.currentUserService = currentUserService;
        this.recallService = recallService;
        this.studyBookRepository = studyBookRepository;
    }

    /**
     * 그날 쓴 글 한 장.
     *
     * @return 200 {@link RecallResponse} / 404 그날 쓴 글이 없음(남의 글도 같은 404 — 키가 (나, 날짜)다)
     */
    @GetMapping("/api/study/recall/{date}")
    public ResponseEntity<RecallResponse> get(Principal principal, @PathVariable("date") String date) {
        User user = currentUserService.resolve(principal);
        return recallService.find(user, parseDate(date))
                .map(RecallResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "쓴 글을 찾을 수 없습니다"));
    }

    /**
     * 그날 글을 저장한다(하루 한 장 — 다시 저장하면 덮어쓴다).
     *
     * @return 200 {@link RecallResponse} / 400 미래 날짜·빈 본문·길이 위반 / 404 남의 bookId
     */
    @PostMapping("/api/study/recall")
    public ResponseEntity<RecallResponse> save(Principal principal, @RequestBody SaveRequest request) {
        User user = currentUserService.resolve(principal);
        StudyBook book = ownedBookOrNull(user, request.bookId());
        StudyRecall recall = recallService.save(user, parseDate(request.date()), book,
                request.subject(), request.scope(), request.body(), parseSource(request.source()));
        return ResponseEntity.ok(RecallResponse.from(recall));
    }

    /**
     * 그날 글을 분석한다.
     *
     * @return 200 {@link RecallResponse} / 403 미승인 / 404 없음 / 409 이미 분석됨 / 429 오늘 몫 소진 /
     *         503 AI 꺼짐·응답 없음
     */
    @PostMapping("/api/study/recall/{date}/analyze")
    public ResponseEntity<RecallResponse> analyze(Principal principal, @PathVariable("date") String date) {
        User user = currentUserService.resolve(principal);
        return ResponseEntity.ok(RecallResponse.from(recallService.analyze(user, parseDate(date))));
    }

    /**
     * 사진에 손으로 쓴 메모를 읽어 텍스트로 돌려준다 — <b>저장하지 않는다</b>.
     *
     * <p>응답이 그대로 저장되지 않는 것이 이 문의 요점이다: 읽은 글은 화면의 textarea로 들어가고,
     * 사용자가 틀린 곳을 고쳐 {@code POST /api/study/recall}로 다시 보내야 비로소 서버에 남는다. 그래서
     * 전사와 분석 사이에는 <b>서버 상태가 없다</b>(그 사이 사용자가 창을 닫으면 아무 흔적도 남지 않는다).
     *
     * @param images 1~3장, {@code image/jpeg|png|webp}, 각 3MB 이하
     * @return 200 {@link TranscribeResponse} / 403 미승인 / 400 장수·형식·읽기 실패 / 413 용량 /
     *         429 오늘 몫 소진 / 503 AI 꺼짐·응답 없음
     */
    @PostMapping("/api/study/recall/transcribe")
    public ResponseEntity<TranscribeResponse> transcribe(
            Principal principal,
            @RequestParam(name = "images", required = false) List<MultipartFile> images) {
        User user = currentUserService.resolve(principal);
        ClaudeStudyAssistant.Transcript transcript = recallService.transcribe(user, images);
        return ResponseEntity.ok(new TranscribeResponse(transcript.text(), transcript.unreadable()));
    }

    /** {@link StudyPlanApiController}와 같은 규약 — IAE 메시지가 그대로 400 본문이 되어 화면에 뜬다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * 403·409·429·503의 <b>한국어 사유를 본문으로</b> 돌려준다.
     *
     * <p>이게 없으면 전역 처리기가 {@code error.html}을 렌더해 <b>HTML 문서 전체</b>가 본문이 된다 —
     * 화면은 「승인이 필요해요」 대신 {@code <!DOCTYPE html>…}을 상태줄에 찍는다. 이 문의 실패는 대부분
     * 사용자가 읽고 행동을 바꿀 수 있는 것들이라(승인 신청 · 내일 다시 · 잠시 후 다시) 사유가 화면까지
     * 닿아야 한다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }

    /** 내 공부 책일 때만 — 아니면 404로 존재 비노출. null은 「책 없이」라 정당하다. */
    private StudyBook ownedBookOrNull(User user, Long bookId) {
        if (bookId == null) {
            return null;
        }
        return studyBookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
    }

    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않아요");
        }
    }

    private static StudyRecall.Source parseSource(String source) {
        if (source == null || source.isBlank()) {
            return StudyRecall.Source.TEXT;
        }
        try {
            return StudyRecall.Source.valueOf(source);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 입력 방식이에요");
        }
    }

    /**
     * @param bookId 대상 공부 책(null·생략 = 자유 제목)
     * @param scope  「범위」 — 구멍 판정의 울타리. 비어 있으면 글에 적힌 내용 안에서만 판단한다
     * @param source {@code TEXT} | {@code PHOTO}(생략 시 TEXT)
     */
    public record SaveRequest(String date, Long bookId, String subject, String scope,
                              String body, String source) {
    }

    /**
     * 전사 응답 — <b>이게 전부다</b>. 서버에 남은 것도, 다음 요청이 참조할 식별자도 없다.
     *
     * @param text       읽어 낸 글. 못 읽은 글자는 {@code [?]}로 표시돼 있다
     * @param unreadable 글씨를 전혀 못 읽음 — 이때 {@code text}는 빈 값이고 화면이 안내를 띄운다
     */
    public record TranscribeResponse(String text, boolean unreadable) {
    }

    /**
     * @param summary    분석 ① 정리 — 분석 전이면 {@code null}
     * @param holes      분석 ② 구멍(분석 전이면 빈 배열)
     * @param questions  분석 ③ 다음날 복습문제(분석 전이면 빈 배열)
     * @param analyzedAt 분석 시각 — {@code null}이면 「저장만 함」이다(화면의 분기 기준)
     */
    public record RecallResponse(LocalDate date, Long bookId, String subject, String scope,
                                 String body, StudyRecall.Source source, String summary,
                                 List<String> holes, List<String> questions,
                                 String model, Instant analyzedAt) {

        static RecallResponse from(StudyRecall recall) {
            StudyBook book = recall.getBook();
            return new RecallResponse(recall.getRecallDate(),
                    book == null ? null : book.getId(),
                    recall.getSubject(), recall.getScopeText(), recall.getBody(), recall.getSource(),
                    recall.getSummary(),
                    StudyRecallService.decode(recall.getHolesJson()),
                    StudyRecallService.decode(recall.getQuestionsJson()),
                    recall.getModel(), recall.getAnalyzedAt());
        }
    }
}
