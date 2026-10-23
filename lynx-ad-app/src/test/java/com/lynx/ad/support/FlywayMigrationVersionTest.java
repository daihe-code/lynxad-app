package com.lynx.ad.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class FlywayMigrationVersionTest {
    @Test void migrationVersionsAreUniqueAndMatchAcrossDatabaseVendors() throws Exception {
        assertEquals(versions("migration"), versions("migration-mysql"));
    }

    private Set<String> versions(String directory) throws Exception {
        var resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/" + directory + "/V*__*.sql");
        assertTrue(resources.length > 0, directory);
        Set<String> versions = new TreeSet<>();
        for (var resource : resources) {
            String name = resource.getFilename();
            String version = name.substring(1, name.indexOf("__"));
            assertTrue(versions.add(version), "Duplicate migration version in " + directory + ": " + version);
        }
        return versions;
    }
}
