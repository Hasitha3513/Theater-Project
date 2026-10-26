package com.cinenest;

import com.cinenest.access.SmartLock;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SmartLockArchitectureTest {
    @Test void accessPortHasNoRuntimeTechnologyOrForeignModuleDependencies() {
        var classes = new ClassFileImporter().importPackages("com.cinenest");
        noClasses().that().haveNameMatching("com\\.cinenest\\.access\\.SmartLock.*")
                .should().dependOnClassesThat().resideInAnyPackage("com.cinenest.iot..", "org.springframework..",
                        "org.springframework.jdbc..", "java.net..", "java.net.http..", "java.sql..")
                .check(classes);
        assertThat(SmartLock.class.isInterface()).isTrue();
    }
    @Test void adapterExistsOnlyInTestsAndHasNoNetworkPersistenceOrBusinessMutationDependencies() throws Exception {
        var classes = new ClassFileImporter().importPackages("com.cinenest.iot.contract");
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.net..", "java.net.http..",
                "java.sql..", "javax.sql..", "org.springframework..", "java.nio.file..",
                "com.cinenest.payments..", "com.cinenest.booking..", "com.cinenest.access.internal..")
                .check(classes);
        try (var files = Files.walk(Path.of("src/main/java"))) {
            assertThat(files.filter(p -> p.getFileName().toString().equals("ContractLockAdapter.java")).count()).isZero();
        }
        assertThat(Files.readString(Path.of("src/main/java/com/cinenest/access/package-info.java"))).doesNotContain("\"iot\"");
        assertThat(Files.readString(Path.of("src/main/java/com/cinenest/iot/package-info.java"))).contains("\"access\"");
    }
}
