package com.cinenest;
import java.nio.file.*;import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.assertThat;
class ExtensionPreviewArchitectureTest {
 @Test void previewUsesPublicPortsAndNoForeignSqlOrDomainWrites()throws Exception{String s=Files.readString(Path.of("src/main/java/com/cinenest/booking/internal/ExtensionPreviewService.java"));assertThat(s).contains("ExtensionPricing","InventoryConstraints","AmendmentGuards.Access","ScopedAuthorization","access.lock(booking)").doesNotContain("cinenest_pricing.","cinenest_access.","cinenest_rooms.","cinenest_payments.","INSERT INTO","UPDATE ","DELETE FROM","reconcileLocked","NotificationFacts");}
 @Test void pricingAdapterIsPureAndDoesNotLookUpCurrentPublication()throws Exception{String s=Files.readString(Path.of("src/main/java/com/cinenest/pricing/internal/ExtensionPricingAdapter.java"));assertThat(s).contains("QuoteRules.calculate","Math.subtractExact","before.get(\"lines\")").doesNotContain("JdbcClient","Repository","QuoteService","PublicationRepository","TransactionTemplate");}
}
