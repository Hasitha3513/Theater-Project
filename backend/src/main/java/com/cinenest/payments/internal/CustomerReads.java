package com.cinenest.payments.internal;
import java.util.*;
/** Explicit redacted customer contracts. No staff, evidence, destination or raw SQL rows. */
final class CustomerReads {
 private CustomerReads(){}
 record Page<T>(List<T> items,int page,int size,boolean hasNext,String observedAt,boolean synthetic){}
 record Resolution(String kind,String state){}
 record Funding(String allocatedMinor,String outstandingMinor,String fundingShortfallMinor,String status,String bookingStatus,String bookingId,String verifiedMatchedMinor,boolean realPayment){
  static Funding from(Map<String,Object> f){return new Funding(f.get("allocatedMinor").toString(),f.get("outstandingMinor").toString(),f.get("fundingShortfallMinor").toString(),f.get("syntheticFundingStatus").toString(),f.get("syntheticBookingStatus").toString(),Objects.toString(f.get("bookingId"),null),f.get("verifiedMatchedMinor").toString(),false);}
 }
 record Request(UUID id,UUID venueId,long revision,String state,String reviewState,boolean claimSubmitted,String amountMinor,String currency,String timezone,String initiateBy,String submitBy,String reviewBy,String expiresAt,Funding funding,List<Resolution> resolutions,boolean synthetic){}
 record Refund(UUID id,UUID requestId,UUID venueId,long revision,String state,String amountMinor,String currency,String resolution,boolean synthetic,boolean realRepayment){}
 static void bounds(int page,int size){if(page<0||page>10000||size<1||size>25)throw new BankFailure(400,"Page must be 0–10000; size 1–25");}
}
