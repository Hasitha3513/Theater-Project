package com.cinenest.booking.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.InventoryConstraints;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name="cinenest.booking.room-blocks.enabled",havingValue="true")
class RoomBlocks {
    private final RoomBlockRepository repository;
    private final InventoryRepository inventory;
    private final InventoryConstraints rooms;
    private final ScopedAuthorization identity;
    private final TransactionTemplate tx;
    RoomBlocks(RoomBlockRepository repository,InventoryRepository inventory,InventoryConstraints rooms,ScopedAuthorization identity,PlatformTransactionManager manager,Environment environment,@Value("${cinenest.booking.room-blocks.mode:DISABLED}") String mode){
        var profiles=Set.of(environment.getActiveProfiles());
        if(!mode.equals("LOCAL_SYNTHETIC_ONLY")||!profiles.contains("synthetic-quotation")||!Set.of("synthetic-quotation","local-synthetic").containsAll(profiles))throw new IllegalStateException("Room Blocking requires isolated synthetic configuration");
        this.repository=repository;this.inventory=inventory;this.rooms=rooms;this.identity=identity;tx=new TransactionTemplate(manager);tx.setTimeout(10);
    }
    private InventoryConstraints.Facts authorize(UUID actor,UUID venue,UUID room,String permission){
        if(!identity.permits(actor,venue,permission,true)||!identity.member(actor,venue,true))throw new InventoryFailure(403,"Access denied");
        rooms.lock(venue);
        var f=rooms.inspect(room).orElseThrow(()->new InventoryFailure(404,"Room not found"));
        if(!f.venueId().equals(venue))throw new InventoryFailure(404,"Room not found");
        return f;
    }
    private static Instant instant(Object value){
        if(!(value instanceof String s)||s.length()>40)throw InventoryRules.invalid();
        try{var t=OffsetDateTime.parse(s).toInstant();if(t.getNano()%1000!=0||t.isBefore(Instant.parse("0001-01-01T00:00:00Z"))||!t.isBefore(Instant.parse("+10000-01-01T00:00:00Z")))throw InventoryRules.invalid();return t;}catch(DateTimeException e){throw InventoryRules.invalid();}
    }
    private static String digest(String... fields){
        try{var out=new StringBuilder();for(String f:fields)out.append(f.length()).append(':').append(f);return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(out.toString().getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    Map<String,Object> create(UUID actor,UUID venue,UUID room,Map<String,Object> body){
        InventoryRules.keys(body,"idempotencyKey","startAt","endAt","expectedConfiguration");
        UUID key=InventoryRules.id(InventoryRules.text(body,"idempotencyKey"));Instant start=instant(body.get("startAt")),end=instant(body.get("endAt"));
        if(!start.isBefore(end))throw InventoryRules.invalid();String configuration=InventoryRules.text(body,"expectedConfiguration");if(configuration.isBlank()||configuration.length()>16384)throw InventoryRules.invalid();
        String hash=digest("CREATE_ROOM_BLOCK",actor.toString(),venue.toString(),room.toString(),start.toString(),end.toString(),configuration);
        return tx.execute(status->{
            var facts=authorize(actor,venue,room,"inventory.block.create");
            var old=repository.command(actor,key);
            if(old.isPresent()){
                var prior=old.get();if(!prior.hash().equals(hash)||!prior.venue().equals(venue)||!prior.room().equals(room))throw new InventoryFailure(409,"Command already used with another request");
                // A replay does not need read authority and never creates another inventory/audit row.
                return view(prior);
            }
            if(!facts.configuration().equals(configuration))throw new InventoryFailure(409,"Room configuration changed; reload before creating a block");
            if(inventory.overlaps(room,start,end))throw new InventoryFailure(409,"Inventory conflict; no booking was changed");
            var block=new RoomBlockRepository.Block(UUID.randomUUID(),actor,key,hash,venue,room,start,end,configuration,repository.now());
            repository.create(block,UUID.randomUUID());return view(block);
        });
    }
    Map<String,Object> list(UUID actor,UUID venue,UUID room,int page,int size){
        if(page<0||size<1||size>100)throw InventoryRules.invalid();
        return tx.execute(status->{var facts=authorize(actor,venue,room,"inventory.read");var items=repository.list(venue,room,page,size).stream().map(RoomBlocks::view).toList();repository.audit(UUID.randomUUID(),actor,venue,room,null,null,"BLOCK_LIST_READ");return Map.of("items",items,"page",page,"size",size,"configuration",facts.configuration(),"timezone",facts.timezone(),"observedAt",repository.now(),"synthetic",true);});
    }
    Map<String,Object> detail(UUID actor,UUID venue,UUID room,UUID block){return tx.execute(status->{authorize(actor,venue,room,"inventory.read");var item=repository.find(venue,room,block).orElseThrow(()->new InventoryFailure(404,"Block not found"));repository.audit(UUID.randomUUID(),actor,venue,room,block,null,"BLOCK_DETAIL_READ");return view(item);});}
    static Map<String,Object> view(RoomBlockRepository.Block b){return Map.of("blockId",b.id(),"venueId",b.venue(),"roomId",b.room(),"startAt",b.start(),"endAt",b.end(),"revision",0,"createdAt",b.created(),"synthetic",true);}
}
