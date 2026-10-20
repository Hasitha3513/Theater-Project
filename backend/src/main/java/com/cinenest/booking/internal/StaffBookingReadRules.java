package com.cinenest.booking.internal;

import java.time.*;
import java.util.*;
import org.springframework.util.MultiValueMap;

final class StaffBookingReadRules {
 private StaffBookingReadRules(){}
 static final class Failure extends RuntimeException {
  final int status;final String reason;
  Failure(int status,String reason){super(reason);this.status=status;this.reason=reason;}
 }
 static Failure invalid(){return new Failure(400,"INVALID_REQUEST");}
 static UUID id(String raw){
  if(raw==null||raw.length()!=36||!raw.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw invalid();
  return UUID.fromString(raw);
 }
 private static int number(String raw,int maximum){
  if(raw==null||!raw.matches("0|[1-9][0-9]{0,4}"))throw invalid();
  int value=Integer.parseInt(raw);if(value>maximum)throw invalid();return value;
 }
 private static Instant instant(String raw){
  if(raw==null||raw.length()>40)throw invalid();
  try{var parsed=OffsetDateTime.parse(raw);if(parsed.getYear()<1||parsed.getYear()>9999)throw invalid();return parsed.toInstant();}catch(DateTimeException e){throw invalid();}
 }
 // PostgreSQL timestamps have microsecond resolution. Ceiling both boundaries keeps
 // >= from / < to exact for stored instants, including sub-microsecond inputs.
 static Instant databaseBoundary(Instant value){if(value==null)return null;int remainder=value.getNano()%1000;return remainder==0?value:value.plusNanos(1000-remainder);}
 static StaffBookingReadStore.Query query(UUID venue,MultiValueMap<String,String> params,boolean detail){
  if(detail&&!params.isEmpty()||!Set.of("reference","from","to","page","size").containsAll(params.keySet()))throw invalid();
  for(var values:params.values())if(values.size()!=1||values.getFirst()==null||values.getFirst().isEmpty())throw invalid();
  UUID reference=params.containsKey("reference")?id(params.getFirst("reference")):null;
  if(params.containsKey("from")!=params.containsKey("to"))throw invalid();
  Instant from=null,to=null;
  if(params.containsKey("from")){
   from=instant(params.getFirst("from"));to=instant(params.getFirst("to"));
   if(!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(31))>0)throw invalid();
  }
  int page=number(params.getFirst("page")==null?"0":params.getFirst("page"),10000);
  int size=number(params.getFirst("size")==null?"10":params.getFirst("size"),25);
  if(size<1)throw invalid();
  return new StaffBookingReadStore.Query(venue,reference,from,to,page,size);
 }
}
