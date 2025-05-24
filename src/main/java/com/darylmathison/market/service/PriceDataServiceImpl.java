package com.darylmathison.market.service;


import com.darylmathison.market.model.StorageStockBar;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;

@Service
public class PriceDataServiceImpl implements PriceDataService {

  @Value("${env.DATA_BUCKET}")
  private String dataBucketName;

  @Value("${env.SYMBOLS}")
  private String symbolsFileKey;

  private final AlpacaAPI alpacaAPI;

  private final ApplicationContext context;

  public PriceDataServiceImpl(AlpacaAPI alpacaAPI, ApplicationContext context) {
    this.alpacaAPI = alpacaAPI;
    this.context = context;
  }

  @Override
  public List<StorageStockBar> getPriceData() throws Exception {
    List<StorageStockBar> allData = new ArrayList<>();
    List<String> symbols;
    try (S3Client s3Client = context.getBean(S3Client.class)) {
      symbols = fetchSymbolsFromS3(s3Client);
    }
    ZonedDateTime today = LocalDate.now().atStartOfDay(ZoneId.systemDefault());
    ZonedDateTime oneWeekAgo = today.minusDays(7);

    boolean callAgain = true;
    MultiStockBarsResponse barsResponse = alpacaAPI.stockMarketData()
        .getBars(symbols, oneWeekAgo, today, null, null, 15, BarTimePeriod.MINUTE,
            BarAdjustment.RAW, BarFeed.IEX);
    while (callAgain) {
      barsResponse.getBars().forEach((symbol, bars) -> bars.forEach(bar -> allData.add(
          StorageStockBar.builder().symbol(symbol).timestamp(bar.getTimestamp()).open(bar.getOpen())
              .close(bar.getClose()).high(bar.getHigh()).low(bar.getLow())
              .volume(bar.getTradeCount()).build())));
      barsResponse = alpacaAPI.stockMarketData()
          .getBars(symbols, oneWeekAgo, today, null, barsResponse.getNextPageToken(), 15,
              BarTimePeriod.MINUTE, BarAdjustment.RAW, BarFeed.IEX);
      callAgain = barsResponse.getNextPageToken() != null;
    }

    return allData;
  }


  /**
   * Fetch a list of symbols from the file stored in the S3 bucket.
   */
   List<String> fetchSymbolsFromS3(S3Client s3Client) throws Exception {
    try (InputStream objectInputStream = s3Client.getObject(request ->
        request.bucket(dataBucketName)
            .key(symbolsFileKey));
        BufferedReader reader = new BufferedReader(new InputStreamReader(objectInputStream))) {
      return reader.lines().toList();
    }
  }
}
