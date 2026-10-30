package com.cinenest.booking.internal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RoomBlockConfigurationTest {
 @Test void defaultsHaveNoControllerOrRuntimeService(){new ApplicationContextRunner().withUserConfiguration(RoomBlocks.class,RoomBlockController.class).run(c->{assertThat(c).hasNotFailed().doesNotHaveBean(RoomBlocks.class).doesNotHaveBean(RoomBlockController.class);});}
 @Test void enabledInvalidProfileOrModeFailsClosed(){for(String[] profiles:new String[][]{{},{"production"},{"synthetic-quotation","production"}}){var env=new MockEnvironment();env.setActiveProfiles(profiles);assertThatThrownBy(()->construct(env,"LOCAL_SYNTHETIC_ONLY")).isInstanceOf(IllegalStateException.class);}var env=new MockEnvironment();env.setActiveProfiles("synthetic-quotation");assertThatThrownBy(()->construct(env,"PRODUCTION")).isInstanceOf(IllegalStateException.class);}
 void construct(MockEnvironment env,String mode){new RoomBlocks(mock(RoomBlockRepository.class),mock(InventoryRepository.class),mock(com.cinenest.rooms.InventoryConstraints.class),mock(com.cinenest.identity.ScopedAuthorization.class),mock(org.springframework.transaction.PlatformTransactionManager.class),env,mode);}
}
