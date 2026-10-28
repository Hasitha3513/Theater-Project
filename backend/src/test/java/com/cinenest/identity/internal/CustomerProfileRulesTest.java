package com.cinenest.identity.internal;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class CustomerProfileRulesTest {
 @Test void namesAndDisplayOnlyPhonesAreBounded(){assertThat(CustomerProfileService.name("  Local Customer  ")).isEqualTo("Local Customer");assertThat(CustomerProfileService.name("සිංහල නම")).isNotBlank();for(String n:new String[]{"","x".repeat(121),"<script>","Name\nOther","Name\u202eOther","Name\u2028Other","Name\u2029Other"})assertThatThrownBy(()->CustomerProfileService.name(n)).isInstanceOf(IllegalArgumentException.class);assertThat(CustomerProfileService.phone(" ")).isNull();assertThat(CustomerProfileService.phone("+94 77 123 4567")).isEqualTo("+94 77 123 4567");for(String p:new String[]{"123","email@example.test","1234567890123456"})assertThatThrownBy(()->CustomerProfileService.phone(p)).isInstanceOf(IllegalArgumentException.class);}
}
