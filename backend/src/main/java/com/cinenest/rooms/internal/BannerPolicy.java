package com.cinenest.rooms.internal;
import java.time.*;import java.util.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.core.env.*;import org.springframework.stereotype.Component;
@Component
class BannerPolicy {
 private final boolean enabled;
 BannerPolicy(@Value("${cinenest.advertising.enabled:false}") boolean enabled,Environment environment){if(enabled&&!environment.acceptsProfiles(Profiles.of("local-synthetic")))throw new IllegalStateException("Local synthetic profile required");this.enabled=enabled;}
 void require(){if(!enabled)throw new RoomFailure(404,"Advertising unavailable");}
 boolean enabled(){return enabled;}
 record Content(String title,String description,String altText,String assetId,int assetVersion,Instant startsAt,Instant endsAt,int displayOrder,String linkTarget){}
 static String text(Map<String,Object> m,String k,int min,int max){Object o=m.get(k);if(!(o instanceof String s)||s.length()<min||s.length()>max||!s.equals(s.strip())||s.chars().anyMatch(c->Character.isISOControl(c)||c=='<'||c=='>'))throw new RoomFailure(400,"Invalid banner content");return s;}
 static long number(Map<String,Object> m,String k){Object v=m.get(k);if(!(v instanceof Number n)||n.doubleValue()!=n.longValue()||n.longValue()<0||n.longValue()>9007199254740991L)throw new RoomFailure(400,"Invalid revision or number");return n.longValue();}
 static Content content(Map<String,Object> m){if(!m.keySet().equals(Set.of("title","description","altText","assetId","assetVersion","startsAt","endsAt","displayOrder","linkTarget")))throw new RoomFailure(400,"Invalid content fields");try{var c=new Content(text(m,"title",1,120),text(m,"description",0,1000),text(m,"altText",1,240),text(m,"assetId",1,20),Math.toIntExact(number(m,"assetVersion")),OffsetDateTime.parse(text(m,"startsAt",1,40)).toInstant(),OffsetDateTime.parse(text(m,"endsAt",1,40)).toInstant(),Math.toIntExact(number(m,"displayOrder")),text(m,"linkTarget",1,10));if(!c.endsAt.isAfter(c.startsAt)||c.displayOrder>10000||!Set.of("NONE","ROOMS").contains(c.linkTarget)||c.assetVersion!=1)throw new IllegalArgumentException();return c;}catch(java.time.DateTimeException|ArithmeticException|IllegalArgumentException e){throw new RoomFailure(400,"Invalid banner schedule or content");}}
}
