package com.booktimer.chat;

import com.booktimer.report.Report;
import com.booktimer.report.ReportRepository;
import com.booktimer.report.ReportStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 차단으로 닫힌 대화방의 보존 삭제(정책 문서 §5·§6, 처리방침 「대화」).
 *
 * <p>대상: {@code CLOSED}이고 닫힌 지 {@link ChatProperties#getRetentionDays()}일이 지난 방.
 * 제외: 그 방을 가리키는 신고가 <b>미처리</b>이거나 <b>법적 보존</b>인 방 — 증거가 처리·해제 전에 사라지지 않게.
 * 처리 끝난(보존 아닌) 신고가 가리키는 방은 지우고, 신고는 조치 기록으로 남기되 방 참조만 푼다(FK).
 *
 * <p>회원 탈퇴는 이 규칙과 무관하게 즉시 삭제다({@code AccountService.purge}) — 법적 보존이 막는 것은
 * <b>자동</b> 삭제뿐이라는 것이 정책 문서의 약속이다.
 */
@Service
@Transactional
public class ChatRetentionService {

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final ChatProperties properties;

    public ChatRetentionService(ChatRoomRepository roomRepository,
                                ChatMessageRepository messageRepository,
                                ReportRepository reportRepository,
                                ChatProperties properties) {
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.properties = properties;
    }

    /** @return 지운 방 수 */
    public int purgeExpiredClosedRooms(Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(properties.getRetentionDays()));
        Set<Long> expired = roomRepository.findByStatusAndClosedAtBefore(ChatRoom.Status.CLOSED, cutoff).stream()
                .map(ChatRoom::getId)
                .collect(Collectors.toSet());
        if (expired.isEmpty()) {
            return 0;
        }
        List<Report> refs = reportRepository.findByChatRoomIdIn(expired);
        Set<Long> kept = refs.stream()
                .filter(r -> r.getStatus() == ReportStatus.OPEN || r.isLegalHold())
                .map(Report::getChatRoomId)
                .collect(Collectors.toSet());
        Set<Long> doomed = expired.stream().filter(id -> !kept.contains(id)).collect(Collectors.toSet());
        if (doomed.isEmpty()) {
            return 0;
        }
        refs.stream().filter(r -> doomed.contains(r.getChatRoomId())).forEach(Report::detachChatRoom);
        messageRepository.deleteByRoomIdIn(doomed); // flushAutomatically — 신고 참조 해제가 먼저 반영된다
        roomRepository.deleteAllByIdInBatch(doomed);
        return doomed.size();
    }
}
