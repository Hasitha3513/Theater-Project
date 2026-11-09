package com.cinenest.pricing.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-synthetic")
class CommercialPolicyApprovalService {
    private final CommercialPolicyApprovalVerifier verifier;
    private final CommercialPolicyDraftService drafts;
    private final JdbcCommercialPolicyApprovalRepository repository;

    CommercialPolicyApprovalService(CommercialPolicyApprovalVerifier verifier,
                                    CommercialPolicyDraftService drafts,
                                    JdbcCommercialPolicyApprovalRepository repository) {
        this.verifier = verifier;
        this.drafts = drafts;
        this.repository = repository;
    }

    @Transactional
    public Map<String, Object> intake(UUID actor, UUID venue, UUID policy, String envelope) {
        if (actor == null) {
            throw new CommercialPolicyDraft.Failure(401, "Sign-in required");
        }
        var approval = verifier.verify(envelope, Instant.now());
        UUID signer = verifier.signer(approval);
        if (actor.equals(signer) || actor.equals(approval.reviewer())
                || !venue.equals(approval.venue()) || !policy.equals(approval.policy())) {
            throw new CommercialPolicyDraft.Failure(403, "Trusted approval scope denied");
        }
        var grant = drafts.require(actor, venue, "commercial.policy.review."
            + approval.domain().toLowerCase(Locale.ROOT), true);
        return repository.append(approval, grant, signer, envelopeHash(envelope), Instant.now());
    }

    private static String envelopeHash(String envelope) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(envelope.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
