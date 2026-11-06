package com.cinenest.pricing.internal;

import java.util.*;

/** Non-activating Pricing-owned policy contract. Monetary/revision transport uses decimal strings. */
final class CommercialPolicyDraft {
    static final String NOTICE="Reviewed draft — not active; commercial issuance disabled.";
    static final List<String> DOMAINS=List.of("FINANCE","LEGAL","OPERATIONS");
    static final Set<String> FIELDS=Set.of("title","effectiveFromProposal","effectiveUntilProposal",
        "taxApplicability","taxType","taxRateBasisPoints","taxAmountMinor","taxTreatment","taxBasis","taxRounding",
        "paymentMethods","feeChannels","fees","cancellationEligibility","cancellationCutoffMinutes","cancellationTimeBasis",
        "noShowTreatment","exceptionHandling","exceptionNotes","refundEligibility","refundCalculation","refundBasis",
        "refundRateBasisPoints","refundAmountMinor","nonRefundableComponents","refundDestination","suppliedReferences","submittedForReview");
    static final class Failure extends RuntimeException {
        final int status;
        Failure(int status,String message){super(message);this.status=status;}
    }
    static Failure invalid(){return new Failure(400,"Invalid commercial-policy fields");}
    static Map<String,Object> flags(){return Map.of("commercialIssuanceEnabled",false,"checkoutEnabled",false,"customerAccessEnabled",false,"notice",NOTICE);}
}
