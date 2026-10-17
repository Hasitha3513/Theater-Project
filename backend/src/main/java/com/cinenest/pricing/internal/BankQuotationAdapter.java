package com.cinenest.pricing.internal;
import com.cinenest.pricing.BankQuotation;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
class BankQuotationAdapter implements BankQuotation {
 private final QuoteRepository repository;private final PublicationRepository publications;private final com.cinenest.booking.QuotationHolds holds;
 BankQuotationAdapter(QuoteRepository repository,PublicationRepository publications,com.cinenest.booking.QuotationHolds holds){this.repository=repository;this.publications=publications;this.holds=holds;}
 public Optional<Facts> accepted(UUID actor,UUID id,long expected,String hash){
  var q=repository.quote(id,false).filter(v->v.owner().equals(actor));if(q.isEmpty())return Optional.empty();
  publications.lock(q.get().venue(),q.get().pkg());holds.lockAndInspect(actor,q.get().hold());q=repository.quote(id,true);
  var r=repository.revision(id,q.get().revision());
  if(r.revision()!=expected||!r.hash().equals(hash)||!r.method().equals("BANK")||!r.expires().isAfter(repository.now())||repository.accepted(id).orElse(-1L)!=expected)return Optional.empty();
  return Optional.of(new Facts(id,actor,q.get().venue(),q.get().hold(),expected,r.holdRevision(),r.hash(),r.expires(),Map.copyOf(r.snapshot())));
 }
}
