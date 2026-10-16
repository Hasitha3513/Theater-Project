package com.cinenest.booking.internal;
import com.cinenest.rooms.InventoryConstraints;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class InventoryRulesTest {
    InventoryConstraints.Facts facts(String zone,Integer buffer,boolean post,List<InventoryConstraints.Minimum> policies) {
        return new InventoryConstraints.Facts(UUID.randomUUID(),UUID.randomUUID(),zone,true,5,buffer,post,"cfg",
            java.util.stream.IntStream.rangeClosed(1,7).mapToObj(d->new InventoryConstraints.Window(d,0,1440)).toList(),policies);
    }
    List<InventoryConstraints.Minimum> minimum() {return List.of(new InventoryConstraints.Minimum(UUID.randomUUID(),180,Instant.parse("2026-01-01T00:00:00Z")));}
    @Test void closingSeparatesCustomerSessionAndApprovedPostCloseCleaning() {
        var f=facts("Asia/Colombo",15,false,minimum());var start=InventoryRules.local("2026-10-20T21:00",f.timezone());
        assertThatThrownBy(()->InventoryRules.candidate(f,start,180,4)).isInstanceOf(InventoryFailure.class);
        var approved=facts("Asia/Colombo",15,true,minimum());var c=InventoryRules.candidate(approved,start,180,4);
        assertThat(Duration.between(c.end(),c.blockedUntil()).toMinutes()).isEqualTo(15);
        assertThatThrownBy(()->InventoryRules.candidate(approved,start,181,4)).isInstanceOf(InventoryFailure.class);
    }
    @Test void capacityMissingPolicyBufferAndFuturePolicyFailClosed() {
        var f=facts("Asia/Colombo",0,false,minimum());var start=InventoryRules.local("2026-10-20T10:00",f.timezone());
        assertThatThrownBy(()->InventoryRules.candidate(f,start,180,6)).isInstanceOf(InventoryFailure.class);
        assertThatThrownBy(()->InventoryRules.candidate(facts("Asia/Colombo",null,false,minimum()),start,180,4)).isInstanceOf(InventoryFailure.class);
        assertThatThrownBy(()->InventoryRules.candidate(facts("Asia/Colombo",0,false,List.of()),start,180,4)).isInstanceOf(InventoryFailure.class);
        var policies=new ArrayList<>(minimum());policies.add(new InventoryConstraints.Minimum(UUID.randomUUID(),240,start.plusSeconds(3600)));
        assertThatThrownBy(()->InventoryRules.candidate(facts("Asia/Colombo",0,false,policies),start,180,4)).isInstanceOf(InventoryFailure.class);
        policies.set(1,new InventoryConstraints.Minimum(UUID.randomUUID(),240,start.plusSeconds(180*60)));
        assertThat(InventoryRules.candidate(facts("Asia/Colombo",0,false,policies),start,180,4).buffer()).isZero();
    }
    @Test void dstGapsAndFoldsAndFractionalSecondsAreRejected() {
        for(String time:List.of("2026-03-08T02:30","2026-11-01T01:30","2026-10-20T10:00:30"))
            assertThatThrownBy(()->InventoryRules.local(time,"America/New_York")).isInstanceOf(InventoryFailure.class);
        assertThat(InventoryRules.local("2026-10-20T10:00","Asia/Colombo")).isEqualTo(Instant.parse("2026-10-20T04:30:00Z"));
    }
    @Test void expiryIsCappedBySessionStartAndNeverAcceptsAPastStart() {
        Instant now=Instant.parse("2026-10-16T00:00:00Z");
        assertThat(InventoryRules.expires(now,now.plusSeconds(300),600)).isEqualTo(now.plusSeconds(300));
        assertThat(InventoryRules.expires(now,now.plusSeconds(3600),600)).isEqualTo(now.plusSeconds(600));
        assertThatThrownBy(()->InventoryRules.expires(now,now,600)).isInstanceOf(InventoryFailure.class);
    }
}
