package com.cinenest.iot.internal;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class LockRoomAssignmentPolicyTest {
    static final UUID VENUE=UUID.randomUUID(),ROOM=UUID.randomUUID(),DEVICE=UUID.randomUUID(),KEY=UUID.randomUUID();
    static String payload() { return "{\"commandKey\":\""+KEY+"\",\"deviceId\":\""+DEVICE+"\",\"expectedAssignmentRevision\":0,\"expectedRoomConfiguration\":\"0:0:\",\"expectedDeviceMetadataRevision\":1}"; }
    static MockEnvironment enabled(String... profiles) {
        var env=new MockEnvironment().withProperty("cinenest.iot.lock-room-assignment.enabled","true")
            .withProperty("cinenest.iot.lock-room-assignment.mode","QUALIFICATION_ONLY");
        env.setActiveProfiles(profiles); return env;
    }
    LockRoomAssignmentPolicy.Create parse(String value) { return new LockRoomAssignmentPolicy(new MockEnvironment()).parse(VENUE,ROOM,value.getBytes(StandardCharsets.UTF_8)); }
    @Test void defaultsHaveNoControllerServiceOrJdbcAdapter() {
        new ApplicationContextRunner().withUserConfiguration(LockRoomAssignmentController.class,LockRoomAssignmentService.class,JdbcLockRoomAssignmentRepository.class)
            .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(LockRoomAssignmentController.class)
                .doesNotHaveBean(LockRoomAssignmentService.class).doesNotHaveBean(JdbcLockRoomAssignmentRepository.class));
    }
    @Test void onlyExplicitLocalSyntheticSetsArePermitted() {
        for (String[] profiles:new String[][]{{"local-synthetic"},{"local-synthetic","synthetic-quotation"},{"synthetic-quotation","local-synthetic"}})
            assertThatCode(() -> new LockRoomAssignmentPolicy(enabled(profiles)).configured()).doesNotThrowAnyException();
        for (String[] profiles:new String[][]{{},{"production"},{"synthetic-quotation"},{"local-synthetic","production"},{"local-synthetic","synthetic-quotation","unknown"}})
            assertThatThrownBy(() -> new LockRoomAssignmentPolicy(enabled(profiles)).configured()).isInstanceOf(LockRoomAssignmentPolicy.Failure.class);
    }
    @Test void disabledOrWrongModeIsDenied() {
        var env=enabled("local-synthetic"); env.setProperty("cinenest.iot.lock-room-assignment.mode","LIVE");
        assertThatThrownBy(() -> new LockRoomAssignmentPolicy(env).configured()).isInstanceOf(LockRoomAssignmentPolicy.Failure.class);
        assertThatThrownBy(() -> new LockRoomAssignmentPolicy(new MockEnvironment()).configured()).isInstanceOf(LockRoomAssignmentPolicy.Failure.class);
    }
    @Test void hashesCanonicalIdsAndScopeWithoutRetainingMutableArrays() {
        var a=parse(payload()); var b=parse(payload().replace(DEVICE.toString(),DEVICE.toString().toUpperCase(Locale.ROOT)));
        assertThat(a.hash()).isEqualTo(b.hash());byte[] hash=a.hash();hash[0]++;assertThat(hash).isNotEqualTo(a.hash());
        assertThat(a.toString()).isEqualTo("AssignmentCreate[REDACTED]");
        var policy=new LockRoomAssignmentPolicy(new MockEnvironment());
        assertThat(policy.parse(UUID.randomUUID(),ROOM,payload().getBytes(StandardCharsets.UTF_8)).hash()).isNotEqualTo(a.hash());
        assertThat(policy.parse(VENUE,UUID.randomUUID(),payload().getBytes(StandardCharsets.UTF_8)).hash()).isNotEqualTo(a.hash());
    }
    @ParameterizedTest @ValueSource(strings={"0.0","1","-1","\"0\"","null"})
    void invalidAssignmentRevision(String value) { assertThatThrownBy(() -> parse(payload().replace("\"expectedAssignmentRevision\":0","\"expectedAssignmentRevision\":"+value))).isInstanceOf(LockRoomAssignmentPolicy.Failure.class); }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.0","1e0","9223372036854775808","\"1\"","null"})
    void invalidMetadataRevision(String value) { assertThatThrownBy(() -> parse(payload().replace("\"expectedDeviceMetadataRevision\":1","\"expectedDeviceMetadataRevision\":"+value))).isInstanceOf(LockRoomAssignmentPolicy.Failure.class); }
    @Test void rejectsUnknownDuplicateMalformedOversizedAndActivationRequests() {
        for(String value:List.of("{}",payload()+payload(),payload().replace("\"deviceId\":","\"customerAccessEnabled\":true,\"deviceId\":"),
            payload().replace("\"commandKey\":","\"commandKey\":\""+KEY+"\",\"commandKey\":"),
            payload().replace("0:0:",""),payload().replace("0:0:","x".repeat(16385))," ".repeat(32769)))
            assertThatThrownBy(() -> parse(value)).isInstanceOf(LockRoomAssignmentPolicy.Failure.class);
    }
    @Test void paginationIsBoundedAndStrict() {
        assertThat(LockRoomAssignmentPolicy.page(null)).isZero();assertThat(LockRoomAssignmentPolicy.page("10000")).isEqualTo(10000);
        for(String page:List.of("-1","01","1.0","10001","999999999999"))assertThatThrownBy(() -> LockRoomAssignmentPolicy.page(page)).isInstanceOf(LockRoomAssignmentPolicy.Failure.class);
    }
}
