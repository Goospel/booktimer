package com.booktimer.chat;

import com.booktimer.report.Report;
import com.booktimer.report.ReportReason;
import com.booktimer.report.ReportRepository;
import com.booktimer.report.ReportStatus;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 대화방 신고와 운영자의 대본 열람·처리(정책 문서 §2·§6).
 *
 * <p>신고는 기존 {@link Report}(신고자·대상 쌍당 1건)를 그대로 쓰고 <b>방 id만 붙인다</b> — 사유도 기존 선택지다.
 * 신고해도 방은 그대로다(끝내려면 신고자가 차단한다). 신고가 새로 생기거나 처리 끝난 신고가 다시 열리면
 * 자동 제재 판정({@link ChatSanctionService#onChatReport})과 운영자 알림({@link ChatOpsAlertService})을 부른다.
 *
 * <p>운영자는 <b>방을 가리키는 신고로만</b> 대본을 연다(URL이 {@code /admin/reports/{id}/chat}인 이유) —
 * 신고가 없는 대화방은 열람 경로 자체가 없다.
 */
@Service
@Transactional
public class ChatSafetyService {

    static final int DETAIL_MAX = 500; // report.detail 컬럼 길이
    private static final DateTimeFormatter KST =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final ChatSanctionService sanctions;
    private final ChatOpsAlertService opsAlert;
    private final RateLimitService rateLimitService;

    public ChatSafetyService(ChatRoomRepository roomRepository,
                             ChatMessageRepository messageRepository,
                             ReportRepository reportRepository,
                             ChatSanctionService sanctions,
                             ChatOpsAlertService opsAlert,
                             RateLimitService rateLimitService) {
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.sanctions = sanctions;
        this.opsAlert = opsAlert;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 방 안에서 상대를 신고한다. 멤버가 아니면 404(남의 방 존재 비누설), 신고 한도(시간당 10, 프로필 신고와 공유)를
     * 넘으면 429. 같은 상대를 이미 신고했으면 새 행을 만들지 않고 방만 붙이며, 처리 끝난 신고면 미처리로 다시 연다.
     */
    public Report reportRoom(User me, long roomId, String reason, String detail) {
        ChatRoom room = roomRepository.findById(roomId).filter(r -> r.has(me)).orElseThrow(ChatException::notFound);
        if (!rateLimitService.allow(RateLimitAction.REPORT, me.getId())) {
            throw ChatException.rateLimited();
        }
        User partner = room.partnerOf(me);
        Report report = reportRepository.findByReporterAndReported(me, partner).orElse(null);
        boolean fresh;
        if (report == null) {
            report = reportRepository.save(Report.of(me, partner, ReportReason.from(reason), clip(detail)));
            fresh = true;
        } else {
            fresh = report.getChatRoomId() == null || report.getStatus() == ReportStatus.RESOLVED;
            report.reopen();
        }
        report.attachChatRoom(room.getId());
        if (fresh) {
            sanctions.onChatReport(partner);
            opsAlert.notifyNewChatReport();
        }
        return report;
    }

    /** 신고가 가리키는 방의 대본(복호화). 방 없는 신고·지워진 방이면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<Transcript> transcript(long reportId) {
        return reportRepository.findById(reportId)
                .filter(r -> r.getChatRoomId() != null)
                .flatMap(r -> roomRepository.findById(r.getChatRoomId()).map(room -> toTranscript(r, room)));
    }

    /** 수사 협조용 텍스트 내보내기(정책 문서 §6). 복호화 안 되는 행은 그렇다고 적는다. */
    @Transactional(readOnly = true)
    public Optional<String> exportText(long reportId) {
        return transcript(reportId).map(t -> {
            StringBuilder sb = new StringBuilder()
                    .append("BookTimer 대화 기록 — 신고 #").append(t.reportId()).append(" · 대화방 #").append(t.roomId()).append('\n')
                    .append("신고자 ").append(t.reporter()).append(" → 대상 ").append(t.reported())
                    .append(" · 사유 ").append(t.reason()).append(" · 접수 ").append(KST.format(t.reportedAt())).append(" KST\n")
                    .append("법적 보존: ").append(t.legalHold() ? "예" : "아니오").append('\n')
                    .append("----\n");
            for (Line l : t.lines()) {
                sb.append('[').append(KST.format(l.createdAt())).append(" KST] ").append(l.sender()).append(": ")
                        .append(l.body() == null ? "(복호화할 수 없는 메시지)" : l.body())
                        .append(l.flagged() ? "  [연락처·링크 감지]" : "").append('\n');
            }
            return sb.toString();
        });
    }

    /** 처리 완료로 닫고 고른 조치를 대상에게 적용한다. */
    public void resolve(long reportId, ChatSanctionService.Action action) {
        Report report = reportRepository.findById(reportId).orElseThrow(ChatException::notFound);
        report.resolve(action.name());
        sanctions.apply(report.getReported(), action);
    }

    public void setLegalHold(long reportId, boolean hold) {
        reportRepository.findById(reportId).orElseThrow(ChatException::notFound).setLegalHold(hold);
    }

    private Transcript toTranscript(Report r, ChatRoom room) {
        List<Line> lines = messageRepository.findByRoomOrderByIdAsc(room).stream()
                .map(m -> new Line(m.getId(), handle(m.getSender()), m.getBody(), m.isFlagged(), m.getCreatedAt()))
                .toList();
        return new Transcript(r.getId(), room.getId(), handle(r.getReporter()), handle(r.getReported()),
                r.getReported().getLoginId(), r.getReason().getLabel(), r.getDetail(), r.getStatus(),
                r.getResolution(), r.isLegalHold(), r.getCreatedAt(), lines);
    }

    private static String handle(User u) {
        return u.getLoginId() != null ? "@" + u.getLoginId() : u.getNickname();
    }

    private static String clip(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String s = detail.strip();
        return s.length() > DETAIL_MAX ? s.substring(0, DETAIL_MAX) : s;
    }

    /** @param body 복호화할 수 없으면 {@code null} */
    public record Line(long id, String sender, String body, boolean flagged, Instant createdAt) {
    }

    public record Transcript(long reportId, long roomId, String reporter, String reported, String reportedLoginId,
                             String reason, String detail, ReportStatus status, String resolution,
                             boolean legalHold, Instant reportedAt, List<Line> lines) {
    }
}
