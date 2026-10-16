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
        assertThat(modules.stream().map(module -> module.getIdentifier().toString())).containsExactlyInAnyOrder("foundation", "identity", "rooms", "pricing", "booking");
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
    @Test void bookingUsesOwnerPortsAndItsOwnPersistenceAdapter() {
        var classes=new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().resideInAPackage("com.cinenest.booking..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.rooms.internal..","com.cinenest.identity.internal..","com.cinenest.pricing..").check(classes);
        noClasses().that().haveSimpleName("InventoryService").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
        noClasses().that().haveSimpleName("InventoryService").should().dependOnClassesThat().haveSimpleName("JdbcInventoryRepository").check(classes);
    }
}
