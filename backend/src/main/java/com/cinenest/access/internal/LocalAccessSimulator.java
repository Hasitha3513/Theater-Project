package com.cinenest.access.internal;
import java.util.UUID;import org.springframework.stereotype.Component;
@Component
class LocalAccessSimulator implements AccessSimulator {
 public String apply(UUID operation,long version,String fixture,boolean reconcile){return switch(fixture){case "SYNTHETIC-INSTALLED"->"SIMULATOR_INSTALLED";case "SYNTHETIC-FAILED"->"FAILED";case "SYNTHETIC-UNKNOWN"->{if(reconcile)throw new AccessFailure(409);yield "UNKNOWN";}default->throw new AccessFailure(400);};}
}
