package com.cinenest.payments.internal;
import java.util.UUID;
interface InstructionResolver {
 record Fixture(String label,String accountDisplay,String warning){}
 Fixture resolve(UUID reference,UUID version,String digest);
}
