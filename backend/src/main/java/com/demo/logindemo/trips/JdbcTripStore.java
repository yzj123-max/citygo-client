package com.demo.logindemo.trips;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import static com.demo.logindemo.trips.TripContracts.*;

@Repository
public class JdbcTripStore implements TripStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcTripStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    private Snapshot snapshot(ResultSet r) throws SQLException {
        try {
            return new Snapshot(r.getString("trip_id"), r.getLong("version"), r.getString("title"),
                    json.readTree(r.getString("document_json")), r.getLong("updated_at"));
        } catch (java.io.IOException e) { throw new SQLException("Stored trip is invalid", e); }
    }
    public void lockUser(long owner) {
        jdbc.update("INSERT IGNORE INTO citygo_trip_current(user_id,trip_id) VALUES(?,NULL)", owner);
        jdbc.queryForObject("SELECT user_id FROM citygo_trip_current WHERE user_id=? FOR UPDATE", Long.class, owner);
    }
    public Stored find(String id) {
        var rows = jdbc.query("SELECT * FROM citygo_trip WHERE trip_id=?", (r,n) -> new Stored(r.getLong("user_id"), snapshot(r)), id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public Receipt receipt(long owner, String mutation) {
        var rows = jdbc.query("SELECT * FROM citygo_trip_revision WHERE user_id=? AND mutation_id=?",
                (r,n) -> new Receipt(r.getString("request_hash"), snapshot(r)), owner, mutation);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public void insert(long owner, Snapshot s) {
        jdbc.update("INSERT INTO citygo_trip(trip_id,user_id,version,title,document_json,updated_at) VALUES(?,?,?,?,?,?)",
                s.tripId(), owner, s.version(), s.title(), s.document().toString(), s.updatedAt());
    }
    public boolean update(long owner, long base, Snapshot s) {
        return jdbc.update("UPDATE citygo_trip SET version=?,title=?,document_json=?,updated_at=? WHERE trip_id=? AND user_id=? AND version=?",
                s.version(), s.title(), s.document().toString(), s.updatedAt(), s.tripId(), owner, base) == 1;
    }
    public void record(long owner, String mutation, String hash, Snapshot s) {
        jdbc.update("INSERT INTO citygo_trip_revision(trip_id,version,user_id,mutation_id,request_hash,title,document_json,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                s.tripId(), s.version(), owner, mutation, hash, s.title(), s.document().toString(), s.updatedAt());
    }
    public void activate(long owner, String id) {
        jdbc.update("UPDATE citygo_trip_current SET trip_id=? WHERE user_id=?", id, owner);
    }
    public Snapshot current(long owner) {
        var rows = jdbc.query("SELECT t.* FROM citygo_trip t JOIN citygo_trip_current c ON c.trip_id=t.trip_id AND c.user_id=t.user_id WHERE c.user_id=?",
                (r,n) -> snapshot(r), owner);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public List<Summary> list(long owner) {
        return jdbc.query("SELECT trip_id,version,title,updated_at FROM citygo_trip WHERE user_id=? ORDER BY updated_at DESC LIMIT 200",
                (r,n) -> new Summary(r.getString(1), r.getLong(2), r.getString(3), r.getLong(4)), owner);
    }
}
