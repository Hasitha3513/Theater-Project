package com.cinenest.booking.internal;

import java.security.Principal;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="cinenest.booking.staff-read.enabled",havingValue="true")
class StaffBookingReadController {
 private final StaffBookingReadService service;
 StaffBookingReadController(StaffBookingReadService service){this.service=service;}
 @GetMapping("/api/admin/venues/{venue}/bookings")
 ResponseEntity<?> list(Principal principal,@PathVariable String venue,@RequestParam MultiValueMap<String,String> params){return read(principal,venue,null,params);}
 @GetMapping("/api/admin/venues/{venue}/bookings/{booking}")
 ResponseEntity<?> detail(Principal principal,@PathVariable String venue,@PathVariable String booking,@RequestParam MultiValueMap<String,String> params){return read(principal,venue,booking,params);}
 private ResponseEntity<?> read(Principal principal,String venue,String booking,MultiValueMap<String,String> params){
  UUID correlation=UUID.randomUUID();
  try{Object result=service.read(principal==null?null:UUID.fromString(principal.getName()),venue,booking,params,correlation);return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Correlation-ID",correlation.toString()).body(result);}
  catch(StaffBookingReadRules.Failure failure){String message=switch(failure.status){case 400->"Invalid booking reference or filters";case 401->"Current session required";case 403->"Booking read denied";case 404->"Booking not found";default->"Synthetic booking reads unavailable";};return ResponseEntity.status(failure.status).header("Cache-Control","private, no-store").header("X-Correlation-ID",correlation.toString()).body(Map.of("message",message,"synthetic",true,"realBooking",false));}
 }
}
