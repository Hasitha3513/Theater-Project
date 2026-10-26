package com.cinenest;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LockRegistryArchitectureTest {
    @Test void registryHasNoNetworkFilesystemOrForeignPersistenceDependency(){
        var classes=new ClassFileImporter().importPackages("com.cinenest.iot");
        noClasses().that().haveNameMatching(".*LockRegistry.*").should().dependOnClassesThat().resideInAnyPackage("java.net..","java.net.http..","java.nio.file..","com.cinenest.access.internal..","com.cinenest.booking..","com.cinenest.payments..").check(classes);
    }
    @Test void registryIsDisabledByDefaultAndDoesNotWireProvisioningWorkers() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/cinenest/iot/internal/LockRegistryController.java"));assertThat(source).contains("ConditionalOnProperty","havingValue=\"true\"").doesNotContain("matchIfMissing");
        String service=Files.readString(Path.of("src/main/java/com/cinenest/iot/internal/LockRegistryService.java"));assertThat(service).doesNotContain("AccessService","AccessWorker","createTimedPin","BookingAccess","@Scheduled");
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V27__inactive_smart_lock_registry.sql"));assertThat(sql).doesNotContain("cinenest_access.","cinenest_payments.","cinenest_booking.","access_grant","INSERT INTO").contains("DEFERRABLE INITIALLY DEFERRED","registry_no_truncate");
    }
}
