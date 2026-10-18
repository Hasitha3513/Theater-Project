package com.cinenest.identity;
import java.util.Set;
import java.util.UUID;
/** Identity owns directory access; all permissions are evaluated for the same recipient and venue. */
public interface NotificationRecipients { Set<UUID> bankReviewers(UUID venue); }
