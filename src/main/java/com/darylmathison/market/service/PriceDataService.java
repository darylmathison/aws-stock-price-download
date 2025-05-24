package com.darylmathison.market.service;

import com.darylmathison.market.model.StorageStockBar;
import java.util.List;

public interface PriceDataService {

  List<StorageStockBar> getPriceData() throws Exception;
}
