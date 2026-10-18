package com.cinenest.pricing.internal;
import com.cinenest.booking.AmendmentGuards;import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
class AmendmentPricingAdapter implements AmendmentGuards.Pricing {
 private final QuoteService quotes;AmendmentPricingAdapter(QuoteService quotes){this.quotes=quotes;}
 private static void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Caller transaction required");}
 public AmendmentGuards.Quote validate(UUID actor,UUID original,UUID hold,UUID quote,long revision,String hash,boolean accept){transaction();try{return quotes.amendment(actor,original,hold,quote,revision,hash,accept);}catch(DraftFailure invalid){throw new AmendmentGuards.Rejected();}}
 public UUID packageId(UUID original,UUID actor){transaction();return quotes.amendmentPackage(actor,original);}
}
