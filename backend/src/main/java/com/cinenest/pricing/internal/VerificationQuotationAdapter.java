package com.cinenest.pricing.internal;
import com.cinenest.pricing.VerificationQuotation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
class VerificationQuotationAdapter implements VerificationQuotation {
    private final QuoteRepository quotes;
    VerificationQuotationAdapter(QuoteRepository quotes){this.quotes=quotes;}
    public Optional<Context> ownedSynthetic(UUID actor,UUID quote){
        return quotes.quote(quote,false).filter(q->q.owner().equals(actor)).map(q->{quotes.revision(q.id(),q.revision());return new Context(q.id(),q.owner(),q.venue());});
        // All V7 quotations reference a CHECK(synthetic) policy; no commercial quotation path exists.
    }
}
