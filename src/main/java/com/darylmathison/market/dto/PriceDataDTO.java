package com.darylmathison.market.dto;

import com.darylmathison.market.model.StorageStockBar;
import java.time.LocalDate;
import java.util.List;

public interface PriceDataDTO {

  List<StorageStockBar> getPriceData(List<String> symbols, LocalDate start, LocalDate end) throws Exception;
}
