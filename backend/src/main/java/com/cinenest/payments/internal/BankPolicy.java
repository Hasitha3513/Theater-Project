package com.cinenest.payments.internal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component
class BankPolicy {
 static final int MAX_BYTES=5242880,MAX_PIXELS=20000000;
 static final List<String> LABELS=List.of("SYNTHETIC BANK-PAYMENT TEST","DO NOT TRANSFER REAL MONEY","PAYMENT NOT VERIFIED","BOOKING NOT CONFIRMED");
 final Environment env;BankPolicy(Environment env){this.env=env;}
 record Policy(String version,String hash,UUID id,Duration lead,Duration request,Duration initiation,Duration submission,Duration review,Duration hold,Duration escalation,Duration upload,Duration retention,Duration cleanup,LocalTime opens,LocalTime closes,Set<String> fixtures){}
 Policy current(){return load(true);}Policy maintenance(){return load(false);}
 private Policy load(boolean commands){try{
  if((commands&&!env.getProperty("cinenest.bank.enabled",Boolean.class,false))||!Set.of(env.getActiveProfiles()).equals(Set.of("synthetic-quotation")))throw new IllegalArgumentException();
  if(!java.nio.file.Path.of(env.getRequiredProperty("cinenest.bank.storage-root")).isAbsolute()||Base64.getDecoder().decode(env.getRequiredProperty("cinenest.bank.encryption-key")).length!=32)throw new IllegalArgumentException();
  String version=env.getRequiredProperty("cinenest.bank.policy-version");if(!version.matches("SYNTHETIC-[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException();
  List<Duration> v=new ArrayList<>();for(String n:List.of("lead","request","initiation","submission","review","hold","escalation","upload","retention","cleanup")){long seconds=Long.parseLong(env.getRequiredProperty("cinenest.bank."+n+"-seconds"));if(seconds<=0||seconds>Integer.MAX_VALUE)throw new IllegalArgumentException();v.add(Duration.ofSeconds(seconds));}
  if(v.get(2).compareTo(v.get(3))>0||v.get(3).compareTo(v.get(1))>0||v.get(5).compareTo(v.get(1))>0||v.get(3).compareTo(v.get(4))>0||v.get(4).compareTo(v.get(6))>0||v.get(7).compareTo(v.get(8))>0)throw new IllegalArgumentException();
  LocalTime opens=LocalTime.parse(env.getRequiredProperty("cinenest.bank.review-opens")),closes=LocalTime.parse(env.getRequiredProperty("cinenest.bank.review-closes"));if(!opens.isBefore(closes))throw new IllegalArgumentException();
  Set<String> fixtures=new TreeSet<>(Arrays.asList(env.getRequiredProperty("cinenest.bank.fixture-sha256").split(",")));if(fixtures.isEmpty()||!SyntheticFixtures.hashes().containsAll(fixtures))throw new IllegalArgumentException();
  String hash=hash(version+v+opens+closes+fixtures+":BANK=0:SIMULATED");return new Policy(version,hash,UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)),v.get(0),v.get(1),v.get(2),v.get(3),v.get(4),v.get(5),v.get(6),v.get(7),v.get(8),v.get(9),opens,closes,fixtures);
 }catch(RuntimeException e){throw new BankFailure(503,"Synthetic bank policy unavailable; real payments disabled");}}
 static String commandHash(String operation,Map<String,Object> body){return hash(operation+":"+com.nimbusds.jose.util.JSONObjectUtils.toJSONString(new TreeMap<>(body)));}
 static String hash(String s){return hash(s.getBytes(StandardCharsets.UTF_8));}static String hash(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
}
