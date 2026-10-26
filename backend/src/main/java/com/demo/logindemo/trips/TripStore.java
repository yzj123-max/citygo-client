package com.demo.logindemo.trips;

import java.util.List;
import static com.demo.logindemo.trips.TripContracts.*;

public interface TripStore {
    void lockUser(long userId);
    Stored find(String tripId);
    Receipt receipt(long userId, String mutationId);
    void insert(long userId, Snapshot snapshot);
    boolean update(long userId, long baseVersion, Snapshot snapshot);
    void record(long userId, String mutationId, String hash, Snapshot snapshot);
    void activate(long userId, String tripId);
    Snapshot current(long userId);
    List<Summary> list(long userId);
}
