package com.booktimer.email;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SES 반송·불만 통지 처리 — 영구 반송/불만은 억제, 일시 반송은 무시, 불만은 마케팅 동의까지 철회함을 못 박는다.
 */
@ExtendWith(MockitoExtension.class)
class SesBounceComplaintHandlerTest {

    @Mock
    EmailSuppressionService suppressionService;

    @Mock
    UserRepository userRepository;

    private SesBounceComplaintHandler handler() {
        return new SesBounceComplaintHandler(suppressionService, userRepository);
    }

    @Test
    void permanentBounce_suppressesEachRecipientAsBounce() {
        String json = """
                {"notificationType":"Bounce","bounce":{"bounceType":"Permanent","bounceSubType":"General",
                "bouncedRecipients":[{"emailAddress":"a@example.com"},{"emailAddress":"b@example.com"}]}}
                """;

        handler().handle(json);

        verify(suppressionService).suppress("a@example.com", SuppressionReason.BOUNCE, "Permanent/General");
        verify(suppressionService).suppress("b@example.com", SuppressionReason.BOUNCE, "Permanent/General");
    }

    @Test
    void transientBounce_isNotSuppressed() {
        String json = """
                {"notificationType":"Bounce","bounce":{"bounceType":"Transient","bounceSubType":"MailboxFull",
                "bouncedRecipients":[{"emailAddress":"temp@example.com"}]}}
                """;

        handler().handle(json);

        verify(suppressionService, never()).suppress(any(), any(), any());
    }

    @Test
    void complaint_suppressesAndWithdrawsMarketingConsent() {
        String json = """
                {"notificationType":"Complaint","complaint":{"complaintFeedbackType":"abuse",
                "complainedRecipients":[{"emailAddress":"spam@example.com"}]}}
                """;
        User user = org.mockito.Mockito.mock(User.class);
        when(userRepository.findByEmail("spam@example.com")).thenReturn(Optional.of(user));

        handler().handle(json);

        verify(suppressionService).suppress(eq("spam@example.com"), eq(SuppressionReason.COMPLAINT), any());
        verify(user).withdrawMarketingConsent();
        verify(userRepository).save(user);
    }

    @Test
    void complaint_unknownUser_stillSuppressesNoConsentChange() {
        String json = """
                {"notificationType":"Complaint","complaint":{"complaintFeedbackType":"abuse",
                "complainedRecipients":[{"emailAddress":"ghost@example.com"}]}}
                """;
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        handler().handle(json);

        verify(suppressionService).suppress(eq("ghost@example.com"), eq(SuppressionReason.COMPLAINT), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownNotificationType_isIgnored() {
        handler().handle("{\"notificationType\":\"DeliveryDelay\"}");

        verify(suppressionService, never()).suppress(any(), any(), any());
    }
}
