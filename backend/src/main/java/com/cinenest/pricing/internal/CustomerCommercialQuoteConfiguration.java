package com.cinenest.pricing.internal;

import org.springframework.context.annotation.Configuration;

/** The operational authority adapter has no approved trust roots or activation in this release. */
@Configuration(proxyBeanMethods = false)
class CustomerCommercialQuoteConfiguration {
    void requireOperationalIssuance() {
        throw new DraftFailure(503, "Commercial quotation issuance is unavailable");
    }
}
