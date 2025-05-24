package com.darylmathison.market.service;

import com.darylmathison.market.model.StorageStockBar;
import java.util.function.Consumer;
import net.jacobpeterson.alpaca.AlpacaAPI;
import net.jacobpeterson.alpaca.rest.endpoint.marketdata.stock.StockMarketDataEndpoint;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.StockBar;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.common.historical.bar.enums.BarTimePeriod;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.MultiStockBarsResponse;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.enums.BarAdjustment;
import net.jacobpeterson.alpaca.model.endpoint.marketdata.stock.historical.bar.enums.BarFeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.*;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceDataServiceTest {

    @Mock
    private AlpacaAPI alpacaAPI;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private S3Client s3Client;

    @Mock
    private StockMarketDataEndpoint stockMarketDataEndpoint;

    private PriceDataServiceImpl priceDataService;

    @BeforeEach
    void setUp() {
        priceDataService = new PriceDataServiceImpl(alpacaAPI, applicationContext);
        // Optionally set fields via reflection if @Value fields are required in test
        setField(priceDataService, "dataBucketName", "test-bucket");
        setField(priceDataService, "symbolsFileKey", "symbols.txt");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ignored) {}
    }

    @Test
    void getPriceData_shouldReturnStockBarList() throws Exception {
        // Prepare symbol list from S3
        String symbolList = "AAPL\nGOOG";
        InputStream symbolStream = new ByteArrayInputStream(symbolList.getBytes());
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(mock(GetObjectResponse.class), symbolStream);
        when(applicationContext.getBean(S3Client.class)).thenReturn(s3Client);
        when(s3Client.getObject(any(Consumer.class)))
            .thenReturn(responseStream);

        // Prepare MultiStockBarsResponse
        when(alpacaAPI.stockMarketData()).thenReturn(stockMarketDataEndpoint);

        String token1 = "token1";
        String token2 = null;

        List<String> symbols = List.of("AAPL", "GOOG");
        ZonedDateTime now = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        StockBar bar1 = mock(StockBar.class);
        when(bar1.getTimestamp()).thenReturn(now);
        when(bar1.getOpen()).thenReturn(100.0);
        when(bar1.getClose()).thenReturn(110.0);
        when(bar1.getHigh()).thenReturn(115.0);
        when(bar1.getLow()).thenReturn(95.0);
        when(bar1.getTradeCount()).thenReturn(120L);

        HashMap<String, ArrayList<StockBar>> barsMap1 = new HashMap<>();
        barsMap1.put("AAPL", new ArrayList<>(List.of(bar1)));

        MultiStockBarsResponse response1 = mock(MultiStockBarsResponse.class);
        when(response1.getBars()).thenReturn(barsMap1);
        when(response1.getNextPageToken()).thenReturn(token1);

        MultiStockBarsResponse response2 = mock(MultiStockBarsResponse.class);
        when(response2.getNextPageToken()).thenReturn(token2);

        when(stockMarketDataEndpoint.getBars(eq(symbols), any(), any(), isNull(), isNull(), eq(15), eq(BarTimePeriod.MINUTE), eq(BarAdjustment.RAW), eq(BarFeed.IEX)))
                .thenReturn(response1);
        when(stockMarketDataEndpoint.getBars(eq(symbols), any(), any(), isNull(), eq(token1), eq(15), eq(BarTimePeriod.MINUTE), eq(BarAdjustment.RAW), eq(BarFeed.IEX)))
                .thenReturn(response2);

        // Execute
        List<StorageStockBar> data = priceDataService.getPriceData();

        // Verify
        assertNotNull(data);
        assertEquals(1, data.size());
        assertEquals("AAPL", data.getFirst().getSymbol());
        assertEquals(100.0, data.getFirst().getOpen());
        assertEquals(110.0, data.getFirst().getClose());
        assertEquals(115.0, data.getFirst().getHigh());
        assertEquals(95.0, data.getFirst().getLow());
        assertEquals(120, data.getFirst().getVolume());

        verify(applicationContext, times(1)).getBean(S3Client.class);
        verify(s3Client, times(1)).getObject(any(Consumer.class));
        verify(alpacaAPI, atLeastOnce()).stockMarketData();
    }

    @Test
    void fetchSymbolsFromS3_shouldReturnSymbolsList() throws Exception {
        // Given
        String symbolData = "AAPL\nTSLA\nMSFT";
        InputStream inputStream = new ByteArrayInputStream(symbolData.getBytes());
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(mock(GetObjectResponse.class), inputStream);
        when(s3Client.getObject(any(Consumer.class)))
            .thenReturn(responseStream);

        setField(priceDataService, "dataBucketName", "bucket");
        setField(priceDataService, "symbolsFileKey", "file.txt");

        // When
        List<String> symbols = priceDataService.fetchSymbolsFromS3(s3Client);

        // Then
        assertEquals(List.of("AAPL", "TSLA", "MSFT"), symbols);
    }
}