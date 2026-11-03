package org.evd.game.runtime.Db.snapshot;

import io.protostuff.Schema;
import io.protostuff.Tag;
import io.protostuff.runtime.RuntimeSchema;
import org.evd.game.runtime.Db.serialize.ProtostuffSerializer;
import org.evd.game.runtime.Db.table.TRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotManagerConcurrencyTest {
    private static final Schema<SnapshotEntity> ENTITY_SCHEMA =
            RuntimeSchema.getSchema(SnapshotEntity.class);

    @TempDir
    Path tempDirectory;

    @Test
    void multipleServicesCanWriteSameNodeMapConcurrently() throws Exception {
        String previousSnapshotDirectory = System.getProperty("evd.db.snapshot.dir");
        System.setProperty("evd.db.snapshot.dir", tempDirectory.toString());

        int serviceCount = 8;
        int snapshotsPerService = 64;
        Set<String> serviceIds = new HashSet<>();
        for (int index = 0; index < serviceCount; index++) {
            serviceIds.add("Service" + index);
        }

        try (SnapshotManager manager = SnapshotManager.open(
                1, 2, 3,
                (long) serviceCount * snapshotsPerService,
                1024,
                Set.of("sample_table"))) {
            try (ExecutorService executor = Executors.newFixedThreadPool(serviceCount)) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> writes = new ArrayList<>();
                int serviceIndex = 0;
                for (String serviceId : serviceIds) {
                    int currentServiceIndex = serviceIndex++;
                    writes.add(executor.submit(() -> {
                        try (ProtostuffSerializer serializer = new ProtostuffSerializer()) {
                            start.await();
                            for (int objectId = 0; objectId < snapshotsPerService; objectId++) {
                                SnapshotEntity entity = new SnapshotEntity(serviceId + "-" + objectId);
                                SnapshotValue value = SnapshotValue.forSizing(
                                        serializer,
                                        new Snapshot(TRecord.ADD, 1L, 0L),
                                        entity,
                                        ENTITY_SCHEMA);
                                manager.put(SnapshotKey.of(
                                        "sample_table",
                                        currentServiceIndex * snapshotsPerService + objectId), value);
                            }
                        }
                        return null;
                    }));
                }

                start.countDown();
                for (Future<?> write : writes) {
                    write.get();
                }

                assertEquals((long) serviceCount * snapshotsPerService, manager.size());
                Set<SnapshotKey> keys = manager.keys();
                assertEquals((long) serviceCount * snapshotsPerService, keys.size());
                assertTrue(keys.stream().allMatch(key -> "sample_table".equals(key.tableName())));
            }
        } finally {
            if (previousSnapshotDirectory == null) {
                System.clearProperty("evd.db.snapshot.dir");
            } else {
                System.setProperty("evd.db.snapshot.dir", previousSnapshotDirectory);
            }
        }
    }

    private static final class SnapshotEntity {
        @Tag(1)
        private String value;

        public SnapshotEntity() {
        }

        private SnapshotEntity(String value) {
            this.value = value;
        }
    }
}
