package com.cinenest.identity.internal;
import java.text.Normalizer;import java.time.Instant;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.core.env.Environment;import org.springframework.core.env.Profiles;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Service;import org.springframework.transaction.support.TransactionTemplate;
@Service
@ConditionalOnProperty(name="cinenest.identity.profile.enabled",havingValue="true")
class CustomerProfileService {
 record Profile(UUID customerId,String fullName,String phone,String verifiedEmail,String emailVerificationStatus,String accountStatus,List<String> linkedProviderTypes,long revision,Instant updatedAt){}
 record Result(int status,Profile profile){}
 private final JdbcClient jdbc;private final TransactionTemplate tx;private final String googleIssuer;
 CustomerProfileService(JdbcClient jdbc,TransactionTemplate tx,Environment env){if(!env.acceptsProfiles(Profiles.of("local-synthetic")))throw new IllegalStateException("Local synthetic profile required");this.jdbc=jdbc;this.tx=tx;this.googleIssuer=env.getProperty("cinenest.identity.google.contract-issuer","");}
 Result read(UUID actor){return tx.execute(t->{if(!lock(actor)){audit(actor,"READ","DENY",0,0);return new Result(403,null);}var p=profile(actor);audit(actor,"READ","ALLOW",p.revision(),p.revision());return new Result(200,p);});}
 Result update(UUID actor,long expected,String fullName,String phone){return tx.execute(t->{if(!lock(actor)){audit(actor,"UPDATE","DENY",0,0);return new Result(403,null);}var before=profile(actor);String name,number;
  try{name=name(fullName);number=phone(phone);if(expected<0||expected==Long.MAX_VALUE)throw new IllegalArgumentException();}catch(IllegalArgumentException invalid){audit(actor,"UPDATE","INVALID",before.revision(),before.revision());return new Result(400,null);}
  if(before.revision()!=expected){audit(actor,"UPDATE","CONFLICT",before.revision(),before.revision());return new Result(409,null);}
  jdbc.sql("INSERT INTO cinenest_identity.customer_profile(account_id,full_name,phone,revision,updated_at) VALUES(:id,:name,:phone,1,clock_timestamp()) ON CONFLICT(account_id) DO UPDATE SET full_name=EXCLUDED.full_name,phone=EXCLUDED.phone,revision=customer_profile.revision+1,updated_at=clock_timestamp()")
   .param("id",actor).param("name",name).param("phone",number,java.sql.Types.VARCHAR).update();
  var result=profile(actor);audit(actor,"UPDATE","ALLOW",before.revision(),result.revision());return new Result(200,result);
 });}
 Result invalid(UUID actor){return invalid(actor,"UPDATE");}
 Result invalid(UUID actor,String action){return tx.execute(t->{if(!lock(actor)){audit(actor,action,"DENY",0,0);return new Result(403,null);}long r=profile(actor).revision();audit(actor,action,"INVALID",r,r);return new Result(400,null);});}
 private boolean lock(UUID actor){return jdbc.sql("SELECT account_id FROM cinenest_identity.account WHERE account_id=:id AND synthetic=TRUE AND eligibility='ELIGIBLE' FOR UPDATE").param("id",actor).query(UUID.class).optional().isPresent();}
 private Profile profile(UUID actor){
  var row=jdbc.sql("SELECT full_name,phone,revision,updated_at FROM cinenest_identity.customer_profile WHERE account_id=:id").param("id",actor).query((rs,n)->new Profile(actor,rs.getString(1),rs.getString(2),null,null,"ELIGIBLE",List.of(),rs.getLong(3),rs.getTimestamp(4).toInstant())).optional();
  var email=jdbc.sql("SELECT email,verified_at FROM cinenest_identity.manual_customer WHERE account_id=:id").param("id",actor).query((rs,n)->Map.of("email",rs.getString(1),"verified",rs.getTimestamp(2)!=null)).optional();
  var methods=new TreeSet<String>();if(email.isPresent())methods.add("MANUAL");
  for(String issuer:jdbc.sql("SELECT issuer FROM cinenest_identity.external_identity WHERE account_id=:id").param("id",actor).query(String.class).list())methods.add(issuer.equals("https://accounts.google.com")||(!googleIssuer.isEmpty()&&issuer.equals(googleIssuer))?"GOOGLE":issuer.startsWith("urn:cinenest:identity:facebook:app:")?"FACEBOOK":"OIDC");
  var p=row.orElse(new Profile(actor,"",null,null,null,"ELIGIBLE",List.of(),0,null));boolean verified=email.map(e->Boolean.TRUE.equals(e.get("verified"))).orElse(false);
  return new Profile(actor,p.fullName(),p.phone(),verified?(String)email.get().get("email"):null,email.isEmpty()?"NOT_AVAILABLE":verified?"VERIFIED":"PENDING","ELIGIBLE",List.copyOf(methods),p.revision(),p.updatedAt());
 }
 private void audit(UUID actor,String action,String outcome,long before,long after){jdbc.sql("INSERT INTO cinenest_identity.customer_profile_audit(event_id,account_id,action,outcome,revision_before,revision_after) VALUES(:event,:actor,:action,:outcome,:before,:after)").param("event",UUID.randomUUID()).param("actor",actor).param("action",action).param("outcome",outcome).param("before",before).param("after",after).update();}
 static String name(String value){if(value==null)throw new IllegalArgumentException();String n=Normalizer.normalize(value.strip(),Normalizer.Form.NFC);if(n.isEmpty()||n.codePointCount(0,n.length())>120||n.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.SURROGATE||Character.getType(c)==Character.LINE_SEPARATOR||Character.getType(c)==Character.PARAGRAPH_SEPARATOR||c=='<'||c=='>'||(c>=0x202a&&c<=0x202e)||(c>=0x2066&&c<=0x2069)))throw new IllegalArgumentException();return n;}
 static String phone(String value){if(value==null||value.isBlank())return null;String n=value.strip();long digits=n.chars().filter(Character::isDigit).count();if(!n.matches("\\+?[0-9][0-9 ()-]{5,31}")||digits<6||digits>15)throw new IllegalArgumentException();return n;}
}
