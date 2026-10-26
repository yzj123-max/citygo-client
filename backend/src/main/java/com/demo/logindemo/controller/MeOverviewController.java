package com.demo.logindemo.controller;
import com.demo.logindemo.dto.MeOverviewResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.MeOverviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/me")
public class MeOverviewController {
 private final MeOverviewService service;
 public MeOverviewController(MeOverviewService service){this.service=service;}
 @GetMapping("/overview")
 public ResponseEntity<Result<MeOverviewResp>> overview(@RequestAttribute("userId") Long userId){
  try{return ResponseEntity.ok(Result.success(service.getOverview(userId)));}
  catch(ResponseStatusException e){Result<MeOverviewResp> result=Result.fail(e.getReason());result.setCode(e.getStatusCode().value());return ResponseEntity.status(e.getStatusCode()).body(result);}
 }
}
