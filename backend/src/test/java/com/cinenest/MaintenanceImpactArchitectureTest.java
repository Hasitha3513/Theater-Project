package com.cinenest;
import org.junit.jupiter.api.Test;import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;
class MaintenanceImpactArchitectureTest {
 @Test void impactOnlyReadsOwnedInventoryAndUsesPublicOwnerPorts()throws Exception{
  String s=Files.readString(Path.of("src/main/java/com/cinenest/booking/internal/MaintenanceImpactQuery.java"));assertThat(s).contains("rooms.observe","ISOLATION_REPEATABLE_READ","setReadOnly(true)","LIMIT 1001");assertThat(s).doesNotContain("reconcile","UPDATE ","DELETE ","FROM cinenest_rooms","FROM cinenest_identity",".internal.Room",".internal.Identity","rooms.lock");
 }
 @Test void migrationAddsOneAuditTableWithoutChangingDomainOrAuthority()throws Exception{
  String s=Files.readString(Path.of("src/main/resources/db/migration/V21__maintenance_impact_read_audit.sql"));assertThat(s.split("CREATE TABLE")).hasSize(2);assertThat(s).contains("maintenance_impact_read_audit","reject_history_mutation").doesNotContain("ALTER TABLE","INSERT INTO","access_grant","inventory_block","reservation_hold");
 }
 @Test void controllerOnlyAllowsReadAndNoClosureOrReadinessAuthority()throws Exception{
  String s=Files.readString(Path.of("src/main/java/com/cinenest/booking/internal/MaintenanceImpactController.java"));assertThat(s).contains("@GetMapping","Cache-Control","no-store").doesNotContain("@PostMapping","@PutMapping","@DeleteMapping","canClose","roomReady","safeToEnter","reopeningApproved");
 }
}
