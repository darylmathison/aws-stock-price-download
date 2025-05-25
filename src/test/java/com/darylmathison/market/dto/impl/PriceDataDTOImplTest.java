package com.darylmathison.market.dto.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darylmathison.market.model.StorageStockBar;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.jacobpeterson.alpaca.AlpacaAPI;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.common.historical.bar.enums.BarTimePeriod;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.MultiStockBarsResponse;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.StockBar;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.enums.BarAdjustment;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.enums.BarFeed;
import net.jacobpeterson.alpaca.rest.endpoint.marketdata.stock.StockMarketDataEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceDataDTOImplTest {

  @Mock
  private AlpacaAPI alpacaAPI;

  @Mock
  private StockMarketDataEndpoint stockMarketDataEndpoint;

  private PriceDataDTOImpl priceDataDTO;

  @BeforeEach
  void setUp() {
    priceDataDTO = new PriceDataDTOImpl(alpacaAPI);
    // Set required fields using reflection
    setField(priceDataDTO, "dataBucketName", "test-bucket");
    setField(priceDataDTO, "symbolsFileKey", "symbols.txt");
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      var field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception ignored) {
    }
  }

  @Test
  void getPriceData_shouldReturnStockBarList() throws Exception {
    // Arrange
    List<String> symbols = List.of("AAPL", "GOOG");
    LocalDate startDate = LocalDate.of(2023, 1, 1);
    LocalDate endDate = LocalDate.of(2023, 1, 3);
    ZonedDateTime startZoned = startDate.atStartOfDay(ZoneId.of("America/New_York"));
    ZonedDateTime endZoned = endDate.atStartOfDay(ZoneId.of("America/New_York"));

    // Mock StockBar
    StockBar appleBar = mock(StockBar.class);
    when(appleBar.getTimestamp()).thenReturn(startZoned);
    when(appleBar.getOpen()).thenReturn(150.0);
    when(appleBar.getClose()).thenReturn(155.0);
    when(appleBar.getHigh()).thenReturn(157.0);
    when(appleBar.getLow()).thenReturn(149.0);
    when(appleBar.getTradeCount()).thenReturn(1000L);

    StockBar googleBar = mock(StockBar.class);
    when(googleBar.getTimestamp()).thenReturn(startZoned);
    when(googleBar.getOpen()).thenReturn(2500.0);
    when(googleBar.getClose()).thenReturn(2550.0);
    when(googleBar.getHigh()).thenReturn(2560.0);
    when(googleBar.getLow()).thenReturn(2490.0);
    when(googleBar.getTradeCount()).thenReturn(500L);

    // Setup first response with pagination token
    HashMap<String, ArrayList<StockBar>> barsMap1 = new HashMap<>();
    barsMap1.put("AAPL", new ArrayList<>(List.of(appleBar)));
    barsMap1.put("GOOG", new ArrayList<>(List.of(googleBar)));

    MultiStockBarsResponse response1 = mock(MultiStockBarsResponse.class);
    when(response1.getBars()).thenReturn(barsMap1);
    when(response1.getNextPageToken()).thenReturn("nextPageToken");

    // Setup second response with no next page token
    HashMap<String, ArrayList<StockBar>> barsMap2 = new HashMap<>();
    barsMap2.put("AAPL", new ArrayList<>(List.of(appleBar)));

    MultiStockBarsResponse response2 = mock(MultiStockBarsResponse.class);
    when(response2.getNextPageToken()).thenReturn(null);

    // Mock API calls
    when(alpacaAPI.stockMarketData()).thenReturn(stockMarketDataEndpoint);

    // First call without page token
    when(stockMarketDataEndpoint.getBars(eq(symbols), eq(startZoned), eq(endZoned),
        isNull(), isNull(), eq(15), eq(BarTimePeriod.MINUTE),
        eq(BarAdjustment.RAW), eq(BarFeed.IEX)))
        .thenReturn(response1);

    // Second call with page token
    when(stockMarketDataEndpoint.getBars(eq(symbols), eq(startZoned), eq(endZoned),
        isNull(), eq("nextPageToken"), eq(15), eq(BarTimePeriod.MINUTE),
        eq(BarAdjustment.RAW), eq(BarFeed.IEX)))
        .thenReturn(response2);

    // Act
    List<StorageStockBar> result = priceDataDTO.getPriceData(symbols, startDate, endDate);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());

    // Verify AAPL data
    List<StorageStockBar> appleData = result.stream()
        .filter(bar -> "AAPL".equals(bar.getSymbol()))
        .toList();
    assertEquals(1, appleData.size());
    StorageStockBar firstAppleData = appleData.getFirst();
    assertEquals(150.0, firstAppleData.getOpen());
    assertEquals(155.0, firstAppleData.getClose());
    assertEquals(157.0, firstAppleData.getHigh());
    assertEquals(149.0, firstAppleData.getLow());
    assertEquals(1000, firstAppleData.getVolume());

    // Verify GOOG data
    List<StorageStockBar> googleData = result.stream()
        .filter(bar -> "GOOG".equals(bar.getSymbol()))
        .toList();
    assertEquals(1, googleData.size());
    StorageStockBar firstGoogleData = googleData.getFirst();
    assertEquals(2500.0, firstGoogleData.getOpen());
    assertEquals(2550.0, firstGoogleData.getClose());

    // Verify API was called twice (for pagination)
    verify(stockMarketDataEndpoint, times(1))
        .getBars(eq(symbols), eq(startZoned), eq(endZoned), isNull(), isNull(), eq(15),
            eq(BarTimePeriod.MINUTE), eq(BarAdjustment.RAW), eq(BarFeed.IEX));
    verify(stockMarketDataEndpoint, times(1))
        .getBars(eq(symbols), eq(startZoned), eq(endZoned), isNull(), eq("nextPageToken"), eq(15),
            eq(BarTimePeriod.MINUTE), eq(BarAdjustment.RAW), eq(BarFeed.IEX));
  }

  @Test
  void toZoneDateTime_shouldConvertLocalDateToZonedDateTime() throws Exception {
    // Use reflection to access the private method
    java.lang.reflect.Method method = PriceDataDTOImpl.class.getDeclaredMethod("toZoneDateTime",
        LocalDate.class);
    method.setAccessible(true);

    // Arrange
    LocalDate testDate = LocalDate.of(2023, 5, 15);

    // Act
    ZonedDateTime result = (ZonedDateTime) method.invoke(priceDataDTO, testDate);

    // Assert
    assertEquals(ZoneId.of("America/New_York"), result.getZone());
    assertEquals(testDate.getYear(), result.getYear());
    assertEquals(testDate.getMonthValue(), result.getMonthValue());
    assertEquals(testDate.getDayOfMonth(), result.getDayOfMonth());
    assertEquals(0, result.getHour());
    assertEquals(0, result.getMinute());
    assertEquals(0, result.getSecond());
  }
}