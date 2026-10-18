package com.cinenest.access.internal;
final class AccessFailure extends RuntimeException {final int status;AccessFailure(int status){super("Synthetic access unavailable, changed or denied");this.status=status;}}
