package com.cinenest.iot.internal;
import com.cinenest.iot.LockIntegration.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class LockAdapterCatalogTest {
    private Descriptor descriptor(String provider, String adapter) { return new Descriptor(new AdapterId(provider, adapter, "TEST", "1"), Map.of("F", Set.of("1"))); }
    @Test void emptyDefaultDoesNotDiscoverOrActivateAnything() { var c = new LockAdapterCatalog(); assertThat(c.size()).isZero(); assertThatThrownBy(() -> c.lookup(descriptor("P", "A").id())).isInstanceOf(IllegalArgumentException.class); }
    @Test void lookupIsExactAndIndependentOfRegistrationOrder() {
        var a = descriptor("P", "A"); var b = descriptor("OTHER", "B");
        for (var order : List.of(List.of(a, b), List.of(b, a))) { var c = new LockAdapterCatalog(order); assertThat(c.lookup(a.id())).isEqualTo(a); assertThat(c.lookup(b.id())).isEqualTo(b); }
    }
    @Test void duplicateIdentityRejectedEvenWhenMetadataDiffers() {
        var a = descriptor("P", "A"); var changed = new Descriptor(a.id(), Map.of("OTHER", Set.of("2")));
        assertThatThrownBy(() -> new LockAdapterCatalog(List.of(a, a))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockAdapterCatalog(List.of(a, changed))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unknownProviderAdapterProtocolAndVersionNeverFallback() {
        var c = new LockAdapterCatalog(List.of(descriptor("P", "A")));
        for (var id : List.of(new AdapterId("UNKNOWN", "A", "TEST", "1"), new AdapterId("P", "OTHER", "TEST", "1"),
            new AdapterId("P", "A", "OTHER", "1"), new AdapterId("P", "A", "TEST", "2"), new AdapterId("p", "A", "TEST", "1")))
            assertThatThrownBy(() -> c.lookup(id)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void callerCollectionCannotRegisterLaterDescriptors() { var items = new ArrayList<Descriptor>(); items.add(descriptor("P", "A")); var c = new LockAdapterCatalog(items); items.clear(); assertThat(c.size()).isEqualTo(1); }
}
