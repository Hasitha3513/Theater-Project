package com.cinenest.identity;

import java.util.UUID;

/** Deliberately excludes provider identifiers, contact details, grants and private evidence. */
public record AccountView(UUID accountId, String eligibility) {}
