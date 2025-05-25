package com.darylmathison.market.service;

import java.util.List;

public interface S3Service {
  List<String> fetchList(String bucket, String key);

  @SuppressWarnings( "SameParameterValue")
  byte[] getObject(String bucket, String key);
  void putObject(String bucket, String key, byte[] data);
}
