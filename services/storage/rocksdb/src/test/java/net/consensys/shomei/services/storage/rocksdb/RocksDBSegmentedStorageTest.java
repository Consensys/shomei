package net.consensys.shomei.services.storage.rocksdb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.consensys.shomei.services.storage.rocksdb.RocksDBSegmentIdentifier.SegmentNames.DEFAULT;
import static net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration.DEFAULT_ROCKSDB_CONFIG;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.shomei.config.ShomeiConfig;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfiguration;
import net.consensys.shomei.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.OptimisticTransactionDB;
import services.storage.KeyValueStorage;

public class RocksDBSegmentedStorageTest {

  @TempDir Path tempDir;
  OptimisticTransactionDB db;

  @BeforeAll
  public static void setup() {
  }

  @Test
  public void shouldCreateSegmentedStorage() throws IOException {

    RocksDBKeyValueStorageFactory factory = new RocksDBKeyValueStorageFactory(DEFAULT_ROCKSDB_CONFIG);
    RocksDBConfiguration rocksDBConfiguration = RocksDBConfigurationBuilder.from(DEFAULT_ROCKSDB_CONFIG)
        .databaseDir(tempDir)
        .build();

    KeyValueStorage defaultSegment = factory
        .create(DEFAULT.getSegmentIdentifier(),
            new ShomeiConfig(() -> rocksDBConfiguration.getDatabaseDir()));
    defaultSegment.startTransaction()
        .put("key1".getBytes(UTF_8), "value1".getBytes(UTF_8))
        .commit();

    assertThat(defaultSegment.get("key1".getBytes(UTF_8))).contains("value1".getBytes(UTF_8));


  }
}
