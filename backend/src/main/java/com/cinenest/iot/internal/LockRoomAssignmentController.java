package com.cinenest.iot.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="cinenest.iot.lock-room-assignment.enabled", havingValue="true")
@RequestMapping("/api/admin/venues/{venue}/rooms/{room}/lock-assignments")
final class LockRoomAssignmentController {
    private final LockRoomAssignmentService service;
    LockRoomAssignmentController(LockRoomAssignmentService service) { this.service=service; }
    private ResponseEntity<?> call(Principal principal, String venue, String room, String assignment,
                                  HttpServletRequest request, String operation, byte[] body, boolean invalid) {
        UUID actor=null; try { actor=UUID.fromString(principal.getName()); } catch (RuntimeException ignored) { }
        var params=request.getParameterMap(); boolean list=operation.equals("LIST");
        invalid=invalid || params.size()>(list?1:0) || params.keySet().stream().anyMatch(k -> !list || !k.equals("page"))
            || params.values().stream().anyMatch(v -> v.length!=1);
        var result=service.run(actor,venue,room,assignment,request.getParameter("page"),invalid,body,operation);
        return ResponseEntity.status(result.status()).header("Cache-Control","no-store").body(result.body());
    }
    @PostMapping ResponseEntity<?> create(Principal principal, @PathVariable String venue, @PathVariable String room, HttpServletRequest request) {
        byte[] body; boolean invalid=false;
        try {
            body=request.getInputStream().readNBytes(LockRoomAssignmentPolicy.MAX_BODY+1);
            String type=request.getContentType(); invalid=type==null || !type.split(";",2)[0].trim().equalsIgnoreCase("application/json");
        } catch (java.io.IOException failure) { body=new byte[0]; invalid=true; }
        return call(principal,venue,room,null,request,"CREATE",body,invalid);
    }
    @GetMapping ResponseEntity<?> list(Principal principal, @PathVariable String venue, @PathVariable String room, HttpServletRequest request) {
        return call(principal,venue,room,null,request,"LIST",null,false);
    }
    @GetMapping("/{assignment}") ResponseEntity<?> detail(Principal principal, @PathVariable String venue, @PathVariable String room,
            @PathVariable String assignment, HttpServletRequest request) {
        return call(principal,venue,room,assignment,request,"DETAIL",null,false);
    }
}
