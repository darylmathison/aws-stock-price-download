package com.darylmathison.market.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.darylmathison.market.model.StorageStockBar;
import com.darylmathison.market.service.PriceDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;


import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockPriceLambdaHandlerTest {

    @Mock
    private PriceDataService priceDataService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private Context context;

    @Mock
    private S3Client s3Client;

    private StockPriceLambdaHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StockPriceLambdaHandler();
        setField(handler, "priceDataService", priceDataService);
        setField(handler, "context", applicationContext);
        when(applicationContext.getBean(S3Client.class)).thenReturn(s3Client);

        LambdaLogger logger = mock(LambdaLogger.class);
        when(context.getLogger()).thenReturn(logger);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ignored) {}
    }

    @Test
    void testHandleRequest_returnsExpectedOutput() throws Exception {
        // Arrange
        List<StorageStockBar> mockBarList = List.of(
                StorageStockBar.builder()
                        .symbol("AAPL")
                        .timestamp(null)
                        .open(100.0)
                        .close(105.0)
                        .high(110.0)
                        .low(95.0)
                        .volume(1000L)
                        .build()
        );
        when(priceDataService.getPriceData()).thenReturn(mockBarList);

        // Act
        handler.handleRequest(null, context);

        verify(priceDataService, times(1)).getPriceData();
    }

    @Test
    void testHandleRequest_handlesExceptionGracefully() throws Exception {
        // Arrange
        when(priceDataService.getPriceData()).thenThrow(new RuntimeException("Service failure"));

        // Act
        String output = handler.handleRequest(null, context);
        assert output.contains("Unable to fetch price data");
        verify(priceDataService, times(1)).getPriceData();
    }
}