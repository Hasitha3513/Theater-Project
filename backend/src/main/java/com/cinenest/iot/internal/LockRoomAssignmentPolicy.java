package com.cinenest.iot.internal;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

@Component
final class LockRoomAssignmentPolicy {
    static final String CREATE = "iot.lock.assignment.create", READ = "iot.lock.assignment.read";
    static final int MAX_BODY = 32768;
    static final class Failure extends RuntimeException {
        final int status;
        Failure(int status) { super("Room lock assignment unavailable, changed or denied"); this.status = status; }
    }
    record Create(UUID key, UUID device, String roomConfiguration, long deviceRevision, byte[] hash) {
        Create { hash = hash.clone(); }
        @Override public byte[] hash() { return hash.clone(); }
        @Override public String toString() { return "AssignmentCreate[REDACTED]"; }
    }
    private final Environment env;
    LockRoomAssignmentPolicy(Environment env) { this.env = env; }
    void configured() {
        var profiles = Set.copyOf(Arrays.asList(env.getActiveProfiles()));
        if (!env.getProperty("cinenest.iot.lock-room-assignment.enabled", Boolean.class, false)
            || !"QUALIFICATION_ONLY".equals(env.getProperty("cinenest.iot.lock-room-assignment.mode"))
            || !(profiles.equals(Set.of("local-synthetic"))
                || profiles.equals(Set.of("local-synthetic", "synthetic-quotation")))) throw new Failure(503);
    }
    static UUID uuid(String value) {
        try { return LockRegistryPolicy.uuid(value); }
        catch (LockRegistryPolicy.Failure failure) { throw new Failure(400); }
    }
    static int page(String value) {
        if (value == null) return 0;
        try {
            if (!value.matches("0|[1-9][0-9]{0,4}")) throw new IllegalArgumentException();
            int page = Integer.parseInt(value);
            if (page > 10000) throw new IllegalArgumentException();
            return page;
        } catch (RuntimeException failure) { throw new Failure(400); }
    }
    Create parse(UUID venue, UUID room, byte[] body) {
        if (body == null || body.length > MAX_BODY) throw new Failure(400);
        try {
            try (var parser = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build().createParser(body)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) throw new Failure(400);
                parser.skipChildren();
                if (parser.nextToken() != null) throw new Failure(400);
            }
            var data = JSONObjectUtils.parse(new String(body, StandardCharsets.UTF_8));
            if (!data.keySet().equals(Set.of("commandKey", "deviceId", "expectedAssignmentRevision",
                "expectedRoomConfiguration", "expectedDeviceMetadataRevision"))) throw new Failure(400);
            if (!(data.get("expectedAssignmentRevision") instanceof Number zero)
                || !zero.toString().equals("0")) throw new Failure(400);
            if (!(data.get("expectedDeviceMetadataRevision") instanceof Number revision)
                || !revision.toString().matches("[1-9][0-9]{0,18}")) throw new Failure(400);
            long expected = Long.parseLong(revision.toString());
            if (!(data.get("expectedRoomConfiguration") instanceof String configuration)
                || configuration.isBlank() || configuration.length() > 16384
                || configuration.chars().anyMatch(Character::isISOControl)) throw new Failure(400);
            UUID key = uuid(Objects.toString(data.get("commandKey"), null));
            UUID device = uuid(Objects.toString(data.get("deviceId"), null));
            var canonical = new TreeMap<String, Object>();
            canonical.put("action", "CREATE_QUALIFICATION_ASSIGNMENT_V1");
            canonical.put("venueId", venue.toString()); canonical.put("roomId", room.toString());
            canonical.put("deviceId", device.toString()); canonical.put("commandKey", key.toString());
            canonical.put("expectedAssignmentRevision", 0);
            canonical.put("expectedDeviceMetadataRevision", expected);
            canonical.put("expectedRoomConfiguration", configuration);
            return new Create(key, device, configuration, expected,
                MessageDigest.getInstance("SHA-256").digest(JSONObjectUtils.toJSONString(canonical)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Failure failure) { throw failure; }
        catch (Exception failure) { throw new Failure(400); }
    }
}
