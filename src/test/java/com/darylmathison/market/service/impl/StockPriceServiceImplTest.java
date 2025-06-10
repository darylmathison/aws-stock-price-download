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
        setField(stockPriceService, "symbolsBatchSize", 2); // Set batch size to 2 for testing
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
        List<StorageStockBar> mockDataBatch1 = List.of(
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

        List<StorageStockBar> mockDataBatch2 = List.of(
            StorageStockBar.builder()
                .symbol("MSFT")
                .timestamp(now)
                .open(300.0)
                .close(305.0)
                .high(310.0)
                .low(295.0)
                .volume(750000L)
                .build()
        );

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(30);

        // First batch: AAPL, GOOG
        when(priceDataDTO.getPriceData(eq(List.of("AAPL", "GOOG")), eq(startDate), eq(today)))
            .thenReturn(mockDataBatch1);

        // Second batch: MSFT
        when(priceDataDTO.getPriceData(eq(List.of("MSFT")), eq(startDate), eq(today)))
            .thenReturn(mockDataBatch2);

        // When
        int result = stockPriceService.getPriceData();

        // Then
        assertEquals(3, result); // 2 from the first batch + 1 from the second batch

        // Verify S3 service calls
        verify(s3Service).fetchList(eq("test-bucket"), eq("symbols.txt"));

        // Verify price data DTO calls for each batch
        verify(priceDataDTO).getPriceData(eq(List.of("AAPL", "GOOG")), eq(startDate), eq(today));
        verify(priceDataDTO).getPriceData(eq(List.of("MSFT")), eq(startDate), eq(today));

        // Verify S3 upload with the correct key format for each batch
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);

        // Verify putObject was called twice (once for each batch)
        verify(s3Service, times(2)).putObject(eq("test-bucket"), keyCaptor.capture(), dataCaptor.capture());

        List<String> capturedKeys = keyCaptor.getAllValues();
        List<byte[]> capturedData = dataCaptor.getAllValues();

        assertEquals(2, capturedKeys.size());
        assertEquals(2, capturedData.size());

        String dateFormat = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // Verify first batch key format
        String expectedKey1 = "stock_prices_" + dateFormat + "_1.csv.gz";
        assertEquals(expectedKey1, capturedKeys.getFirst());
        assertTrue(capturedData.getFirst().length > 0);

        // Verify second batch key format
        String expectedKey2 = "stock_prices_" + dateFormat + "_2.csv.gz";
        assertEquals(expectedKey2, capturedKeys.get(1));
        assertTrue(capturedData.get(1).length > 0);
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
