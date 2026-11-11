package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CustomerCommercialQuoteConfiguration {
    @Value("${cinenest.commercial.operational.enabled:false}")
    private boolean activationEnabled;

    @Value("${cinenest.commercial.operational.issuance-enabled:false}")
    private boolean issuanceEnabled;

    boolean activationEnabled() {
        return activationEnabled;
    }

    void requireOperationalIssuance() {
        if (!activationEnabled || !issuanceEnabled) {
            throw new DraftFailure(503, "Commercial quotation issuance is unavailable");
        }
    }

    @Bean
    QuotationHolds.OperationalPackageValidator operationalPackageValidator(PublicationRepository publications) {
        return (venue, room, packageId, version, start, end, guests) -> {
            if (!activationEnabled) {
                return false;
            }
            try {
                publications.lock(venue, packageId);
                var snapshot = publication(publications, venue, room, packageId, version, start, end);
                return guests <= ((Number) snapshot.get("maximumGuests")).intValue();
            } catch (DraftFailure unavailable) {
                return false;
            }
        };
    }

    static Map<String, Object> publication(PublicationRepository publications, UUID venue, UUID room,
            UUID packageId, UUID version, Instant start, Instant end) {
        return publications.catalog(venue).stream()
            .filter(row -> packageId.equals(row.get("packageId")) && version.equals(row.get("versionId"))
                && !start.isBefore(Instant.parse(row.get("sessionFrom").toString()))
                && !end.isAfter(Instant.parse(row.get("sessionTo").toString())))
            .map(row -> PublicationService.object(row, "snapshot"))
            .filter(snapshot -> PublicationService.rooms(snapshot).contains(room))
            .findFirst().orElseThrow(() -> new DraftFailure(409, "Published package version unavailable"));
    }
}
