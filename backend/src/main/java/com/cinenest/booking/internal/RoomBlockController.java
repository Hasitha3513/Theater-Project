package com.cinenest.booking.internal;

import java.security.Principal;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="cinenest.booking.room-blocks.enabled",havingValue="true")
@RequestMapping("/api/admin/venues/{venueId}/rooms/{roomId}/blocks")
class RoomBlockController {
    private final RoomBlocks service;
    private final InventoryDenialAudit denial;
    RoomBlockController(RoomBlocks service,InventoryDenialAudit denial){this.service=service;this.denial=denial;}
    private UUID actor(Principal p){if(p==null)throw new InventoryFailure(401,"Sign-in required");return InventoryRules.id(p.getName());}
    @PostMapping ResponseEntity<?> create(Principal p,@PathVariable String venueId,@PathVariable String roomId,jakarta.servlet.http.HttpServletRequest request){
        if(request.getQueryString()!=null&&!request.getQueryString().isEmpty())throw InventoryRules.invalid();
        Map<String,Object> body;
        try {
            String type=request.getContentType();if(type==null||!type.split(";",2)[0].trim().equalsIgnoreCase("application/json"))throw InventoryRules.invalid();
            byte[] bytes=request.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new InventoryFailure(413,"Room Blocking request too large");
            try(var parser=tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(bytes)){
                if(parser.nextToken()!=tools.jackson.core.JsonToken.START_OBJECT)throw InventoryRules.invalid();parser.skipChildren();if(parser.nextToken()!=null)throw InventoryRules.invalid();
            }
            var text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            body=com.nimbusds.jose.util.JSONObjectUtils.parse(text);
        }catch(InventoryFailure failure){throw failure;}catch(Exception invalid){throw InventoryRules.invalid();}
        return ok(service.create(actor(p),InventoryRules.id(venueId),InventoryRules.id(roomId),body));
    }
    @GetMapping ResponseEntity<?> list(Principal p,@PathVariable String venueId,@PathVariable String roomId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size,jakarta.servlet.http.HttpServletRequest request){
        if(request.getParameterMap().entrySet().stream().anyMatch(e->!Set.of("page","size").contains(e.getKey())||e.getValue().length!=1))throw InventoryRules.invalid();return ok(service.list(actor(p),InventoryRules.id(venueId),InventoryRules.id(roomId),page,size));}
    @GetMapping("/{blockId}") ResponseEntity<?> detail(Principal p,@PathVariable String venueId,@PathVariable String roomId,@PathVariable String blockId,jakarta.servlet.http.HttpServletRequest request){if(request.getQueryString()!=null&&!request.getQueryString().isEmpty())throw InventoryRules.invalid();return ok(service.detail(actor(p),InventoryRules.id(venueId),InventoryRules.id(roomId),InventoryRules.id(blockId)));}
    private ResponseEntity<?> ok(Object data){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(data);}
    private ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","private, no-store").body(Map.of("message",message));}
    @ExceptionHandler(InventoryFailure.class) ResponseEntity<?> invalid(InventoryFailure e,Principal p){if(e.status==403)try{denial.denied(p==null?null:UUID.fromString(p.getName()));}catch(DataAccessException failed){return error(503,"Room Blocking unavailable");}return error(e.status,e.getMessage());}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409,"Inventory changed or conflicts; no booking was changed");}
    @ExceptionHandler({DataAccessException.class,org.springframework.transaction.TransactionException.class}) ResponseEntity<?> unavailable(){return error(503,"Room Blocking unavailable");}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> malformed(){return error(400,"Malformed Room Blocking request");}
}
