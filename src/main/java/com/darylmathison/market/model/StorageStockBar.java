package com.darylmathison.market.model;

import java.time.ZonedDateTime;

@lombok.Data
@lombok.Builder
public class StorageStockBar {
  private String symbol;
  private ZonedDateTime timestamp;
  private double open;
  private double high;
  private double low;
  private double close;
  private double volume;
}
