package com.cinenest.identity.internal;
import com.cinenest.identity.ScopedAuditSource;
import java.util.List;
import java.util.UUID;
interface AdminRepository {
    record Account(UUID accountId, boolean synthetic, String eligibility, long revision, UUID venueId) {}
    List<Account> accounts(UUID venue, UUID target, String eligibility, UUID after, int limit);
    List<ScopedAuditSource.Event> audit(ScopedAuditSource.Query query);
    void denied(UUID actor);
    void record(UUID actor, UUID venue, UUID target, String action, String outcome, String reason);
}
