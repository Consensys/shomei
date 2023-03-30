package net.consensys.shomei.services.storage.rocksdb;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import services.storage.KeyValueStorage;
import services.storage.KeyValueStorageTransaction;
import services.storage.StorageException;

public class RocksDBKeyValueStorage implements KeyValueStorage {

  private final KeyValueStorage delegate;

  RocksDBKeyValueStorage(final KeyValueStorage delegate) {
    this.delegate = delegate;
  }

  @Override
  public void clear() throws StorageException {
    delegate.clear();
  }

  @Override
  public boolean containsKey(final byte[] key) throws StorageException {
    return delegate.containsKey(key);
  }

  @Override
  public Optional<byte[]> get(final byte[] key) throws StorageException {
    return delegate.get(key);
  }

  @Override
  public Stream<KeyValuePair> stream() throws StorageException {
    return delegate.stream();
  }

  @Override
  public Stream<byte[]> streamKeys() throws StorageException {
    return delegate.streamKeys();
  }

  @Override
  public boolean tryDelete(final byte[] key) throws StorageException {
    return delegate.tryDelete(key);
  }

  @Override
  public Set<byte[]> getAllKeysThat(final Predicate<byte[]> returnCondition) {
    return delegate.getAllKeysThat(returnCondition);
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(final Predicate<byte[]> returnCondition) {
    return delegate.getAllValuesFromKeysThat(returnCondition);
  }

  @Override
  public KeyValueStorageTransaction startTransaction() throws StorageException {
    return delegate.startTransaction();
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
