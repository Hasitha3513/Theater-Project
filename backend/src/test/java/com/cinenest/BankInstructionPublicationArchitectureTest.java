package com.cinenest;
import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
class BankInstructionPublicationArchitectureTest {
 @Test void publicationNeverReadsForeignPersistenceOrContactsProviders(){var c=new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.cinenest.payments");noClasses().that().haveNameMatching(".*InstructionPublication.*").should().dependOnClassesThat().resideInAnyPackage("java.net..","java.net.http..","java.nio.file..","com.cinenest.identity.internal..","com.cinenest.booking..","com.cinenest.pricing..","com.cinenest.access..","com.cinenest.iot..").check(c);}
 @Test void serviceUsesRepositoryPortAndAdapterOwnsOnlyPaymentsSql()throws Exception{var c=new ClassFileImporter().importPackages("com.cinenest.payments");noClasses().that().haveSimpleName("BankInstructionPublicationService").should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..","java.sql..").check(c);String source=Files.readString(Path.of("src/main/java/com/cinenest/payments/internal/JdbcBankInstructionPublicationRepository.java"));assertThat(source).doesNotContain("UPDATE cinenest_","DELETE FROM ","TRUNCATE ","cinenest_booking.","cinenest_identity.","bank_payment_request","payment_allocation","incoming_payment_credit","bank_instruction_request_binding");assertThat(source).contains("FOR UPDATE OF p");}
}
