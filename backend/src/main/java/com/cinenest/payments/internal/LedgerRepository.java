package com.cinenest.payments.internal;
import java.util.*;
/** Payments-owned ledger operations. The application never supplies SQL or foreign repositories. */
interface LedgerRepository {
 List<Map<String,Object>> overdueExceptions(Map<String,?> values);
 int escalateException(Map<String,?> values);
 long refundCommitted(Map<String,?> values);
 long allocatedCredit(Map<String,?> values);
 long allocatedRequest(Map<String,?> values);
 List<Map<String,Object>> adjustments(Map<String,?> values);
 List<Map<String,Object>> correctionHistory(Map<String,?> values);
 int createException(Map<String,?> values);
 Map<String,Object> adjustmentCredit(Map<String,?> values);
 List<Map<String,Object>> matchedOwner(Map<String,?> values);
 int sourceRevision(Map<String,?> values);
 int recordCredit(Map<String,?> values);
 int verifyCredit(Map<String,?> values);
 int matchCredit(Map<String,?> values);
 int resolveUnmatched(Map<String,?> values);
 Map<String,Object> acceptedRequestTerms(Map<String,?> values);
 int allocate(Map<String,?> values);
 int resolvePartial(Map<String,?> values);
 int proposeCorrection(Map<String,?> values);
 Map<String,Object> lockCorrection(Map<String,?> values);
 List<Map<String,Object>> matchedRequest(Map<String,?> values);
 int approveCorrection(Map<String,?> values);
 Map<String,Object> lockException(Map<String,?> values);
 int assignException(Map<String,?> values);
 int audit(Map<String,?> values);
 long fundingShortfall(Map<String,?> values);
 List<Map<String,Object>> requestDisputes(Map<String,?> values);
 Map<String,Object> exceptionDetail(Map<String,?> values);
 Map<String,Object> credit(Map<String,?> values,boolean lock);
 List<Map<String,Object>> creditIds(Map<String,?> values);
 List<Map<String,Object>> exceptionIds(Map<String,?> values);
}
