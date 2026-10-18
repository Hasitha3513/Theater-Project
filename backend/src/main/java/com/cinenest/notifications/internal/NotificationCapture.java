package com.cinenest.notifications.internal;
import java.util.UUID;
/** Test-only output port. It cannot accept a message body, address or credential. */
interface NotificationCapture {
 record Result(String outcome,String evidence){}
 Result capture(UUID operation,long fence,String fixture);
}
