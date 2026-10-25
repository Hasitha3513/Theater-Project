package com.cinenest.payments.internal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;
/** Closed non-payable fixtures, not a general storage/provider adapter. No URLs or file paths. */
@Component
class FixedInstructionResolver implements InstructionResolver {
 static final UUID REFERENCE=id("CN-R1-BI-NONPAYABLE"),VERSION1=id("CN-R1-BI-V1"),VERSION2=id("CN-R1-BI-V2");
 static UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
 static String digest(UUID version){return BankPolicy.hash("NON-PAYABLE STAGING FIXTURE|NO REAL RECEIVING ACCOUNT|DO NOT TRANSFER MONEY|"+version);}
 public Fixture resolve(UUID reference,UUID version,String digest){
  if(!REFERENCE.equals(reference)||!(VERSION1.equals(version)||VERSION2.equals(version))||!digest(version).equals(digest))throw new BankFailure(503,"Staging instructions unavailable");
  return new Fixture("NON-PAYABLE STAGING FIXTURE","NO REAL RECEIVING ACCOUNT","DO NOT TRANSFER MONEY");
 }
}
