package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.in.InternalToolsUseCase;
import com.cocos.challenge.domain.model.CreatedUser;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class JdbcAccountRepositoryCreateUserIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    InternalToolsUseCase createUser;

    @Test
    @Tag("CU-I.08")
    @Transactional
    void createsUsersWithIncrementingAccountNumbers() {
        SeedData.reset(jdbc);

        assertThat(createUser.createUser("demo@test.com"))
                .isEqualTo(new CreatedUser(new UserId(5), "demo@test.com", "10005"));
        assertThat(createUser.createUser("second@test.com"))
                .isEqualTo(new CreatedUser(new UserId(6), "second@test.com", "10006"));
    }
}
