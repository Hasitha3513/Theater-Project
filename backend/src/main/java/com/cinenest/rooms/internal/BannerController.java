package com.cinenest.rooms.internal;
import java.security.Principal;import java.util.*;import jakarta.servlet.http.HttpServletRequest;import org.springframework.dao.*;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;import org.springframework.http.converter.HttpMessageNotReadableException;
@RestController
class BannerController {
 final BannerService service;final BannerRepository store;BannerController(BannerService service,BannerRepository store){this.service=service;this.store=store;}
 UUID actor(Principal p){if(p==null)throw new RoomFailure(401,"Sign-in required");try{return UUID.fromString(p.getName());}catch(RuntimeException e){throw new RoomFailure(403,"Access denied");}}
 ResponseEntity<?> ok(Object body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
 @GetMapping("/api/catalog/banners") Object publicRead(@RequestParam(required=false) UUID venueId){return ok(service.publicBanners(venueId));}
 @GetMapping("/api/admin/venues/{venue}/banner-assets") Object assets(Principal p,@PathVariable UUID venue){return ok(service.catalogue(actor(p),venue));}
 @GetMapping("/api/admin/venues/{venue}/banners") Object list(Principal p,@PathVariable UUID venue){return ok(service.list(actor(p),venue));}
 @PostMapping("/api/admin/venues/{venue}/banners") Object create(Principal p,@PathVariable UUID venue,@RequestBody Map<String,Object> body){return ok(service.mutate(actor(p),venue,null,"CREATE",body));}
 @GetMapping({"/api/admin/venues/{venue}/banners/{id}","/api/admin/venues/{venue}/banners/{id}/preview"}) Object detail(Principal p,@PathVariable UUID venue,@PathVariable UUID id){return ok(service.read(actor(p),venue,id,false));}
 @GetMapping("/api/admin/venues/{venue}/banners/{id}/revisions") Object history(Principal p,@PathVariable UUID venue,@PathVariable UUID id){return ok(service.read(actor(p),venue,id,true));}
 @PutMapping("/api/admin/venues/{venue}/banners/{id}") Object edit(Principal p,@PathVariable UUID venue,@PathVariable UUID id,@RequestBody Map<String,Object> body){return ok(service.mutate(actor(p),venue,id,"EDIT",body));}
 @PostMapping("/api/admin/venues/{venue}/banners/{id}/publications") Object publish(Principal p,@PathVariable UUID venue,@PathVariable UUID id,@RequestBody Map<String,Object> body){return ok(service.mutate(actor(p),venue,id,"PUBLISH",body));}
 @PostMapping("/api/admin/venues/{venue}/banners/{id}/unpublish") Object unpublish(Principal p,@PathVariable UUID venue,@PathVariable UUID id,@RequestBody Map<String,Object> body){return ok(service.mutate(actor(p),venue,id,"UNPUBLISH",body));}
 @ExceptionHandler(RoomFailure.class) ResponseEntity<?> failure(RoomFailure e,Principal p,HttpServletRequest request){if(e.status==403&&p!=null){try{String[] parts=request.getRequestURI().split("/");String action=request.getMethod().equals("GET")?"READ":request.getMethod().equals("PUT")?"EDIT":request.getRequestURI().endsWith("/publications")?"PUBLISH":request.getRequestURI().endsWith("/unpublish")?"UNPUBLISH":"CREATE";store.audit(actor(p),UUID.fromString(parts[4]),parts.length>6?UUID.fromString(parts[6]):null,action,"DENY",0,0);}catch(DataAccessException unavailable){return error(503,"Advertising unavailable");}}return error(e.status,e.getMessage());}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> integrity(){return error(409,"Banner integrity conflict; reload and compare");}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503,"Advertising unavailable");}
 @ExceptionHandler({MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class}) ResponseEntity<?> malformed(){return error(400,"Invalid banner request");}
 ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",message));}
}
