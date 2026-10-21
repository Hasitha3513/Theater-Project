package com.cinenest;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class FoundationArchitectureTest {
    @Test
    void declaredFoundationModuleHasAnEncapsulatedImplementation() {
        var modules = ApplicationModules.of(CineNestApplication.class);
        assertThat(modules.stream().map(module -> module.getIdentifier().toString())).containsExactlyInAnyOrder("foundation", "identity", "rooms", "pricing", "booking", "verification", "payments", "access", "notifications", "iot", "reporting");
        modules.verify();
    }

    @Test
    void publicFoundationApiCannotDependOnItsAdapter() {
        var classes = new ClassFileImporter().importPackages("com.cinenest.foundation");
        noClasses().that().resideInAPackage("com.cinenest.foundation")
            .should().dependOnClassesThat().resideInAPackage("com.cinenest.foundation.internal")
            .check(classes);
    }
    @Test
    void publicIdentityApiCannotDependOnPersistenceOrInternalAdapters() {
        var classes = new ClassFileImporter().importPackages("com.cinenest.identity");
        noClasses().that().resideInAPackage("com.cinenest.identity")
            .should().dependOnClassesThat().resideInAnyPackage("com.cinenest.identity.internal..", "org.springframework.jdbc..")
            .check(classes);
    }
    @Test
    void roomsOnlyUsesIdentityPublicPortAndPersistenceStaysInAdapter() {
        var classes = new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().resideInAPackage("com.cinenest.rooms..")
            .should().dependOnClassesThat().resideInAPackage("com.cinenest.identity.internal..").check(classes);
        noClasses().that().haveSimpleName("RoomService").should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().haveSimpleName("RoomService").should().dependOnClassesThat().haveSimpleName("JdbcRoomRepository").check(classes);
    }

    @Test void pricingUsesOnlyPublicOwnerInterfacesAndItsOwnAdapter() {
        var classes=new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().resideInAPackage("com.cinenest.pricing..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.rooms.internal..","com.cinenest.identity.internal..","com.cinenest.booking.internal..").check(classes);
        noClasses().that().haveSimpleName("DraftService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().haveSimpleName("DraftService").should().dependOnClassesThat().haveSimpleName("JdbcDraftRepository").check(classes);
        noClasses().that().haveSimpleName("PublicationService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().haveSimpleName("PublicationService").should().dependOnClassesThat().haveSimpleName("JdbcPublicationRepository").check(classes);
    }
    @Test void quotationUsesOnlyPublicHoldFactsAndItsOwnPersistencePort() {
        var classes=new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().haveSimpleName("QuoteService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().resideInAPackage("com.cinenest.booking").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.booking.internal..","org.springframework.jdbc..").check(classes);
    }
    @Test void verificationUsesOnlyOwnerPortsAndItsOwnPersistence() {
        var classes=new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().resideInAPackage("com.cinenest.verification..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.identity.internal..","com.cinenest.pricing.internal..","com.cinenest.rooms..","com.cinenest.booking..").check(classes);
        noClasses().that().haveSimpleName("VerificationService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().haveSimpleName("VerificationService").should().dependOnClassesThat().haveSimpleName("JdbcVerificationRepository").check(classes);
    }
    @Test void bookingUsesOwnerPortsAndItsOwnPersistenceAdapter() {
        var classes=new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().resideInAPackage("com.cinenest.booking..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.rooms.internal..","com.cinenest.identity.internal..","com.cinenest.pricing..").check(classes);
        noClasses().that().haveSimpleName("InventoryService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().haveSimpleName("InventoryService").should().dependOnClassesThat().haveSimpleName("JdbcInventoryRepository").check(classes);
    }
    @Test void paymentsUsesOnlyPublicOwnerPortsAndItsOwnPersistence() {
        var classes=new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().resideInAPackage("com.cinenest.payments..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.identity.internal..","com.cinenest.pricing.internal..","com.cinenest.booking.internal..","com.cinenest.verification.internal..").check(classes);
        noClasses().that().haveSimpleName("BankService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().haveSimpleName("BankService").should().dependOnClassesThat().haveSimpleName("JdbcBankRepository").check(classes);
    }
    @Test void ledgerCoordinatesOnlyNamedOwnerPortsWithoutSqlOrAdapterDependencies() throws Exception {
        var classes=new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().haveSimpleName("LedgerService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().haveSimpleName("LedgerService").should().dependOnClassesThat().haveSimpleName("JdbcLedgerRepository").check(classes);
        noClasses().that().resideInAPackage("com.cinenest.booking..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.payments..","com.cinenest.pricing..").check(classes);
        String service=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/payments/internal/LedgerService.java"));
        assertThat(service).doesNotContain("SELECT ","INSERT ","UPDATE ","DELETE ");
        String adapter=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/payments/internal/JdbcLedgerRepository.java"));
        assertThat(adapter).doesNotContain("cinenest_booking.","cinenest_pricing.","cinenest_identity.","cinenest_verification.");
    }
    @Test void adminReadServiceUsesOwnerPortsAndOwnedPersistenceOnly(){
        var classes=new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().haveSimpleName("AdminService").should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..","com.cinenest.verification..").check(classes);
        noClasses().that().haveSimpleName("VerificationAuditProjection").should().dependOnClassesThat().resideInAPackage("com.cinenest.identity.internal..").check(classes);
    }
    @Test void refundsUseOwnedPersistenceAndCurrentIdentityPortOnly() throws Exception {
        var classes=new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().haveSimpleName("RefundService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().haveSimpleName("RefundService").should().dependOnClassesThat().haveSimpleName("JdbcRefundRepository").check(classes);
        String adapter=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/payments/internal/JdbcRefundRepository.java"));
        assertThat(adapter).doesNotContain("cinenest_booking.","cinenest_identity.","cinenest_pricing.","cinenest_verification.");
    }

    @Test void notificationsOwnPersistenceAndNeverUseNetwork() throws Exception {
      var classes=new ClassFileImporter().importPackages("com.cinenest");
      noClasses().that().resideInAPackage("com.cinenest.notifications..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.payments..","com.cinenest.access..","com.cinenest.identity.internal..","java.net..","java.net.http..").check(classes);
      noClasses().that().haveSimpleName("NotificationService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
      String adapter=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/notifications/internal/JdbcNotificationRepository.java"));
      assertThat(adapter).doesNotContain("cinenest_payments.","cinenest_identity.","cinenest_access.","cinenest_booking.");
    }
    @Test void accessUsesPublicOwnerPortsAndOwnedPersistenceOnly()throws Exception{
      var classes=new ClassFileImporter().importPackages("com.cinenest");
      noClasses().that().resideInAPackage("com.cinenest.access..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.identity.internal..","com.cinenest.booking.internal..","com.cinenest.payments.internal..","com.cinenest.verification.internal..").check(classes);
      noClasses().that().haveSimpleName("AccessService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
      noClasses().that().haveSimpleName("AccessService").should().dependOnClassesThat().haveSimpleName("JdbcAccessRepository").check(classes);
      String adapter=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/access/internal/JdbcAccessRepository.java"));assertThat(adapter).doesNotContain("cinenest_booking.","cinenest_payments.","cinenest_identity.","cinenest_verification.");
      noClasses().that().resideInAPackage("com.cinenest.access..").should().dependOnClassesThat().resideInAnyPackage("java.net..","java.net.http..").check(classes);
    }
    @Test void iotUsesPublicPortsAndHasNoNetworkOrForeignPersistence() throws Exception {
 var classes=new ClassFileImporter().importPackages("com.cinenest");
 noClasses().that().resideInAPackage("com.cinenest.iot..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.access.internal..","com.cinenest.booking.internal..","com.cinenest.identity.internal..","com.cinenest.rooms.internal..","java.net..","java.net.http..").check(classes);
 noClasses().that().haveSimpleName("IotService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
 noClasses().that().haveSimpleName("IotService").should().dependOnClassesThat().haveSimpleName("JdbcIotRepository").check(classes);
 String adapter=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/iot/internal/JdbcIotRepository.java"));assertThat(adapter).doesNotContain("cinenest_booking.","cinenest_access.","cinenest_identity.","cinenest_rooms.");
 }

 @Test void turnoverStaysInRoomsAndUsesConsumerOwnedReadPorts() throws Exception {
 var classes=new ClassFileImporter().importPackages("com.cinenest");
 noClasses().that().resideInAPackage("com.cinenest.rooms..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.booking..","com.cinenest.access..","com.cinenest.iot..","com.cinenest.notifications..").check(classes);
 noClasses().that().haveSimpleName("TurnoverService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
 String adapter=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/rooms/internal/JdbcTurnoverRepository.java"));assertThat(adapter).doesNotContain("cinenest_booking.","cinenest_iot.","cinenest_access.","cinenest_payments.");
 }

 @Test void reportingNeverReadsForeignRepositoriesOrFinancialTables() throws Exception {
 var classes=new ClassFileImporter().importPackages("com.cinenest");
 noClasses().that().resideInAPackage("com.cinenest.reporting..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.payments.internal..","com.cinenest.rooms.internal..","com.cinenest.identity.internal..","org.springframework.jdbc..").check(classes);
 String query=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/payments/internal/JdbcBankReportRepository.java"));assertThat(query).doesNotContain("cinenest_booking.","cinenest_rooms.","UPDATE ","DELETE ");
 }
 @Test void staffBookingOperationsReadPreservesConsumerOwnershipAndNoCommands()throws Exception{
  var classes=new ClassFileImporter().importPackages("com.cinenest");
  noClasses().that().haveSimpleName("StaffBookingOperationsQuery").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.booking..","com.cinenest.iot..","org.springframework.jdbc..").check(classes);
  String adapter=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/booking/internal/OperationsBookingContextAdapter.java"));assertThat(adapter).contains("cinenest_booking.booking_revision").doesNotContain("FOR UPDATE","pg_advisory","cinenest_rooms.","cinenest_iot.","UPDATE ","INSERT ");
  String query=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/rooms/internal/StaffBookingOperationsQuery.java"));assertThat(query).doesNotContain(".command(",".create(",".update(",".evidence(",".audit(",".lock(");
 }

 @org.junit.jupiter.api.Test void calendarIsBookingOwnedReadOnlyAndDoesNotUseForeignSqlOrCommands()throws Exception{
  String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/booking/internal/InventoryCalendar.java"));
  org.assertj.core.api.Assertions.assertThat(source).contains("setReadOnly(true)","ISOLATION_REPEATABLE_READ","rooms.observe","LIMIT 1001").doesNotContain("reconcileLocked","cinenest_rooms.","cinenest_payments.","cinenest_iot.","cinenest_notifications.","FOR UPDATE","INSERT ","UPDATE ","DELETE ");
 }
}
