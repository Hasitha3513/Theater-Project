package com.cinenest.rooms.internal;

import java.net.URI;
import java.security.Principal;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
class RoomController {
    private final RoomService service;
    private final DeniedConfigurationAudit audit;
    RoomController(RoomService service,DeniedConfigurationAudit audit){this.service=service;this.audit=audit;}
    private UUID actor(Principal p){if(p==null)throw new RoomFailure(401,"Sign-in required");try{return UUID.fromString(p.getName());}catch(IllegalArgumentException e){throw new RoomFailure(403,"Access denied");}}
    @GetMapping("/api/catalog/venues") Object publicVenues(){return service.venues(null,true);}
    @GetMapping("/api/catalog/rooms") Object publicRooms(@RequestParam(required=false) UUID venueId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int minimumCapacity,@RequestParam(defaultValue="") String facility,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size){return service.rooms(null,venueId,q,minimumCapacity,facility,null,page,size,true);}
    @GetMapping("/api/catalog/rooms/{roomId}") Object publicRoom(@PathVariable String roomId){return service.room(null,null,ConfigurationRules.uuid(roomId),true);}
    @GetMapping("/api/admin/venues") ResponseEntity<?> venues(Principal p){return ok(service.venues(actor(p),false));}
    @PostMapping("/api/admin/venues") ResponseEntity<?> createVenue(Principal p,@RequestBody Map<String,Object> d){UUID id=ConfigurationRules.uuid(ConfigurationRules.text(d,"venueId",36,true));return ResponseEntity.created(URI.create("/api/admin/venues/"+id)).header("Cache-Control","no-store").body(service.saveVenue(actor(p),id,d,true));}
    @GetMapping("/api/admin/venues/{venueId}") ResponseEntity<?> venue(Principal p,@PathVariable String venueId){return ok(service.venue(actor(p),ConfigurationRules.uuid(venueId)));}
    @PutMapping("/api/admin/venues/{venueId}") ResponseEntity<?> updateVenue(Principal p,@PathVariable String venueId,@RequestBody Map<String,Object> d){return ok(service.saveVenue(actor(p),ConfigurationRules.uuid(venueId),d,false));}
    @GetMapping("/api/admin/venues/{venueId}/rooms") ResponseEntity<?> rooms(Principal p,@PathVariable String venueId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int minimumCapacity,@RequestParam(defaultValue="") String facility,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size){return ok(service.rooms(actor(p),ConfigurationRules.uuid(venueId),q,minimumCapacity,facility,enabled,page,size,false));}
    @PostMapping("/api/admin/venues/{venueId}/rooms") ResponseEntity<?> createRoom(Principal p,@PathVariable String venueId,@RequestBody Map<String,Object> d){UUID v=ConfigurationRules.uuid(venueId),id=UUID.randomUUID();return ResponseEntity.created(URI.create("/api/admin/venues/"+v+"/rooms/"+id)).header("Cache-Control","no-store").body(service.saveRoom(actor(p),v,id,d,true));}
    @GetMapping("/api/admin/venues/{venueId}/rooms/{roomId}") ResponseEntity<?> room(Principal p,@PathVariable String venueId,@PathVariable String roomId){return ok(service.room(actor(p),ConfigurationRules.uuid(venueId),ConfigurationRules.uuid(roomId),false));}
    @PutMapping("/api/admin/venues/{venueId}/rooms/{roomId}") ResponseEntity<?> updateRoom(Principal p,@PathVariable String venueId,@PathVariable String roomId,@RequestBody Map<String,Object> d){return ok(service.saveRoom(actor(p),ConfigurationRules.uuid(venueId),ConfigurationRules.uuid(roomId),d,false));}
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().header("Cache-Control","no-store").body(value);}
    @ExceptionHandler(RoomFailure.class) ResponseEntity<?> failure(RoomFailure e,Principal p){
        if(e.status==403)try{audit.record(p==null?null:UUID.fromString(p.getName()));}catch(DataAccessException unavailable){return error(503,"Service unavailable");}
        return error(e.status,e.getMessage());
    }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409,"Configuration conflict; reload and compare");}
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503,"Service unavailable");}
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class}) ResponseEntity<?> malformed(){return error(400,"Malformed request");}
    private ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message));}
}
