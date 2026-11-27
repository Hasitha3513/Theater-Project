package com.cinenest.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Identity-owned transaction boundary. All records are private application facts. */
interface CustomerCredentialRepository {
    record Head(UUID account, UUID address, String lookup, byte[] sealedAddress, String keyVersion,
                CustomerCredentialLifecycle.State state, long revision, long epoch, String password,
                String privacy, String policy, String delivery, Instant registrationExpires) {
        @Override public String toString() { return "CustomerCredentialHead[REDACTED]"; }
        CustomerCredentialLifecycle.Account core() {
            return new CustomerCredentialLifecycle.Account(account,state,revision,epoch,address,privacy,password);
        }
    }
    record Capability(CustomerCredentialLifecycle.Capability core, String digest) {
        @Override public String toString() { return "CustomerCapability[REDACTED]"; }
    }
    record Receipt(UUID command, String requestDigest, UUID account, String action, int status, long epoch) {}
    record Issue(UUID capability, String digest, String purpose, Instant expires, UUID outbox, byte[] sealedPayload,
                 String keyVersion) {
        @Override public String toString() { return "CustomerCredentialIssue[REDACTED]"; }
    }
    record Outbox(UUID id, UUID command, UUID account, UUID capability, String purpose, byte[] payload,
                  String keyVersion, String policy, Instant expires, String state) {
        @Override public String toString() { return "CustomerCredentialOutbox[REDACTED]"; }
    }
    <T> T transaction(Supplier<T> work);
    Instant now();
    void keyContinuity(String version,String lookup,String commands,String encryption,String addressPolicy);
    void lockCommand(UUID command);
    void lockAddress(String lookup);
    Optional<Receipt> receipt(UUID command);
    Optional<Head> address(String lookup, boolean lock);
    Optional<Head> account(UUID account, boolean lock);
    Optional<Capability> capability(String digest);
    boolean abandonExpired(Head head,UUID replacementCommand);
    boolean resendDue(Head head,int seconds);
    void reissue(Head head,UUID command,String digest,UUID lease,Issue issue);
    UUID admit(UUID command, String action, String subject, boolean hash, CustomerCredentialConfiguration.Settings settings);
    CustomerCredentialLifecycle.Permit permit(UUID lease, UUID command, UUID account,
                                             CustomerCredentialLifecycle.Action action, String policy);
    CustomerCredentialPorts.CommitReceipt commit(CustomerCredentialLifecycle.Plan plan, Head before,
                                                 String requestDigest, UUID lease, Issue issue);
    Receipt neutral(UUID command, String digest, String action, int status);
    void release(UUID lease);
    void denied(UUID command,String digest,String action,int status);
    java.util.List<UUID> pending(int limit,CustomerCredentialConfiguration.Settings settings);
    boolean deliveryCurrent(UUID id,CustomerCredentialConfiguration.Settings settings);
    Optional<Outbox> claim(UUID id,CustomerCredentialConfiguration.Settings settings);
    void deliveryResult(UUID id, String result);
}
