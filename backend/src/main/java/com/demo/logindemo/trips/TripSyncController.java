package com.demo.logindemo.trips;

import java.util.List;
import java.util.Map;
import com.demo.logindemo.entity.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import static com.demo.logindemo.trips.TripContracts.*;

@RestController
@RequestMapping("/api/me/trips")
public class TripSyncController {
    private final TripSyncService service;
    public TripSyncController(TripSyncService service) { this.service = service; }
    @GetMapping public Result<List<Summary>> list(@RequestAttribute("userId") Long owner) { return Result.success(service.list(owner)); }
    @GetMapping("/current") public Result<Snapshot> current(@RequestAttribute("userId") Long owner) { return Result.success(service.current(owner)); }
    @GetMapping("/{id}") public Result<Snapshot> get(@RequestAttribute("userId") Long owner, @PathVariable String id) { return Result.success(service.get(owner, id)); }
    @PutMapping("/{id}") public Result<Snapshot> save(@RequestAttribute("userId") Long owner, @PathVariable String id, @RequestBody Write req) { return Result.success(service.save(owner,id,req)); }
    @PostMapping("/{id}/activate") public Result<Snapshot> activate(@RequestAttribute("userId") Long owner, @PathVariable String id) { return Result.success(service.activate(owner,id)); }
    @ExceptionHandler(Conflict.class) public ResponseEntity<?> conflict(Conflict e) {
        return ResponseEntity.status(409).body(Map.of("code",409,"msg",e.getMessage(),"data",e.getRemote()));
    }
}
