package net.consensys.shomei.config;

import java.nio.file.Path;
import java.util.function.Supplier;

public class ShomeiConfig {
  private final Supplier<Path> storagePath;

  public ShomeiConfig(Supplier<Path> storagePath){
    this.storagePath = storagePath;
  }
  public Path getStoragePath(){
    return storagePath.get();
  };

}
