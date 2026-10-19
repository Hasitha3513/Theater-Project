package com.cinenest.pricing.internal;
import com.cinenest.pricing.BankQuotation;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
class BankQuotationAdapter implements BankQuotation {
 private final QuoteRepository repository;private final PublicationRepository publications;private final com.cinenest.booking.QuotationHolds holds;
 BankQuotationAdapter(QuoteRepository repository,PublicationRepository publications,com.cinenest.booking.QuotationHolds holds){this.repository=repository;this.publications=publications;this.holds=holds;}
 private String refusal(QuoteRepository.Quote q,QuoteRepository.Revision r,Long expected,String hash){
  if(expected!=null&&r.revision()!=expected)return "QUOTE_REVISION_CHANGED";
  if(hash!=null&&!r.hash().equals(hash))return "QUOTE_HASH_CHANGED";
  if(repository.accepted(q.id()).orElse(-1L)!=r.revision())return "QUOTE_NOT_ACCEPTED";
  if(!r.method().equals("BANK"))return "BANK_METHOD_REQUIRED";
  if(!BankQuotation.unexpired(r.expires(),repository.now()))return "QUOTE_EXPIRED";
  return "ELIGIBLE";
 }
 private Facts facts(QuoteRepository.Quote q,QuoteRepository.Revision r){return new Facts(q.id(),q.owner(),q.venue(),q.hold(),r.revision(),r.holdRevision(),r.hash(),r.expires(),Map.copyOf(r.snapshot()));}
 public Optional<Observation> observe(UUID actor,UUID id){
  return repository.quote(id,false).filter(q->q.owner().equals(actor)).map(q->{var r=repository.revision(id,q.revision());return new Observation(facts(q,r),refusal(q,r,null,null));});
 }
 public Optional<Facts> requestBound(UUID actor,UUID id,long expected,String hash){
  var q=repository.quote(id,false).filter(v->v.owner().equals(actor));if(q.isEmpty())return Optional.empty();
  publications.lock(q.get().venue(),q.get().pkg());holds.lockAndInspect(actor,q.get().hold());q=repository.quote(id,true);
  var r=repository.revision(id,expected);if(!r.hash().equals(hash)||!r.method().equals("BANK")||repository.accepted(id).orElse(-1L)!=expected)return Optional.empty();
  return Optional.of(new Facts(id,actor,q.get().venue(),q.get().hold(),expected,r.holdRevision(),r.hash(),r.expires(),Map.copyOf(r.snapshot())));
 }
 public Optional<Facts> accepted(UUID actor,UUID id,long expected,String hash){
  var q=repository.quote(id,false).filter(v->v.owner().equals(actor));if(q.isEmpty())return Optional.empty();
  publications.lock(q.get().venue(),q.get().pkg());holds.lockAndInspect(actor,q.get().hold());q=repository.quote(id,true);
  var r=repository.revision(id,q.get().revision());
  if(!refusal(q.get(),r,expected,hash).equals("ELIGIBLE"))return Optional.empty();
  return Optional.of(facts(q.get(),r));
 }
}
