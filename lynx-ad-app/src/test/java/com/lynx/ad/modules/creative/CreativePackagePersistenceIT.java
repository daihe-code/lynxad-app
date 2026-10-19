package com.lynx.ad.modules.creative;

import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes=LynxAdApplication.class)
@ActiveProfiles("test")
class CreativePackagePersistenceIT extends AbstractContainerIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CreativeTaskRepository packages;

    @Test void confirmationAndClearingPersistWithoutDuplicatedAssignments() {
        com.lynx.ad.support.CreativePackagePersistenceAssertions.verifyConfirmationRoundTrip(jdbc,packages);
    }

}
