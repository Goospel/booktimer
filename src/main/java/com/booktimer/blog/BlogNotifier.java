package com.booktimer.blog;

import com.booktimer.blog.Moderation.Action;
import com.booktimer.email.EmailDispatcher;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 블로그 처리 알림 메일(B2b · 계획 §4.6 · §6.6 · U-154 ②③).
 *
 * <ul>
 *   <li>받는 주소 = <b>검증된 진짜 주소만</b>(합성 · 미검증 0 — {@code AppLinkNotificationService}와 같은 규칙). 앱 안 표시는 따로다.</li>
 *   <li>운영자 메일 본문 = 건수와 관리자 화면 링크<b>만</b> — 신고자 · 대상 · 이름 · 사유 · 부가 설명 · 원문 0(처리방침 8절 「신고 내역은
 *       서울 리전」이 참이려면). 그래서 운영자 메서드는 건수만 받는다.</li>
 *   <li>발송 한 곳 {@link #send}: 트랜잭션 동기화가 있으면 <b>커밋 뒤</b>(롤백된 처리에 메일 0), 없으면 <b>곧바로</b> — 매일 09:00
 *       스케줄러는 트랜잭션 밖에서 부른다({@code ChatOpsAlertService}처럼 동기화가 없을 때 return하면 요약 메일이 늘 0통이다).</li>
 * </ul>
 */
@Component
public class BlogNotifier {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EmailDispatcher dispatcher;
    private final UserRepository userRepository;
    private final String baseUrl;

    public BlogNotifier(EmailDispatcher dispatcher, UserRepository userRepository,
                        @Value("${booktimer.base-url:http://localhost:8080}") String baseUrl) {
        this.dispatcher = dispatcher;
        this.userRepository = userRepository;
        this.baseUrl = baseUrl;
    }

    /** 열린 블로그 신고가 0 → 1이 될 때(부르는 쪽이 가른다). */
    public void newReport(long open) {
        toAdmins("[책생글] 새 " + BlogNames.SPACE + " 신고", "새 신고 " + open + "건 — 관리자 화면에서 확인해 주세요");
    }

    /** 매일 09:00 KST 요약 — 부르는 쪽이 둘 중 하나라도 1+일 때만. */
    public void daily(long open, long expired) {
        toAdmins("[책생글] " + BlogNames.SPACE + " 신고 · 임시조치 요약",
                "열린 신고 " + open + "건 · 기간이 끝난 임시조치 " + expired + "건 — 관리자 화면에서 확인해 주세요");
    }

    /**
     * 게시자 — 가리기 · 다시 보이기 · 삭제. {@code note}는 COPYRIGHT면 중단 요청서 사본(처리방침 6절).
     *
     * @param unit  「글」 · 「댓글」 · 「인용」
     * @param title 그 글의 제목(REMOVE면 지우기 전에 읽어 둔 것) — 쪽지 박스면 null
     */
    public void poster(User poster, Action action, String unit, Instant until, String title, String note) {
        if (!deliverable(poster)) return;
        String what = switch (action) {
            case TEMP -> "운영 정책으로 가려졌어요(" + date(until) + "까지 — 그때까지 이의가 없으면 지워요)";
            case COPYRIGHT -> "권리자의 복제 · 전송 중단 요구로 가려졌어요 — 정당한 권리가 있으면 [문의하기]로 다시 보이게 해 달라고 요청해 주세요"
                    + "(관련 법령의 절차를 따릅니다)";
            case POLICY -> "운영 정책으로 가려졌어요";
            case RESTORE -> "다시 보여요";
            case REMOVE -> "운영 정책으로 지워졌어요";
        };
        StringBuilder html = new StringBuilder("<p>").append(escape(poster.getNickname())).append("님, 올리신 ").append(BlogNames.SPACE)
                .append(' ').append(unit).append("이 ").append(what).append(".</p>");
        if (title != null) html.append("<p>글 「").append(escape(title)).append("」</p>");
        if (note != null && !note.isBlank()) html.append("<p>").append(escape(note)).append("</p>");
        html.append("<p>이의는 책생글 앱 설정의 [문의하기]로 보내 주세요.</p>");
        send(poster.getEmail(), "[책생글] 올리신 " + BlogNames.SPACE + " " + unit + " 처리 안내", html.toString());
    }

    /** 신고자 — 처리 결과만(대상 내용 0). @param result {@code HIDDEN} · {@code REMOVED} · {@code NONE} */
    public void reporter(User reporter, BlogReport.Target target, String result) {
        if (!deliverable(reporter)) return;
        String what = switch (result) {
            case "HIDDEN" -> "가렸어요";
            case "REMOVED" -> "지웠어요";
            default -> "조치하지 않았어요";
        };
        send(reporter.getEmail(), "[책생글] " + BlogNames.SPACE + " 신고 처리 결과", "<p>신고하신 " + BlogNames.SPACE + " "
                + (target == BlogReport.Target.POST ? "글" : "댓글") + "을 검토했어요 — 결과: " + what + "</p>");
    }

    private void toAdmins(String subject, String line) {
        String link = baseUrl + "/admin/reports";
        String html = "<p>" + line + "</p><p><a href=\"" + link + "\">" + link + "</a></p>";
        userRepository.findByRole(Role.ADMIN).stream().filter(BlogNotifier::deliverable).forEach(a -> send(a.getEmail(), subject, html));
    }

    void send(String to, String subject, String html) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatcher.dispatch(to, subject, html);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.dispatch(to, subject, html);
            }
        });
    }

    private static boolean deliverable(User u) {
        return u.isEmailVerified() && !u.hasSyntheticEmail();
    }

    private static String date(Instant until) {
        if (until == null) return "기한";
        ZonedDateTime d = until.atZone(SEOUL);
        return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
