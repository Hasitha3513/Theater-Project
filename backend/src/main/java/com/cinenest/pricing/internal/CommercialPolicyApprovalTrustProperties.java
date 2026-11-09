package com.cinenest.pricing.internal;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cinenest.pricing.trusted-approvals")
record CommercialPolicyApprovalTrustProperties(
        boolean enabled, UUID appointmentAuthority, UUID identityVerifier, UUID keyCustodian,
        UUID revocationAuthority, long revocationVersion,
        Instant revocationObservedAt, List<Appointment> appointments, List<Key> keys) {

    CommercialPolicyApprovalTrustProperties {
        appointments = appointments == null ? List.of() : List.copyOf(appointments);
        keys = keys == null ? List.of() : List.copyOf(keys);
    }

    record Appointment(UUID id, UUID appointedBy, UUID verifiedBy, UUID reviewer, UUID venue, UUID room, UUID packageVersion,
                       Set<String> domains, Instant validFrom, Instant validUntil, boolean conflicted, boolean revoked) {
        Appointment {
            domains = domains == null ? Set.of() : Set.copyOf(domains);
        }
    }

    record Key(String id, UUID owner, UUID custodian, String publicKey, Instant validFrom, Instant validUntil, boolean revoked) { }
}
