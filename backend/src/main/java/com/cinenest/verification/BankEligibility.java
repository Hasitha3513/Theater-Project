package com.cinenest.verification;
import java.util.UUID;
/** Current synthetic decision only; never commercial or physical admission eligibility. */
public interface BankEligibility { boolean eligible(UUID actor,UUID quote,UUID venue); }
