package com.cinenest.payments.internal;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
class DisabledReviewerNotice implements ReviewerNotice {
 public String deliver(UUID request){return "DELIVERY_DISABLED";}
}
