package com.cinenest.identity.internal;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;

class CustomerCredentialLifecycleArchitectureTest {
    @Test void coreHasNoRuntimeRegistrationPersistenceNetworkOrCrossModuleDependency() {
        var all = new java.util.ArrayList<Class<?>>();
        for (Class<?> c : new Class<?>[] {CustomerCredentialLifecycle.class, CustomerCredentialPorts.class, CustomerCredentialSecurity.class}) { all.add(c); java.util.Collections.addAll(all, c.getDeclaredClasses()); }
        var classes = new ClassFileImporter().importClasses(all.toArray(Class<?>[]::new));
        noClasses().should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "jakarta.servlet..", "java.sql..", "java.net..", "java.net.http..",
            "com.cinenest.payments..", "com.cinenest.booking..").check(classes);
        for (Class<?> c : all) {
            assertThat(Modifier.isPublic(c.getModifiers())).isFalse();
            assertThat(c.getAnnotations()).isEmpty();
            for (var method : c.getDeclaredMethods()) assertThat(method.getAnnotations()).isEmpty();
        }
    }
    @Test void futureInfrastructureIsOnlyUnimplementedPorts() {
        for (Class<?> c : new Class<?>[] {CustomerCredentialPorts.TrustedTime.class, CustomerCredentialPorts.CredentialRead.class,
            CustomerCredentialPorts.AbuseLimits.class, CustomerCredentialPorts.AtomicCommit.class, CustomerCredentialPorts.ProtectedDelivery.class}) {
            assertThat(c.isInterface()).isTrue();
            assertThat(c.getDeclaredMethods()).allSatisfy(m -> assertThat(Modifier.isAbstract(m.getModifiers())).isTrue());
        }
    }
}
