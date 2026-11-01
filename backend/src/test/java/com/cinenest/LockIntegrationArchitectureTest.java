package com.cinenest;
import com.cinenest.iot.LockIntegration;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;
class LockIntegrationArchitectureTest {
    @Test void foundationCannotUseRuntimeNetworkPersistenceOrForeignInternals() {
        var c=new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.cinenest.iot");
        noClasses().that().haveNameMatching(".*(LockIntegration|LockAdapterCatalog|LockCompatibilityPolicy).*")
            .should().dependOnClassesThat().resideInAnyPackage("java.net..","java.net.http..","java.sql..","javax.sql..","java.nio.file..","java.io..","java.lang.reflect..","java.util.concurrent..","org.springframework..","com.cinenest.identity..","com.cinenest.booking..","com.cinenest.payments..","com.cinenest.rooms..","com.cinenest.access.internal..").check(c);
    }
    @Test void contractsExposeNoRuntimeAdapterAndReuseExistingAccessTypes() {
        assertThat(LockIntegration.Adapter.class.isInterface()).isTrue();
        assertThat(LockIntegration.Decision.class.isRecord()).isTrue();
        assertThat(new LockIntegration.Decision(LockIntegration.Reason.CONTRACT_COMPATIBLE).customerAccessEnabled()).isFalse();
    }
}
