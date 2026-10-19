package com.cinenest.pricing.internal;
import com.cinenest.pricing.VerificationQuotation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
class VerificationQuotationAdapter implements VerificationQuotation {
    private final QuoteService service;
    VerificationQuotationAdapter(QuoteService service){this.service=service;}
    public Optional<Context> ownedSynthetic(UUID actor,UUID quote){
        try{return service.verificationContext(actor,quote);}
        catch(DraftFailure unavailable){return Optional.empty();} // No foreign-module exception or private detail escapes.
    }
}
