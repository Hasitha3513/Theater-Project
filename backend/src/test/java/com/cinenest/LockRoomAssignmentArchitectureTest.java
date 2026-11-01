package com.cinenest;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
class LockRoomAssignmentArchitectureTest {
 @Test void assignmentCannotOperateDevicesOrAccessForeignPersistence(){var classes=new ClassFileImporter().importPackages("com.cinenest.iot");noClasses().that().haveNameMatching(".*LockRoomAssignment.*").should().dependOnClassesThat().resideInAnyPackage("java.net..","java.net.http..","java.nio.file..","com.cinenest.rooms.internal..","com.cinenest.identity.internal..","com.cinenest.booking..","com.cinenest.payments..","com.cinenest.access..").check(classes);}
 @Test void serviceDependsOnlyOnPortsNotJdbc(){noClasses().that().haveSimpleName("LockRoomAssignmentService").should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..","java.sql..").check(new ClassFileImporter().importPackages("com.cinenest.iot"));}
}
