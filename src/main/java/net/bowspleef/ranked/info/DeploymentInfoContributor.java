package net.bowspleef.ranked.info;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Spec 039 (REQ-039-10, ADR-0037): {@code GET /actuator/info} gives the schema version (the version of the last
 * migration applied to the database) and the commit the API was built from, so that the deployment of the bot can
 * refuse an API that is too old. Open without the API key, like the health probe.
 */
@Component
public class DeploymentInfoContributor implements InfoContributor {

    private final Flyway flyway;
    private final String commit;

    public DeploymentInfoContributor(Flyway flyway, @Value("${APP_COMMIT:unknown}") String commit) {
        this.flyway = flyway;
        this.commit = commit;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("schemaVersion", schemaVersion());
        builder.withDetail("commit", commit.isBlank() ? "unknown" : commit);
    }

    /** The version of the last migration applied ({@code V36} gives 36), or {@code null} before the first one. */
    private Object schemaVersion() {
        MigrationInfo current = flyway.info().current();
        if (current == null || current.getVersion() == null) {
            return null;
        }
        String version = current.getVersion().getVersion();
        try {
            return Integer.valueOf(version);
        } catch (NumberFormatException e) {
            return version;
        }
    }
}
