package com.cinenest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import static org.assertj.core.api.Assertions.assertThat;

class CommercialPolicyApprovalArchitectureTest {
    @Test
    void approvalIntakePreservesModuleAndMigrationBoundaries() throws Exception {
        ApplicationModules.of(CineNestApplication.class).verify();
        var migrations = Path.of("src/main/resources/db/migration");
        var pinned = Map.of(
            "V36__commercial_policy_drafts.sql", "bee769c1bed44bd52d4887975760f8775308945794c94c00b30e546ec39e7505",
            "V37__commercial_policy_binding.sql", "5b8300720fa412956c0e607fadb6e800ffccc3618e5cdaa989a771c760a6ecb5");
        for (var entry : pinned.entrySet()) {
            var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(migrations.resolve(entry.getKey())));
            assertThat(HexFormat.of().formatHex(digest)).isEqualTo(entry.getValue());
        }
        try (var paths = Files.list(migrations)) {
            assertThat(paths.map(path -> path.getFileName().toString()).toList())
                .noneMatch(name -> name.startsWith("V31__"))
                .contains("V38__commercial_policy_trusted_approval_intake.sql");
        }
    }
}
