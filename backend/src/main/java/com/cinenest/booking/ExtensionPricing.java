package com.cinenest.booking;

import java.util.Map;

/** Consumer-owned pure pricing port. Input is a trusted immutable Booking purchase, never a request body. */
public interface ExtensionPricing {
    record Preview(int currentMinutes,int additionalMinutes,int totalMinutes,int incrementMinutes,
                   int maximumAdditionalMinutes,String additionalMinor,String currency,String packageVersion) {}
    Preview preview(Map<String,Object> purchase,int additionalMinutes);
    final class Unsupported extends RuntimeException {
        public Unsupported(){super("Frozen extension pricing unavailable");}
    }
}
