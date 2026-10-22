package com.cinenest;
import org.junit.jupiter.api.Test;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
class ManagedGrantArchitectureTest {
 @Test void grantQueryUsesIdentityRepositoryNotJdbcOrOtherModuleInternals(){
  var classes=new ClassFileImporter().importPackages("com.cinenest");
  noClasses().that().haveSimpleName("ManagedGrantQuery").should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..").check(classes);
  noClasses().that().resideInAPackage("com.cinenest.identity..").should().dependOnClassesThat().resideInAnyPackage("com.cinenest.booking.internal..","com.cinenest.payments.internal..","com.cinenest.rooms.internal..","com.cinenest.access.internal..").check(classes);
 }
 @Test void migrationAndRepositoryCannotBootstrapOrMutateBusinessAuthority()throws Exception{
  var root=java.nio.file.Path.of("src/main");var migration=java.nio.file.Files.readString(root.resolve("resources/db/migration/V20__managed_grant_read_audit.sql"));
  assertThat(migration).doesNotContain("INSERT INTO","access_grant","account_scope");
  var repository=java.nio.file.Files.readString(root.resolve("java/com/cinenest/identity/internal/ManagedGrantRepository.java"));
  assertThat(repository).doesNotContain("UPDATE cinenest_identity","DELETE FROM","INSERT INTO cinenest_identity.access_grant");
  assertThat(repository).contains("INSERT INTO cinenest_identity.grant_read_audit");
 }
 @Test void readSurfaceIsOptInAndHasNoMutationHandlers()throws Exception{
  var source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/cinenest/identity/internal/ManagedGrantController.java"));
  assertThat(source).contains("cinenest.identity.grant-read.enabled","havingValue=\"true\"");
  assertThat(source).doesNotContain("@PostMapping","@PutMapping","@DeleteMapping","@PatchMapping","matchIfMissing");
 }
}
