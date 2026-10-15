package com.cinenest.foundation.internal;

import com.cinenest.foundation.MigrationStatus;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

@Component
final class FlywayMigrationStatus implements MigrationStatus {
    private final Flyway flyway;

    FlywayMigrationStatus(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public boolean isCurrent() {
        return flyway.info().current() != null && flyway.info().pending().length == 0;
    }
}
