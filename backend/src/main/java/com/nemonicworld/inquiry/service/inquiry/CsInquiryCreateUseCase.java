package com.nemonicworld.inquiry.service.inquiry;

import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.inquiry.dto.request.CsInquiryCreateRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryCreateResponse;
import com.nemonicworld.inquiry.entity.CsInquiryStatus;
import com.nemonicworld.inquiry.entity.CsInquiryType;
import com.nemonicworld.inquiry.repository.CsInquiryInsertCommand;
import com.nemonicworld.inquiry.repository.CsInquiryRepository;
import com.nemonicworld.inquiry.service.support.CsInquiryJsonSupport;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CsInquiryCreateUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final CsInquiryRepository csInquiryRepository;
    private final CsInquiryJsonSupport csInquiryJsonSupport;

    public CsInquiryCreateUseCase(AnonymousUserResolver anonymousUserResolver, CsInquiryRepository csInquiryRepository,
        CsInquiryJsonSupport csInquiryJsonSupport) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.csInquiryRepository = csInquiryRepository;
        this.csInquiryJsonSupport = csInquiryJsonSupport;
    }

    @Transactional
    public CsInquiryCreateResponse createInquiry(String userUuid, String userAgent, String referer,
        CsInquiryCreateRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuid);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        String type = CsInquiryType.fromValue(request.type()).getValue();
        CsInquiryInsertCommand command = new CsInquiryInsertCommand(user.getId(), type,
            normalizeRequiredTrimmed(request.title()), normalizeOptional(request.content()),
            normalizeOptional(request.email()), csInquiryJsonSupport.serializeAttachments(request.attachments()),
            csInquiryJsonSupport.serializeMeta(request.meta(), userAgent, referer, now), CsInquiryStatus.NEW.getValue(),
            now, now);

        CsInquiryCreateResponse response = CsInquiryCreateResponse.from(csInquiryRepository.insertInquiry(command));
        StructuredEventLogger.apiBusiness("inquiry_created", "inquiry", user.getId().toString(),
            StructuredEventLogger.metadata("inquiry_id", response.id(), "type", type, "has_email",
                StringUtils.hasText(request.email()), "attachment_count",
                request.attachments() == null ? 0 : request.attachments().size(), "result", "success"));

        return response;
    }

    private String normalizeRequiredTrimmed(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }
}
