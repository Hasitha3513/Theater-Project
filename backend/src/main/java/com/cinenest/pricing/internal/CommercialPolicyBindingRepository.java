package com.cinenest.pricing.internal;

import java.util.UUID;

interface CommercialPolicyBindingRepository {
    record Receipt(UUID bindingId, boolean replay) {}

    Receipt bind(CommercialPolicyBinding command);
}
