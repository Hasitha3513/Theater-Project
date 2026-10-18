package com.cinenest.payments.internal;
import com.cinenest.booking.SyntheticBooking;import java.security.Principal;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.dao.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
@RestController
@ConditionalOnProperty(name="cinenest.bank.ledger.enabled",havingValue="true")
class LedgerController {
 private final LedgerService ledger;private final SyntheticBooking bookings;private final BankRepository bank;private final TransactionTemplate tx;
 LedgerController(LedgerService ledger,SyntheticBooking bookings,BankRepository bank,PlatformTransactionManager manager){this.ledger=ledger;this.bookings=bookings;this.bank=bank;tx=new TransactionTemplate(manager);}
 UUID actor(Principal p){if(p==null)throw new BankFailure(401,"Sign-in required");return BankService.id(p.getName());}
 ResponseEntity<?> ok(Object v){return ResponseEntity.ok().header("Cache-Control","no-store").body(v);}
 @PostMapping("/api/admin/venues/{v}/bank/credits") ResponseEntity<?> credit(Principal p,@PathVariable UUID v,@RequestBody Map<String,Object>b){return ok(ledger.command(actor(p),v,null,"CREDIT",b));}
 @GetMapping("/api/admin/venues/{v}/bank/credits") ResponseEntity<?> credits(Principal p,@PathVariable UUID v){return ok(ledger.read(actor(p),v,null,false));}
 @GetMapping("/api/admin/venues/{v}/bank/credits/{id}") ResponseEntity<?> credit(Principal p,@PathVariable UUID v,@PathVariable UUID id){return ok(ledger.read(actor(p),v,id,false));}
 @PostMapping("/api/admin/venues/{v}/bank/credits/{id}/matches") ResponseEntity<?> match(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(ledger.command(actor(p),v,id,"MATCH",b));}
 @PostMapping("/api/admin/venues/{v}/bank/requests/{id}/allocations") ResponseEntity<?> allocate(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(ledger.command(actor(p),v,id,"ALLOCATE",b));}
 @GetMapping("/api/admin/venues/{v}/bank/exceptions") ResponseEntity<?> exceptions(Principal p,@PathVariable UUID v){return ok(ledger.read(actor(p),v,null,true));}
 @GetMapping("/api/admin/venues/{v}/bank/exceptions/{id}") ResponseEntity<?> exception(Principal p,@PathVariable UUID v,@PathVariable UUID id){return ok(ledger.read(actor(p),v,id,true));}
 @PostMapping("/api/admin/venues/{v}/bank/exceptions/{id}/actions") ResponseEntity<?> action(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(ledger.command(actor(p),v,id,"ACTION",b));}
 @PostMapping("/api/admin/venues/{v}/bank/credits/{id}/corrections") ResponseEntity<?> correct(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(ledger.command(actor(p),v,id,"CORRECT",b));}
 @PostMapping("/api/admin/venues/{v}/bank/corrections/{id}/decisions") ResponseEntity<?> approve(Principal p,@PathVariable UUID v,@PathVariable UUID id,@RequestBody Map<String,Object>b){return ok(ledger.command(actor(p),v,id,"APPROVE",b));}
 @GetMapping("/api/bookings") ResponseEntity<?> bookings(Principal p,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size){return ok(tx.execute(s->{ledger.configured();var result=bookings.ownedHistory(actor(p),page,size);bank.audit(actor(p),null,"CUSTOMER_BOOKINGS_READ","OWNED_SYNTHETIC_ONLY");return result;}));}
 @ExceptionHandler(SecurityException.class) ResponseEntity<?> eligibility(){return error(403);}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> pagination(){return error(400);}
 @GetMapping("/api/bookings/{id}") ResponseEntity<?> booking(Principal p,@PathVariable UUID id){return ok(tx.execute(s->{ledger.configured();var result=bookings.read(actor(p),id).orElseThrow(()->new BankFailure(404,"Booking not found"));bank.audit(actor(p),null,"BOOKING_READ","SYNTHETIC_ONLY");return result;}));}
 @ExceptionHandler(BankFailure.class) ResponseEntity<?> failure(BankFailure f,Principal p){try{tx.executeWithoutResult(s->bank.audit(p==null?null:actor(p),null,"LEDGER_DENIED","DENIED"));}catch(DataAccessException e){return error(503);}return error(f.status);}
 @ExceptionHandler(com.cinenest.booking.BankRequestHolds.Rejected.class) ResponseEntity<?> rejected(){return error(409);}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return error(409);}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<?> unavailable(){return error(503);}
 @ExceptionHandler({ArithmeticException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return error(400);}
 ResponseEntity<?> error(int status){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message","Synthetic finance unavailable, changed or denied; refresh before retrying","synthetic",true,"paymentVerified",false,"bookingConfirmed",false));}
}
