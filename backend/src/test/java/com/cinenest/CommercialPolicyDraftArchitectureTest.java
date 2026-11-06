package com.cinenest;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import static org.assertj.core.api.Assertions.*;
class CommercialPolicyDraftArchitectureTest {
 @Test void moduleBoundariesRemainValid(){ApplicationModules.of(CineNestApplication.class).verify();}
 @Test void qualifiedMigrationBytesAndNoHistoricalV31(){try{var p=Path.of("src/main/resources/db/migration/V36__commercial_policy_drafts.sql");assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)))).isEqualTo("bee769c1bed44bd52d4887975760f8775308945794c94c00b30e546ec39e7505");try(var files=Files.list(p.getParent())){assertThat(files.filter(x->x.getFileName().toString().startsWith("V31__")).count()).isZero();}}catch(Exception e){throw new AssertionError(e);}}
 @Test void noActivationEndpointOrInternalIdentityImport()throws Exception{var root=Path.of("src/main/java/com/cinenest/pricing/internal");try(var files=Files.list(root)){for(var p:files.filter(p->p.getFileName().toString().contains("CommercialPolicy")).toList()){String s=Files.readString(p);assertThat(s).doesNotContain("import com.cinenest.identity.internal", "@PostMapping(\"/activate", "@PostMapping(\"/publish");}}}
}
