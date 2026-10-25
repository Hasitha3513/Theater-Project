package com.cinenest.payments.internal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FixedInstructionResolverTest {
 final FixedInstructionResolver resolver=new FixedInstructionResolver();
 @Test void exactVersionAndDigestResolveOnlyNonPayableFixtures(){for(UUID v:java.util.List.of(FixedInstructionResolver.VERSION1,FixedInstructionResolver.VERSION2)){var f=resolver.resolve(FixedInstructionResolver.REFERENCE,v,FixedInstructionResolver.digest(v));assertThat(f.accountDisplay()).isEqualTo("NO REAL RECEIVING ACCOUNT");assertThat(f.warning()).isEqualTo("DO NOT TRANSFER MONEY");}}
 @Test void changedReferenceVersionOrHashNeverFallsBack(){assertThatThrownBy(()->resolver.resolve(UUID.randomUUID(),FixedInstructionResolver.VERSION1,FixedInstructionResolver.digest(FixedInstructionResolver.VERSION1))).isInstanceOf(BankFailure.class);assertThatThrownBy(()->resolver.resolve(FixedInstructionResolver.REFERENCE,UUID.randomUUID(),"a".repeat(64))).isInstanceOf(BankFailure.class);assertThatThrownBy(()->resolver.resolve(FixedInstructionResolver.REFERENCE,FixedInstructionResolver.VERSION1,FixedInstructionResolver.digest(FixedInstructionResolver.VERSION2))).isInstanceOf(BankFailure.class);}
}
