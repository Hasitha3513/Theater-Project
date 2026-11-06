package com.cinenest.pricing.internal;

import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.nimbusds.jose.util.JSONObjectUtils;

@Repository
class JdbcCommercialPolicyDraftRepository implements CommercialPolicyDraftRepository {
    private final JdbcClient jdbc;
    JdbcCommercialPolicyDraftRepository(JdbcClient jdbc){this.jdbc=jdbc;}
 public List<Map<String,Object>> previewOptions(UUID venue){return jdbc.sql("SELECT s.service_id,s.code,s.label,s.required_facility_code,r.rate_version_id,r.revision,r.unit_price_minor FROM cinenest_pricing.service_definition s JOIN cinenest_pricing.service_rate_version r USING(service_id) WHERE s.venue_id=:v AND s.approved=TRUE ORDER BY s.code,r.revision").param("v",venue).query((rs,n)->Map.<String,Object>of("serviceId",rs.getObject(1,UUID.class),"code",rs.getString(2),"label",rs.getString(3),"requiredFacilityCode",rs.getString(4),"rateVersionId",rs.getObject(5,UUID.class),"revision",rs.getLong(6),"unitPriceMinor",Long.toString(rs.getLong(7)),"currency","LKR","pricingBasis","PER_SESSION")).list();}
    /** One scoped statement over current immutable revisions; never exposes private approval evidence. */
    public List<CommercialQuotePreview.PolicyCandidate> previewCandidates(UUID venue,UUID packageId,Instant start,Instant end){
        return jdbc.sql("""
            SELECT h.policy_id,h.package_id,h.current_revision,r.content_hash,r.effective_from_proposal,
              r.effective_until_proposal,r.submitted_for_review,cinenest_pricing.commercial_policy_is_complete(r) AS complete,
              CASE WHEN NOT cinenest_pricing.commercial_policy_is_complete(r) THEN 'INCOMPLETE'
                WHEN NOT r.submitted_for_review THEN 'DRAFT'
                WHEN EXISTS(SELECT 1 FROM cinenest_pricing.commercial_policy_review v WHERE v.policy_id=h.policy_id
                  AND v.revision=h.current_revision AND v.outcome<>'APPROVE') THEN 'REJECTED'
                WHEN (SELECT count(*) FROM cinenest_pricing.commercial_policy_review v WHERE v.policy_id=h.policy_id
                  AND v.revision=h.current_revision)=3 THEN 'APPROVED' ELSE 'PENDING_REVIEW' END AS draft_status
            FROM cinenest_pricing.commercial_policy_draft h JOIN cinenest_pricing.commercial_policy_revision r
              ON r.policy_id=h.policy_id AND r.revision=h.current_revision
            WHERE h.venue_id=:venue AND (h.package_id IS NULL OR h.package_id=:package)
              AND (r.effective_from_proposal IS NULL OR r.effective_from_proposal<=:start)
              AND (r.effective_until_proposal IS NULL OR r.effective_until_proposal>=:end)
            ORDER BY h.policy_id LIMIT 2
            """).param("venue",venue).param("package",packageId).param("start",OffsetDateTime.ofInstant(start,java.time.ZoneOffset.UTC))
                .param("end",OffsetDateTime.ofInstant(end,java.time.ZoneOffset.UTC)).query((rs,n)->{
                    OffsetDateTime from=rs.getObject("effective_from_proposal",OffsetDateTime.class),until=rs.getObject("effective_until_proposal",OffsetDateTime.class);
                    return new CommercialQuotePreview.PolicyCandidate(rs.getObject("policy_id",UUID.class),rs.getLong("current_revision"),
                            rs.getString("content_hash"),rs.getObject("package_id",UUID.class),from==null?null:from.toInstant(),
                            until==null?null:until.toInstant(),rs.getString("draft_status"),rs.getBoolean("complete"),rs.getBoolean("submitted_for_review"));
                }).list();
    }
    static Map<String,Object> parse(String s){try{return JSONObjectUtils.parse(s);}catch(java.text.ParseException e){throw new IllegalStateException("Invalid persisted policy");}}
    Map<String,Object> row(String sql,Map<String,?> params){return jdbc.sql(sql).params(params).query(String.class).optional().map(JdbcCommercialPolicyDraftRepository::parse).orElseThrow(()->new CommercialPolicyDraft.Failure(404,"Policy unavailable in this venue"));}
    public Optional<Map<String,Object>> replay(UUID actor,UUID venue,UUID key){return jdbc.sql("SELECT to_jsonb(c)::text FROM cinenest_pricing.commercial_policy_command c WHERE actor_id=:a AND venue_id=:v AND idempotency_key=:k").param("a",actor).param("v",venue).param("k",key).query(String.class).optional().map(JdbcCommercialPolicyDraftRepository::parse);}
    public Map<String,Object> head(UUID venue,UUID policy,boolean lock){return row("SELECT to_jsonb(h)::text FROM cinenest_pricing.commercial_policy_draft h WHERE venue_id=:v AND policy_id=:p"+(lock?" FOR UPDATE":""),Map.of("v",venue,"p",policy));}
    public List<Map<String,Object>> list(UUID venue,String query,int page,int size){return jdbc.sql("""
        SELECT jsonb_build_object('policyId',h.policy_id,'title',r.title,'revision',r.revision::text)::text
        FROM cinenest_pricing.commercial_policy_draft h JOIN cinenest_pricing.commercial_policy_revision r
        ON r.policy_id=h.policy_id AND r.revision=h.current_revision
        WHERE h.venue_id=:v AND strpos(lower(r.title),lower(:q))>0 ORDER BY h.created_at DESC,h.policy_id LIMIT :s OFFSET :o
        """).param("v",venue).param("q",query).param("s",size).param("o",Math.multiplyExact(page,size)).query(String.class).list().stream().map(JdbcCommercialPolicyDraftRepository::parse).toList();}
    Map<String,Object> revision(UUID policy,long revision){return row("SELECT (to_jsonb(r)||jsonb_build_object('complete',cinenest_pricing.commercial_policy_is_complete(r)))::text FROM cinenest_pricing.commercial_policy_revision r WHERE policy_id=:p AND revision=:r",Map.of("p",policy,"r",revision));}
    List<Map<String,Object>> reviews(UUID policy,long revision){return jdbc.sql("SELECT to_jsonb(v)::text FROM cinenest_pricing.commercial_policy_review v WHERE policy_id=:p AND revision=:r ORDER BY domain").param("p",policy).param("r",revision).query(String.class).list().stream().map(JdbcCommercialPolicyDraftRepository::parse).map(CommercialPolicyDraftRules::transport).toList();}
    public Map<String,Object> detail(UUID venue,UUID policy){var h=head(venue,policy,false);long n=((Number)h.get("current_revision")).longValue();var raw=revision(policy,n);var d=CommercialPolicyDraftRules.transport(raw);var document=new LinkedHashMap<String,Object>();for(String field:CommercialPolicyDraft.FIELDS)document.put(field,d.get(field));var reviews=reviews(policy,n);boolean complete=Boolean.TRUE.equals(raw.get("complete"));String status=!complete?"INCOMPLETE":!Boolean.TRUE.equals(raw.get("submitted_for_review"))?"DRAFT":reviews.stream().anyMatch(x->!"APPROVE".equals(x.get("outcome")))?"REJECTED":reviews.size()==3?"APPROVED":"PENDING_REVIEW";var result=new LinkedHashMap<String,Object>(CommercialPolicyDraft.flags());result.putAll(Map.of("policyId",policy.toString(),"venueId",venue.toString(),"revision",Long.toString(n),"contentHash",raw.get("content_hash"),"status",status,"document",document,"reviews",reviews,"venueTimezone",raw.get("venue_timezone")));result.put("packageId",h.get("package_id"));result.put("createdBy",h.get("created_by"));return result;}
    public List<Map<String,Object>> history(UUID venue,UUID policy,int page,int size){head(venue,policy,false);var rows=jdbc.sql("SELECT to_jsonb(r)::text FROM cinenest_pricing.commercial_policy_revision r WHERE policy_id=:p ORDER BY revision DESC LIMIT :s OFFSET :o").param("p",policy).param("s",size).param("o",Math.multiplyExact(page,size)).query(String.class).list();return rows.stream().map(JdbcCommercialPolicyDraftRepository::parse).map(raw->{var result=CommercialPolicyDraftRules.transport(raw);result.put("reviews",reviews(policy,((Number)raw.get("revision")).longValue()));return result;}).toList();}
    public Map<String,Object> write(MatchedGrant g,UUID policy,UUID packageId,UUID key,String operation,long expected,String requestHash,String reason,Map<String,Object> document,Map<String,Object> review){
        UUID command=UUID.randomUUID(),reviewId=operation.equals("REVIEW")?UUID.randomUUID():null;long revision=operation.equals("CREATE")?1:operation.equals("REVIEW")?expected:Math.incrementExact(expected);String outcome=operation.equals("CREATE")?"CREATED":operation.equals("REVISE")?"REVISED":review.get("outcome").toString();
        if(operation.equals("CREATE"))jdbc.sql("INSERT INTO cinenest_pricing.commercial_policy_draft(policy_id,venue_id,package_id,current_revision,created_by,created_command_id) VALUES(:p,:v,:pkg,1,:a,:c)").param("p",policy).param("v",g.venue()).param("pkg",packageId,java.sql.Types.OTHER).param("a",g.account()).param("c",command).update();
        var args=new LinkedHashMap<String,Object>();args.put("c",command);args.put("p",policy);args.put("v",g.venue());args.put("a",g.account());args.put("ar",g.accountRevision());args.put("ep",g.credentialEpoch());args.put("g",g.grantId());args.put("gr",g.grantRevision());args.put("k",key);args.put("op",operation);args.put("expected",expected);args.put("r",revision);args.put("hash",review==null?null:review.get("expectedContentHash"));args.put("domain",review==null?null:review.get("domain"));args.put("review",reviewId);args.put("out",outcome);args.put("rh",requestHash);args.put("reason",reason);
        jdbc.sql("""
            INSERT INTO cinenest_pricing.commercial_policy_command(command_id,policy_id,venue_id,actor_id,account_revision,expected_credential_epoch,grant_id,grant_revision,idempotency_key,operation,expected_revision,result_revision,expected_content_hash,review_domain,result_review_id,outcome,request_hash,reason)
            VALUES(:c,:p,:v,:a,:ar,:ep,:g,:gr,:k,:op,:expected,:r,:hash,:domain,:review,:out,:rh,:reason)
            """).params(args).update();
        if(operation.equals("REVIEW"))jdbc.sql("""
            INSERT INTO cinenest_pricing.commercial_policy_review(review_id,command_id,policy_id,venue_id,revision,content_hash,domain,reviewer_id,outcome,evidence_reference,reason)
            VALUES(:review,:c,:p,:v,:r,:hash,:domain,:a,:out,:evidence,:reason)
            """).params(args).param("evidence",review.get("evidenceReference")).update();
        else {
            var doc=new LinkedHashMap<String,Object>();document.forEach((k,v)->doc.put(CommercialPolicyDraftRules.snake(k),v));doc.put("schema_version",1);doc.put("currency","LKR");doc.put("policy_id",policy.toString());doc.put("venue_id",g.venue().toString());doc.put("revision",revision);doc.put("command_id",command.toString());doc.put("authored_by",g.account().toString());doc.put("venue_timezone",jdbc.sql("SELECT timezone FROM cinenest_rooms.venue WHERE venue_id=:v").param("v",g.venue()).query(String.class).single());
            var columns=new ArrayList<>(doc.keySet());String names=String.join(",",columns);String selects=columns.stream().map(x->"r."+x).collect(java.util.stream.Collectors.joining(","));
            jdbc.sql("INSERT INTO cinenest_pricing.commercial_policy_revision("+names+") SELECT "+selects+" FROM jsonb_populate_record(NULL::cinenest_pricing.commercial_policy_revision,CAST(:doc AS jsonb)) r").param("doc",JSONObjectUtils.toJSONString(doc)).update();
            if(operation.equals("REVISE"))jdbc.sql("UPDATE cinenest_pricing.commercial_policy_draft SET current_revision=:r WHERE policy_id=:p AND venue_id=:v").params(args).update();
        }
        try {
        jdbc.sql("""
            INSERT INTO cinenest_pricing.commercial_policy_audit(event_id,command_id,policy_id,venue_id,revision,actor_id,operation,outcome,reason,correlation_id,occurred_at)
            SELECT :event,command_id,policy_id,venue_id,result_revision,actor_id,operation,outcome,reason,:correlation,admitted_at FROM cinenest_pricing.commercial_policy_command WHERE command_id=:c
            """).param("event",UUID.randomUUID()).param("correlation",UUID.randomUUID()).param("c",command).update();
        // Force deferred audit/receipt checks before returning from the service transaction.
        jdbc.sql("SET CONSTRAINTS ALL IMMEDIATE").update();
        } catch(org.springframework.dao.DataAccessException unavailable) {throw new CommercialPolicyDraft.Failure(503,"Policy audit unavailable; command rolled back");}
        return receipt(args,false);
    }
    static Map<String,Object> receipt(Map<String,Object> row,boolean replay){var result=new LinkedHashMap<String,Object>(CommercialPolicyDraft.flags());boolean stored=row.containsKey("command_id");result.put("commandId",row.get(stored?"command_id":"c").toString());result.put("policyId",row.get(stored?"policy_id":"p").toString());result.put("revision",row.get(stored?"result_revision":"r").toString());Object review=row.get(stored?"result_review_id":"review");result.put("reviewId",review==null?null:review.toString());result.put("replayed",replay);return result;}
}
