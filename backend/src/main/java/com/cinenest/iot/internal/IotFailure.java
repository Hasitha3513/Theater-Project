package com.cinenest.iot.internal;
class IotFailure extends RuntimeException {final int status;IotFailure(int status){super("Synthetic IoT unavailable, changed or denied");this.status=status;}}
