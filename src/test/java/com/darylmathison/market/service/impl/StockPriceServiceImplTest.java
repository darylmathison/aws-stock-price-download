package com.darylmathison.market.service.impl;

import com.darylmathison.market.model.StorageStockBar;
import com.darylmathison.market.dto.PriceDataDTO;
import com.darylmathison.market.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockPriceServiceImplTest {

    @Mock
    private PriceDataDTO priceDataDTO;

    @Mock
    private S3Service s3Service;

    private StockPriceServiceImpl stockPriceService;

    @BeforeEach
    void setUp() {
        stockPriceService = new StockPriceServiceImpl(priceDataDTO, s3Service);
        setField(stockPriceService, "dataBucketName", "test-bucket");
        setField(stockPriceService, "symbolsFileKey", "symbols.txt");
        setField(stockPriceService, "historyDays", 30);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ignored) {}
    }

    @Test
    void getPriceData_shouldReturnNumberOfRecordsProcessed() throws Exception {
        // Given
        List<String> symbols = List.of("AAPL", "GOOG", "MSFT");
        when(s3Service.fetchList(eq("test-bucket"), eq("symbols.txt"))).thenReturn(symbols);

        ZonedDateTime now = ZonedDateTime.now();
        List<StorageStockBar> mockData = List.of(
            StorageStockBar.builder()
                .symbol("AAPL")
                .timestamp(now)
                .open(150.0)
                .close(152.0)
                .high(155.0)
                .low(149.0)
                .volume(1000000L)
                .build(),
            StorageStockBar.builder()
                .symbol("GOOG")
                .timestamp(now)
                .open(2500.0)
                .close(2520.0)
                .high(2550.0)
                .low(2480.0)
                .volume(500000L)
                .build()
        );

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(30);

        when(priceDataDTO.getPriceData(eq(symbols), eq(startDate), eq(today))).thenReturn(mockData);

        // When
        int result = stockPriceService.getPriceData();

        // Then
        assertEquals(2, result);

        // Verify S3 service calls
        verify(s3Service).fetchList(eq("test-bucket"), eq("symbols.txt"));

        // Verify price data DTO call
        verify(priceDataDTO).getPriceData(eq(symbols), eq(startDate), eq(today));

        // Verify S3 upload with correct key format
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(s3Service).putObject(eq("test-bucket"), keyCaptor.capture(), dataCaptor.capture());

        String expectedKeyPrefix = "stock_prices/prices_" + today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        assertTrue(keyCaptor.getValue().startsWith(expectedKeyPrefix));
        assertTrue(dataCaptor.getValue().length > 0);
    }

    @Test
    void getPriceData_shouldThrowRuntimeExceptionWhenFetchSymbolsFails() throws Exception {
        // Given
        when(s3Service.fetchList(anyString(), anyString())).thenThrow(new RuntimeException("Failed to fetch symbols"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> stockPriceService.getPriceData());
        assertTrue(exception.getMessage().contains("Failed to process price data"));
        verify(s3Service).fetchList(anyString(), anyString());
        verify(priceDataDTO, never()).getPriceData(anyList(), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void getPriceData_shouldThrowRuntimeExceptionWhenGetPriceDataFails() throws Exception {
        // Given
        List<String> symbols = List.of("AAPL", "GOOG", "MSFT");
        when(s3Service.fetchList(anyString(), anyString())).thenReturn(symbols);
        when(priceDataDTO.getPriceData(anyList(), any(LocalDate.class), any(LocalDate.class)))
            .thenThrow(new Exception("Failed to get price data"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> stockPriceService.getPriceData());
        assertTrue(exception.getMessage().contains("Failed to process price data"));
        verify(s3Service).fetchList(anyString(), anyString());
        verify(priceDataDTO).getPriceData(anyList(), any(LocalDate.class), any(LocalDate.class));
    }
}