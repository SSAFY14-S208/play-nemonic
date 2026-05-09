package com.nemonicworld;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemonicworld.support.IntegrationTest;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

@IntegrationTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void applicationUsesKstDefaultTimeZone() {
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

}
