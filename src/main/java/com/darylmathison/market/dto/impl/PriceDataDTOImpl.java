package com.darylmathison.market.dto.impl;


import com.darylmathison.market.dto.PriceDataDTO;
import com.darylmathison.market.model.StorageStockBar;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import net.jacobpeterson.alpaca.AlpacaAPI;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.common.historical.bar.enums.BarTimePeriod;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.MultiStockBarsResponse;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.enums.BarAdjustment;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.enums.BarFeed;
import org.springframework.stereotype.Service;

@Service
public class PriceDataDTOImpl implements PriceDataDTO {

  private final AlpacaAPI alpacaAPI;

  public PriceDataDTOImpl(AlpacaAPI alpacaAPI) {
    this.alpacaAPI = alpacaAPI;
  }

  @Override
  public List<StorageStockBar> getPriceData(List<String> symbols, LocalDate start, LocalDate end)
      throws Exception {
    List<StorageStockBar> allData = new ArrayList<>();

    ZonedDateTime requestStart = toZoneDateTime(start);
    ZonedDateTime requestEnd = toZoneDateTime(end);
    boolean callAgain = true;
    MultiStockBarsResponse barsResponse = alpacaAPI.stockMarketData()
        .getBars(symbols, requestStart, requestEnd, null, null, 15, BarTimePeriod.MINUTE,
            BarAdjustment.RAW, BarFeed.IEX);
    while (callAgain) {
      barsResponse.getBars().forEach((symbol, bars) -> bars.forEach(bar -> allData.add(
          StorageStockBar.builder().symbol(symbol).timestamp(bar.getTimestamp()).open(bar.getOpen())
              .close(bar.getClose()).high(bar.getHigh()).low(bar.getLow())
              .volume(bar.getTradeCount()).build())));
      barsResponse = alpacaAPI.stockMarketData()
          .getBars(symbols, requestStart, requestEnd, null, barsResponse.getNextPageToken(), 15,
              BarTimePeriod.MINUTE, BarAdjustment.RAW, BarFeed.IEX);
      callAgain = barsResponse.getNextPageToken() != null;
    }

    return allData;
  }

  private ZonedDateTime toZoneDateTime(LocalDate localDate) {
    return localDate.atStartOfDay(ZoneId.of("America/New_York"));
  }
}
