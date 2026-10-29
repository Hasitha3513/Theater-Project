package com.cinenest;
import org.junit.jupiter.api.Test;import com.tngtech.archunit.core.importer.ClassFileImporter;import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
class AdvertisingArchitectureTest {
 @Test void bannerServiceUsesOnlyTheRoomsRepositoryPort(){var c=new ClassFileImporter().importPackages("com.cinenest");noClasses().that().haveSimpleName("BannerService").should().dependOnClassesThat().haveSimpleName("JdbcBannerRepository").check(c);noClasses().that().haveSimpleName("BannerService").should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..","com.cinenest.payments..","com.cinenest.pricing..","com.cinenest.booking..","com.cinenest.access..","com.cinenest.iot..","com.cinenest.identity.internal..").check(c);}
}
