package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.PackageConstraints;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class QuoteService {
    private final QuoteRepository store;
    private final PublicationRepository publications;
    private final DraftRepository drafts;
    private final QuotationHolds holds;
    private final PackageConstraints rooms;
    private final ScopedAuthorization identity;
    private final TransactionTemplate tx;
    private final Environment environment;
    private final boolean enabled;
    private final String profile,reference;
    private final long tax;
    private final int rate,seconds;
    QuoteService(QuoteRepository store,PublicationRepository publications,DraftRepository drafts,QuotationHolds holds,PackageConstraints rooms,ScopedAuthorization identity,PlatformTransactionManager transactions,Environment environment,
        @Value("${cinenest.pricing.synthetic-quotes.enabled:false}") boolean enabled,
        @Value("${cinenest.pricing.synthetic-quotes.tax-profile:}") String profile,
        @Value("${cinenest.pricing.synthetic-quotes.tax-minor:-1}") long tax,
        @Value("${cinenest.pricing.synthetic-quotes.approval-reference:}") String reference,
        @Value("${cinenest.pricing.synthetic-quotes.rate-count:0}") int rate,
        @Value("${cinenest.pricing.synthetic-quotes.rate-seconds:0}") int seconds) {
        this.store=store;this.publications=publications;this.drafts=drafts;this.holds=holds;this.rooms=rooms;this.identity=identity;this.tx=new TransactionTemplate(transactions);this.environment=environment;this.enabled=enabled;this.profile=profile;this.tax=tax;this.reference=reference;this.rate=rate;this.seconds=seconds;
    }
    private void configured(){if(!enabled||!Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation"))||!profile.equals("SYNTHETIC_FIXED_MINOR_V1")||tax<0||reference.isBlank()||rate<=0||seconds<=0)throw new DraftFailure(503,"Synthetic quotation profile unavailable; commercial issuance disabled");}
    private void eligible(UUID actor){if(!identity.eligible(actor,true))throw new DraftFailure(403,"Access denied");}
    private QuotationHolds.Facts ownedHold(UUID actor,UUID id,boolean lock){var h=(lock?holds.lockAndInspect(actor,id):holds.locate(id)).orElseThrow(()->new DraftFailure(404,"Hold not found"));if(!h.owner().equals(actor))throw new DraftFailure(404,"Hold not found");return h;}
    private QuoteRepository.Quote owned(UUID actor,UUID id,boolean lock){var q=store.quote(id,lock).orElseThrow(()->new DraftFailure(404,"Quote not found"));if(!q.owner().equals(actor))throw new DraftFailure(404,"Quote not found");return q;}
    private void active(QuotationHolds.Facts h){if(!h.state().equals("ACTIVE")||!store.now().isBefore(h.expires()))throw new DraftFailure(409,"Hold expired or invalidated; select inventory again");}
    private void compatible(QuotationHolds.Facts h,Map<String,Object> snapshot){
        if(!PublicationService.rooms(snapshot).contains(h.room()))throw new DraftFailure(409,"Package does not cover held room");
        var facts=rooms.inspect(h.venue(),Set.of(h.room()),false,false).orElseThrow(()->new DraftFailure(409,"Room/package configuration invalidated"));
        if(h.guests()>facts.rooms().getFirst().capacity())throw new DraftFailure(409,"Room capacity changed");
        var rates=drafts.options(h.venue());
        for(var option:DraftRules.objects(snapshot,"options")) {
            var found=rates.stream().filter(r->r.get("serviceId").toString().equals(option.get("serviceId").toString())&&r.get("rateVersionId").toString().equals(option.get("rateVersionId").toString())).findFirst().orElseThrow(()->new DraftFailure(409,"Service unavailable"));
            if(!facts.rooms().getFirst().facilities().contains(found.get("requiredFacilityCode")))throw new DraftFailure(409,"Service incompatible with held room");
        }
    }
    private Map<String,Object> published(QuotationHolds.Facts h,UUID pkg,UUID version){return publications.catalog(h.venue()).stream().filter(p->p.get("packageId").equals(pkg)&&p.get("versionId").equals(version)&&!h.start().isBefore(Instant.parse(p.get("sessionFrom").toString()))&&!h.end().isAfter(Instant.parse(p.get("sessionTo").toString()))).findFirst().orElseThrow(()->new DraftFailure(409,"Published version no longer applicable"));}
    private UUID policy(){UUID id=UUID.nameUUIDFromBytes((profile+":"+tax+":BANK=0:GATEWAY=0:"+reference).getBytes(StandardCharsets.UTF_8));store.policy(id,profile,tax,reference);return id;}
    private static String hash(String action,UUID id,Map<String,Object> input){return PublicationService.hash(Map.of("action",action,"target",id==null?"NEW":id.toString(),"input",input));}
    Map<String,Object> command(UUID actor,UUID id,String action,Map<String,Object> input){
        configured();
        UUID key=DraftRules.id(DraftRules.text(input,"idempotencyKey",36,true));String requestHash=hash(action,id,input);
        // Separate committed admission budget; failed commands cannot evade it. Actor lock serializes budget and replay.
        boolean admitted=Boolean.TRUE.equals(tx.execute(status->{eligible(actor);var prior=store.command(actor,key);if(prior.isPresent())return true;if(store.attempts(actor,seconds)>=rate)return false;store.audit(actor,null,null,"ATTEMPT");return true;}));
        if(!admitted)throw new DraftFailure(429,"Too many quotation attempts; retry later");
        return tx.execute(status->{
            eligible(actor);var prior=store.command(actor,key);
            if(prior.isPresent()){
                var c=prior.get();if(!c.action().equals(action)||!c.hash().equals(requestHash))throw new DraftFailure(409,"Idempotency key already used");
                var q=owned(actor,c.quote(),false);publications.lock(q.venue(),q.pkg());var h=ownedHold(actor,q.hold(),true);q=owned(actor,q.id(),true);return view(q,store.revision(q.id(),c.revision()),h);
            }
            if(action.equals("CREATE"))return create(actor,input,key,requestHash);
            var q=owned(actor,id,false);publications.lock(q.venue(),q.pkg());var h=ownedHold(actor,q.hold(),true);q=owned(actor,id,true);
            DraftRules.keys(input,action.equals("ACCEPT")?new String[]{"expectedRevision","contentHash","disclosures","idempotencyKey"}:new String[]{"expectedRevision","optionalServiceIds","paymentMethod","idempotencyKey"});
            long expected=DraftRules.revision(input,"expectedRevision");if(q.revision()!=expected)throw new DraftFailure(409,"Quote changed; review the current revision");
            if(store.accepted(q.id()).isPresent())throw new DraftFailure(409,"Quote already accepted; immutable snapshot retained");
            var previous=store.revision(q.id(),expected);active(h);if(!store.now().isBefore(previous.expires())||h.revision()!=previous.holdRevision())throw new DraftFailure(409,"Quote expired or hold changed");
            compatible(h,PublicationService.object(previous.snapshot(),"package"));
            if(action.equals("ACCEPT")) {
                if(!previous.hash().equals(input.get("contentHash"))||!QuoteRules.DISCLOSURES.equals(input.get("disclosures")))throw new DraftFailure(409,"Explicit review of exact quotation disclosures required");
                // Read database time after all guards/locks; original hold is never extended.
                active(h);if(!store.now().isBefore(previous.expires()))throw new DraftFailure(409,"Quote expired");
                store.accept(q,actor,previous.hash());store.audit(actor,id,expected,"ACCEPT");store.command(actor,key,action,requestHash,id,expected);return view(q,previous,h);
            }
            var snapshot=QuoteRules.calculate(PublicationService.object(previous.snapshot(),"package"),h,QuoteRules.selections(input),method(input),tax);
            snapshot.putAll(context(h));UUID revisedPolicy=policy();snapshot.put("policyId",revisedPolicy.toString());snapshot.put("packageVersionId",previous.version().toString());snapshot.put("revision",expected+1);
            var revision=new QuoteRepository.Revision(Math.addExact(expected,1),previous.version(),revisedPolicy,h.revision(),previous.expires(),method(input),snapshot,PublicationService.hash(snapshot));
            store.append(q,revision);store.audit(actor,id,revision.revision(),"REVISE");store.command(actor,key,action,requestHash,id,revision.revision());return view(owned(actor,id,false),revision,h);
        });
    }
    com.cinenest.booking.AmendmentGuards.Quote amendment(UUID actor,UUID original,UUID target,UUID quote,long revision,String hash,boolean accept){
        configured();eligible(actor);var purchase=owned(actor,original,false);var q=owned(actor,quote,false);
        if(!purchase.pkg().equals(q.pkg())||!q.hold().equals(target)||!purchase.venue().equals(q.venue())||!purchase.room().equals(q.room()))throw new com.cinenest.booking.AmendmentGuards.Rejected();
        publications.lock(q.venue(),q.pkg());var h=ownedHold(actor,target,true);q=owned(actor,quote,true);active(h);
        var originalRevision=store.accepted(original).orElseThrow(com.cinenest.booking.AmendmentGuards.Rejected::new);
        var old=store.revision(original,originalRevision);var proposed=store.revision(quote,q.revision());
        if(q.revision()!=revision||!proposed.hash().equals(hash)||!proposed.version().equals(old.version())||h.revision()!=proposed.holdRevision()||!store.now().isBefore(proposed.expires())||store.accepted(quote).isPresent())throw new com.cinenest.booking.AmendmentGuards.Rejected();
        published(h,q.pkg(),old.version());compatible(h,PublicationService.object(old.snapshot(),"package"));
        for(String key:List.of("package","packageVersionId","guests","optionalServiceIds","lines","totalMinor","currency","paymentMethod"))if(!Objects.equals(old.snapshot().get(key),proposed.snapshot().get(key)))throw new com.cinenest.booking.AmendmentGuards.Rejected();
        active(h);if(!store.now().isBefore(proposed.expires()))throw new com.cinenest.booking.AmendmentGuards.Rejected();
        if(accept){store.accept(q,actor,proposed.hash());store.audit(actor,quote,revision,"AMENDMENT_ACCEPT");}
        return new com.cinenest.booking.AmendmentGuards.Quote(quote,revision,proposed.hash(),proposed.snapshot(),q.pkg());
    }
    UUID amendmentPackage(UUID actor,UUID original){eligible(actor);return owned(actor,original,false).pkg();}
    private String method(Map<String,Object> input){String method=DraftRules.text(input,"paymentMethod",8,true);if(!Set.of("BANK","GATEWAY").contains(method))throw DraftRules.invalid("approved method fee required");return method;}
    private Map<String,Object> create(UUID actor,Map<String,Object> input,UUID key,String hash){
        DraftRules.keys(input,"holdId","packageId","packageVersionId","expectedHoldRevision","optionalServiceIds","paymentMethod","idempotencyKey");
        UUID hold=DraftRules.id(DraftRules.text(input,"holdId",36,true)),pkg=DraftRules.id(DraftRules.text(input,"packageId",36,true)),version=DraftRules.id(DraftRules.text(input,"packageVersionId",36,true));
        var h=ownedHold(actor,hold,false);publications.lock(h.venue(),pkg);h=ownedHold(actor,hold,true);active(h);
        if(h.revision()!=DraftRules.revision(input,"expectedHoldRevision"))throw new DraftFailure(409,"Hold changed");
        if(store.byHold(hold).isPresent())throw new DraftFailure(409,"Quotation already exists for hold; review its revision");
        var publication=published(h,pkg,version);var pkgSnapshot=PublicationService.object(publication,"snapshot");compatible(h,pkgSnapshot);
        var snapshot=QuoteRules.calculate(pkgSnapshot,h,QuoteRules.selections(input),method(input),tax);snapshot.putAll(context(h));UUID policy=policy();snapshot.put("policyId",policy.toString());snapshot.put("packageVersionId",version.toString());snapshot.put("revision",1L);
        var q=new QuoteRepository.Quote(UUID.randomUUID(),actor,hold,h.venue(),h.room(),pkg,1);
        var revision=new QuoteRepository.Revision(1,version,policy,h.revision(),h.expires(),method(input),snapshot,PublicationService.hash(snapshot));
        active(h);store.create(q);store.append(q,revision);store.audit(actor,q.id(),1L,"CREATE");store.command(actor,key,"CREATE",hash,q.id(),1);return view(q,revision,h);
    }
    private Map<String,Object> context(QuotationHolds.Facts h){var facts=new LinkedHashMap<String,Object>();facts.put("bufferMinutes",h.bufferMinutes());facts.put("blockedUntil",h.blockedUntil().toString());facts.put("configuration",h.configuration());facts.putAll(Map.of("holdId",h.id().toString(),"roomId",h.room().toString(),"venueId",h.venue().toString(),"sessionStart",h.start().toString(),"sessionEnd",h.end().toString(),"holdExpiresAt",h.expires().toString(),"timezone",h.timezone(),"guests",h.guests()));return facts;}
    Map<String,Object> read(UUID actor,UUID id){configured();return tx.execute(status->{eligible(actor);var q=owned(actor,id,false);publications.lock(q.venue(),q.pkg());var h=ownedHold(actor,q.hold(),true);q=owned(actor,id,true);return view(q,store.revision(id,q.revision()),h);});}
    private Map<String,Object> view(QuoteRepository.Quote q,QuoteRepository.Revision r,QuotationHolds.Facts h){
        Instant now=store.now();String state=store.accepted(q.id()).filter(v->v==r.revision()).isPresent()?"ACCEPTED":!now.isBefore(r.expires())?"EXPIRED":!h.state().equals("ACTIVE")||h.revision()!=r.holdRevision()?"INVALIDATED":"ISSUED";
        if(state.equals("ISSUED"))try{compatible(h,PublicationService.object(r.snapshot(),"package"));}catch(DraftFailure invalid){state="INVALIDATED";}
        var result=new LinkedHashMap<String,Object>(r.snapshot());result.put("quoteId",q.id());result.put("revision",r.revision());result.put("packageVersionId",r.version());result.put("expiresAt",r.expires());result.put("contentHash",r.hash());result.put("serverNow",now);result.put("state",state);return result;
    }
}
