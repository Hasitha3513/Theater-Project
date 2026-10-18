package com.cinenest.iot.internal;
import java.time.Instant;import java.util.*;import org.springframework.stereotype.Component;
/** Deterministic in-process evidence only. No networking or electrical adapter exists. */
interface LightingSimulator {record Evidence(String outcome,String observation,Long sequence,Instant observedAt){} Evidence apply(Map<String,Object> input,Instant now,int freshness);}
@Component
class LocalLightingSimulator implements LightingSimulator {
 public Evidence apply(Map<String,Object> b,Instant now,int freshness){
  String outcome=Objects.toString(b.get("outcome"),"");if(!Set.of("ACKNOWLEDGED","FAILED","UNKNOWN").contains(outcome))throw new IotFailure(400);
  String observed=b.containsKey("observed")?Objects.toString(b.get("observed"),""):null;Long sequence=null;Instant at=null;
  if(observed!=null){if(!Set.of("ON","OFF").contains(observed))throw new IotFailure(400);Object v=b.get("observationSequence");if(!(v instanceof Number n)||n.longValue()<1||n.doubleValue()!=n.longValue())throw new IotFailure(400);sequence=n.longValue();try{at=Instant.parse(Objects.toString(b.get("observedAt"),""));}catch(RuntimeException e){throw new IotFailure(400);}if(at.isAfter(now)||at.isBefore(now.minusSeconds(freshness)))throw new IotFailure(409);}
  else if(b.containsKey("observedAt")||b.containsKey("observationSequence"))throw new IotFailure(400);
  return new Evidence(outcome,observed,sequence,at);
 }
}
