package com.cinenest.payments.internal;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

/** Offline configuration and closed-fixture regression; no ledger writes or network access. */
class SyntheticCreditSourceProfileTest {
 static Stream<Arguments> approvedProfiles(){return PaymentsProfileConfigurationTest.approvedProfiles();}
 static Stream<Arguments> deniedProfiles(){return PaymentsProfileConfigurationTest.deniedProfiles();}
 MockEnvironment environment(String[] profiles){var e=new MockEnvironment();e.setActiveProfiles(profiles);return e.withProperty("cinenest.bank.ledger.enabled","true").withProperty("cinenest.bank.ledger.venue-id","00000000-0000-0000-0000-000000000001").withProperty("cinenest.bank.ledger.fixture-credited-at","2020-01-01T00:00:00Z").withProperty("cinenest.bank.ledger.resolution-seconds","60");}
 void denied(Runnable action,int status){assertThatThrownBy(action::run).isInstanceOfSatisfying(BankFailure.class,e->assertThat(e.status).isEqualTo(status));}
 @ParameterizedTest @MethodSource("approvedProfiles") void exactProfilesPermitCommandsAndMaintenance(String[] profiles){var source=new SyntheticCreditSource(environment(profiles));assertThatCode(source::configured).doesNotThrowAnyException();assertThatCode(source::maintenance).doesNotThrowAnyException();}
 @ParameterizedTest @MethodSource("deniedProfiles") void otherProfilesDenyCommandsMaintenanceAndFixtures(String[] profiles){var source=new SyntheticCreditSource(environment(profiles));denied(source::configured,503);denied(source::maintenance,503);denied(()->source.fixture("SYNTHETIC-BANK-100"),503);}
 @ParameterizedTest @MethodSource("approvedProfiles") void disabledCommandsPreserveMaintenance(String[] profiles){var source=new SyntheticCreditSource(environment(profiles).withProperty("cinenest.bank.ledger.enabled","false"));denied(source::configured,503);assertThatCode(source::maintenance).doesNotThrowAnyException();}
 @ParameterizedTest @MethodSource("approvedProfiles") void malformedAndMissingConfigurationFailClosed(String[] profiles){
  for(var entry:List.of(Map.entry("cinenest.bank.ledger.venue-id","invalid"),Map.entry("cinenest.bank.ledger.fixture-credited-at","invalid"),Map.entry("cinenest.bank.ledger.resolution-seconds","0"),Map.entry("cinenest.bank.ledger.resolution-seconds","2147483648"),Map.entry("cinenest.bank.ledger.resolution-seconds",""))){var source=new SyntheticCreditSource(environment(profiles).withProperty(entry.getKey(),entry.getValue()));denied(source::configured,503);denied(source::maintenance,503);}
  var missing=new MockEnvironment();missing.setActiveProfiles(profiles);missing.withProperty("cinenest.bank.ledger.enabled","true");denied(new SyntheticCreditSource(missing)::configured,503);
 }
 @ParameterizedTest @MethodSource("approvedProfiles") void closedCatalogueAndDuplicateIdentityRemainUnchanged(String[] profiles){
  var source=new SyntheticCreditSource(environment(profiles));
  for(var key:List.of("SYNTHETIC-BANK-100","SYNTHETIC-BANK-200","SYNTHETIC-BANK-10000","SYNTHETIC-GATEWAY-10000","SYNTHETIC-BANK-50000","SYNTHETIC-GATEWAY-50000")){var fact=source.fixture(key);assertThat(fact.amount()).isEqualTo(Long.parseLong(key.substring(key.lastIndexOf('-')+1)));assertThat(fact.key()).isEqualTo(key);assertThat(fact.venue()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));assertThat(fact.credited()).isEqualTo(java.time.Instant.parse("2020-01-01T00:00:00Z"));assertThat(fact.account()).isEqualTo("SYNTHETIC-TEST-ACCOUNT");assertThat(fact.movement()).isEqualTo(key);assertThat(fact.kind()).isEqualTo(key.contains("GATEWAY")?"SYNTHETIC_GATEWAY":"SYNTHETIC_BANK");assertThat(fact.hash()).isEqualTo(BankPolicy.hash(key+":"+fact.amount()+":"+fact.credited()+":"+fact.venue()));}
  assertThat(source.fixture("SYNTHETIC-OVERLAP-100")).isEqualTo(source.fixture("SYNTHETIC-BANK-100"));
  for(var key:List.of("","REAL-BANK-100","SYNTHETIC-BANK-101"))denied(()->source.fixture(key),400);
  assertThat(new ReceiptAdmissionPolicy().decision()).isEqualTo(ReceiptAdmissionPolicy.Decision.DENY);
 }
}
