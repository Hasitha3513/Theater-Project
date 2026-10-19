package com.cinenest.verification;
import java.util.UUID;
/** Current synthetic decision only; never commercial or physical admission eligibility. */
public interface BankEligibility { boolean eligible(UUID actor,UUID quote,UUID venue);
 /** Same current decision predicate without row locks or mutations; caller uses a read-only snapshot. */
 boolean observe(UUID actor,UUID quote,UUID venue); }
