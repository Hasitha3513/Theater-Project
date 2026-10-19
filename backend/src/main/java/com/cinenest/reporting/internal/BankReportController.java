package com.cinenest.reporting.internal;
import com.cinenest.payments.BankBalanceReport;import com.cinenest.rooms.VenueReportingClock;import com.cinenest.identity.ScopedAuthorization;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController
class BankReportController {
 private final BankBalanceReport reports;private final VenueReportingClock clocks;private final ScopedAuthorization identity;private final boolean enabled;private final int maximumDays;
 BankReportController(BankBalanceReport reports,VenueReportingClock clocks,ScopedAuthorization identity,@Value("${cinenest.reporting.synthetic-bank.enabled:false}")boolean enabled,@Value("${cinenest.reporting.synthetic-bank.maximum-days:0}")int maximumDays){this.reports=reports;this.clocks=clocks;this.identity=identity;this.enabled=enabled;this.maximumDays=maximumDays;}
 private static final class Failure extends RuntimeException {final int status;Failure(int status,String text){super(text);this.status=status;}}
 private void permit(UUID actor,UUID venue){if(!identity.permits(actor,venue,"report.bank.synthetic.read",false)){reports.denied(actor,venue);throw new Failure(403,"Access denied");}}
 @GetMapping("/api/admin/venues/{venue}/reports/synthetic-bank-balances")
 ResponseEntity<?> read(Principal principal,@PathVariable UUID venue,@RequestParam Map<String,String> params){
  if(principal==null)throw new Failure(401,"Sign-in required");UUID actor=UUID.fromString(principal.getName());permit(actor,venue);
  if(!enabled||maximumDays<=0)throw new Failure(503,"Synthetic reporting is not configured");
  if(!Set.of("currency","from","to","page","size").containsAll(params.keySet()))throw new Failure(400,"Unsupported filter");
  String currency=params.getOrDefault("currency","");if(!currency.matches("[A-Z]{3}"))throw new Failure(400,"Explicit currency required");
  String timezone=clocks.timezone(venue).orElseThrow(()->new Failure(404,"Venue not found"));ZoneId zone=ZoneId.of(timezone);
  Instant from=null,to=null;String fs=params.getOrDefault("from",""),ts=params.getOrDefault("to","");
  if(!fs.isEmpty()||!ts.isEmpty()){if(fs.isEmpty()||ts.isEmpty())throw new Failure(400,"Both date boundaries required");LocalDate f=LocalDate.parse(fs),t=LocalDate.parse(ts);long days=ChronoUnit.DAYS.between(f,t);if(days<1||days>maximumDays)throw new Failure(400,"Invalid or excessive date range");if(zone.getRules().getValidOffsets(f.atStartOfDay()).size()!=1||zone.getRules().getValidOffsets(t.atStartOfDay()).size()!=1)throw new Failure(400,"Ambiguous or nonexistent local boundary");from=f.atStartOfDay(zone).toInstant();to=t.atStartOfDay(zone).toInstant();}
  int page=Integer.parseInt(params.getOrDefault("page","0")),size=Integer.parseInt(params.getOrDefault("size","25"));if(page<0||page>100000||size<1||size>100)throw new Failure(400,"Invalid page");
  var result=reports.snapshot(actor,new BankBalanceReport.Query(venue,currency,from,to,page,size));permit(actor,venue);
  var out=new LinkedHashMap<String,Object>();out.put("synthetic",true);out.put("realFinancialReporting",false);out.put("venueId",venue);out.put("currency",currency);out.put("timezone",timezone);out.put("filters",Map.copyOf(params));out.put("page",page);out.put("size",size);out.put("snapshot",result);out.put("snapshotPolicy","CURRENT_REQUEST_ONLY");return ResponseEntity.ok().header("Cache-Control","no-store").body(out);
 }
 @ExceptionHandler(Failure.class) ResponseEntity<?> failure(Failure f){return error(f.status,f.getMessage());}
 @ExceptionHandler({IllegalArgumentException.class,DateTimeException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return error(400,"Invalid report filters");}
 @ExceptionHandler(org.springframework.dao.DataAccessException.class) ResponseEntity<?> unavailable(){return error(503,"Synthetic report unavailable");}
 @ExceptionHandler(RuntimeException.class) ResponseEntity<?> invariant(){return error(422,"Financial invariant unavailable; no totals returned");}
 private ResponseEntity<?> error(int status,String text){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("message",text));}
}
