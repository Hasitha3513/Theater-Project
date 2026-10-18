package com.cinenest.access.internal;
import java.util.UUID;
/** Future device boundary. This implementation has no network/device dependency. */
interface AccessSimulator {String apply(UUID operation,long version,String fixture,boolean reconcile);}
