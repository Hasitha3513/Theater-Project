package com.cinenest.identity.internal;

import java.util.UUID;

/** Explicit after-commit dispatch. Uncertain sends are never automatically retried. */
final class CustomerCredentialDeliveryDispatcher {
    final CustomerCredentialRepository repository;final CustomerCredentialConfiguration.Crypto crypto;
    final CustomerCredentialConfiguration.DeliveryGateway gateway;final CustomerCredentialConfiguration.Settings settings;
    CustomerCredentialDeliveryDispatcher(CustomerCredentialRepository repository,CustomerCredentialConfiguration.Crypto crypto,CustomerCredentialConfiguration.DeliveryGateway gateway,CustomerCredentialConfiguration.Settings settings){this.repository=repository;this.crypto=crypto;this.gateway=gateway;this.settings=settings;}
    void dispatchPending(int limit){if(limit<1||limit>4)throw new IllegalArgumentException();for(UUID id:repository.pending(limit,settings))dispatch(id);}
    void dispatch(UUID id){
        var claimed=repository.claim(id,settings);if(claimed.isEmpty())return;
        var message=claimed.get();String result="UNKNOWN";
        try{
            String[] payload=crypto.open(id,message.payload(),message.keyVersion()).split("\n",-1);
            if(!repository.deliveryCurrent(id,settings)||payload.length!=2||!payload[1].matches("[0-9a-f]{64}")||!message.expires().isAfter(repository.now()))throw new IllegalStateException("Delivery unavailable");
            var status=gateway.send(new CustomerCredentialConfiguration.DeliveryMessage(id,payload[0],payload[1],message.purpose().equals("VERIFY")?"VERIFY_EMAIL":"RECOVER_ACCOUNT",message.expires()));
            if(status==CustomerCredentialConfiguration.DeliveryStatus.ACCEPTED)result="ACCEPTED";
        }catch(RuntimeException unavailable){/* No provider output, protected payload or exception message is logged. */}
        repository.deliveryResult(id,result);
    }
}
