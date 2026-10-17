package com.cinenest.payments.internal;
import java.util.UUID;
/** Internal dry-run delivery seam. This task provides no live delivery implementation. */
interface ReviewerNotice { String deliver(UUID request); }
