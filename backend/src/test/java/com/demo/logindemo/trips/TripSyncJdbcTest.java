package com.demo.logindemo.trips;

import com.demo.logindemo.entity.User;
import com.demo.logindemo.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.demo.logindemo.trips.TripContracts.*;

/** 显式启用；只操作本连接的 TEMPORARY TABLE，关闭连接后自动消失，绝不写业务表。 */
@EnabledIfSystemProperty(named="citygo.tripJdbcTest", matches="true")
class TripSyncJdbcTest {
    @Test void mysqlTransactionsOwnershipConflictAndRetry() throws Exception {
        var p = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) { p.load(reader); }
        try (var connection = DriverManager.getConnection(p.getProperty("spring.datasource.url"),
                p.getProperty("spring.datasource.username"),p.getProperty("spring.datasource.password"))) {
            var ds = new SingleConnectionDataSource(connection,true);
            String ddl;
            try (var in = getClass().getResourceAsStream("/db/trip-sync.sql")) {
                ddl = new String(in.readAllBytes(),StandardCharsets.UTF_8)
                        .replace("CREATE TABLE IF NOT EXISTS", "CREATE TEMPORARY TABLE");
            }
            new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))).execute(ds);
            var jdbc = new JdbcTemplate(ds);
            var json = new ObjectMapper();
            var users = mock(UserMapper.class); var user = new User(); user.setId(9L);
            when(users.selectById(9L)).thenReturn(user); when(users.selectById(10L)).thenReturn(user);
            var service = new TripSyncService(new JdbcTripStore(jdbc,json),users,json);
            var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            String id = UUID.randomUUID().toString();
            var doc = json.readTree("{\"plan\":null,\"messages\":[],\"options\":null}");
            var request = new Write(0,UUID.randomUUID().toString(),"事务测试",doc,true);
            var first = tx.execute(s -> service.save(9L,id,request));
            assertEquals(1,first.version()); assertEquals(first,service.current(9L));
            assertNull(service.current(10L)); assertTrue(service.list(10L).isEmpty());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.get(10L,id));
            assertEquals(first,tx.execute(s -> service.save(9L,id,request)));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM citygo_trip_revision",Integer.class));
            var second = tx.execute(s -> service.save(9L,id,new Write(1,UUID.randomUUID().toString(),"第二版",doc,true)));
            assertEquals(2,second.version());
            assertThrows(Conflict.class,()->tx.execute(s -> service.save(9L,id,
                    new Write(1,UUID.randomUUID().toString(),"旧设备",doc,true))));
            assertEquals(second,service.get(9L,id));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM citygo_trip_revision",Integer.class));
        }
    }
}
