package com.cinenest.iot.internal;
import java.util.*;import org.springframework.stereotype.Component;import org.springframework.boot.ApplicationArguments;import org.springframework.boot.ApplicationRunner;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.core.env.Environment;import org.springframework.transaction.annotation.Transactional;import com.cinenest.rooms.PackageConstraints;
/** Explicit synthetic bootstrap only. No production inventory discovery or pairing. */
@Component
@ConditionalOnProperty(name="cinenest.iot.bootstrap-enabled",havingValue="true")
class IotBootstrap implements ApplicationRunner {
 private final IotRepository repo;private final IotPolicy policy;private final Environment env;private final PackageConstraints rooms;
 IotBootstrap(IotRepository r,IotPolicy p,Environment e,PackageConstraints rooms){repo=r;policy=p;env=e;this.rooms=rooms;}
 @Transactional public void run(ApplicationArguments ignored){policy.configured();UUID id=UUID.fromString(env.getRequiredProperty("cinenest.iot.fixture-device")),venue=UUID.fromString(env.getRequiredProperty("cinenest.iot.fixture-venue")),room=UUID.fromString(env.getRequiredProperty("cinenest.iot.fixture-room"));if(rooms.inspect(venue,Set.of(room),false,true).isEmpty())throw new IllegalStateException("Synthetic fixture room unavailable");
  repo.register(id,venue,room);repo.configure(id,venue,env.getRequiredProperty("cinenest.iot.fixture-enabled",Boolean.class),env.getRequiredProperty("cinenest.iot.fixture-online",Boolean.class));
 }
}
