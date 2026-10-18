package com.cinenest.notifications.internal;
class NotificationFailure extends RuntimeException { final int status; NotificationFailure(int status){super("Synthetic notification unavailable, changed or denied");this.status=status;} }
