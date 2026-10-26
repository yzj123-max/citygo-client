package com.demo.logindemo.trips;

import com.demo.logindemo.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import static com.demo.logindemo.trips.TripContracts.*;

@Service
public class TripSyncService {
    private final TripStore store;
    private final UserMapper users;
    private final ObjectMapper json;
    public TripSyncService(TripStore store, UserMapper users, ObjectMapper json) {
        this.store = store; this.users = users; this.json = json;
    }
    private long owner(Long id) {
        if (id == null || id <= 0 || users.selectById(id) == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录");
        return id;
    }
    private String uuid(String value) {
        try { if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); return value; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的行程或请求编号"); }
    }
    private Snapshot owned(long owner, String id) {
        var row = store.find(uuid(id));
        if (row == null || row.userId() != owner) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "行程不存在");
        return row.snapshot();
    }
    public List<Summary> list(Long user) { return store.list(owner(user)); }
    public Snapshot current(Long user) { return store.current(owner(user)); }
    public Snapshot get(Long user, String id) { return owned(owner(user), id); }
    @Transactional
    public Snapshot activate(Long user, String id) {
        long owner = owner(user); store.lockUser(owner);
        var s = owned(owner, id); store.activate(owner, id); return s;
    }
    @Transactional
    public Snapshot save(Long user, String id, Write req) {
        long owner = owner(user); uuid(id);
        if (req == null || req.baseVersion() < 0 || req.baseVersion() == Long.MAX_VALUE || req.title() == null ||
                req.title().isBlank() || req.title().length() > 120 || req.document() == null || !req.document().isObject())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行程请求不完整");
        uuid(req.mutationId());
        var messages = req.document().get("messages");
        var plan = req.document().get("plan");
        if (messages == null || !messages.isArray() || messages.size() > 2000 || plan == null ||
                (!plan.isNull() && (!plan.isObject() || !plan.path("dailyPlans").isArray() || !plan.path("allPlaceList").isArray())) ||
                req.document().toString().getBytes(StandardCharsets.UTF_8).length > 2_000_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行程或聊天格式错误，或内容过大");
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(req))); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取行程内容"); }
        // 用户级数据库锁串行化写入；版本更新、幂等记录、当前行程指针在一个事务内提交。
        store.lockUser(owner);
        var row = store.find(id);
        if (row != null && row.userId() != owner) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "行程不存在");
        var receipt = store.receipt(owner, req.mutationId());
        if (receipt != null) {
            if (!receipt.requestHash().equals(hash) || !receipt.snapshot().tripId().equals(id))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求编号重复且内容不同");
            return receipt.snapshot();
        }
        long currentVersion = row == null ? 0 : row.snapshot().version();
        if (currentVersion != req.baseVersion()) {
            if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "行程不存在");
            throw new Conflict(row.snapshot());
        }
        var next = new Snapshot(id, currentVersion + 1, req.title().trim(), req.document(), System.currentTimeMillis());
        if (row == null) store.insert(owner, next);
        else if (!store.update(owner, currentVersion, next)) throw new Conflict(owned(owner, id));
        store.record(owner, req.mutationId(), hash, next);
        if (req.makeCurrent()) store.activate(owner, id);
        return next;
    }
}
