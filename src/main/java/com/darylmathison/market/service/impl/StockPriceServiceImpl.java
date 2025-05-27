package com.darylmathison.market.service.impl;

import com.darylmathison.market.model.StorageStockBar;
import com.darylmathison.market.dto.PriceDataDTO;
import com.darylmathison.market.service.S3Service;
import com.darylmathison.market.service.StockPriceService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation of StockPriceService for downloading and storing stock price data.
 */
@Service
public class StockPriceServiceImpl implements StockPriceService {

    @Value("${data.bucket.name}")
    private String dataBucketName;

    @Value("${data.symbols.file}")
    private String symbolsFileKey;

    @Value("${history.days:7}")
    private int historyDays;

    private final PriceDataDTO priceDataDTO;
    private final S3Service s3Service;

    public StockPriceServiceImpl(PriceDataDTO priceDataDTO, S3Service s3Service) {
        this.priceDataDTO = priceDataDTO;
        this.s3Service = s3Service;
    }

    /**
     * Downloads price data for configured symbols and stores it in S3.
     *
     * @return Number of price records processed
     */
    @Override
    public int getPriceData() {
        try {
            // Get symbols from S3
            List<String> symbols = s3Service.fetchList(dataBucketName, symbolsFileKey);

            // Calculate date range
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(historyDays);

            // Get price data
            List<StorageStockBar> priceData = priceDataDTO.getPriceData(symbols, startDate, endDate);

            // Convert to compressed CSV
            byte[] compressedData = generateCompressedCSV(priceData);

            // Create S3 key with today's date
            String key = generateS3Key(endDate);

            // Upload to S3
            s3Service.putObject(dataBucketName, key, compressedData);

            return priceData.size();
        } catch (Exception e) {
            throw new RuntimeException("Failed to process price data: " + e.getMessage(), e);
        }
    }

    /**
     * Generates an S3 key in the format "stock_prices/prices_{year}-{month}-{day}.csv.gz"
     */
    private String generateS3Key(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return String.format("stock_prices/prices_%s.csv.gz", date.format(formatter));
    }

    /**
     * Converts a list of StorageStockBar objects to a compressed CSV byte array.
     */
    private byte[] generateCompressedCSV(List<StorageStockBar> priceData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(out);
        OutputStreamWriter writer = new OutputStreamWriter(gzipOut);

        try (CSVPrinter printer = new CSVPrinter(writer,
                CSVFormat.DEFAULT.builder().setHeader("symbol", "timestamp", "open", "high", "low", "close", "volume").get())) {
            for (StorageStockBar bar : priceData) {
                printer.printRecord(
                        bar.getSymbol(),
                        bar.getTimestamp(),
                        bar.getOpen(),
                        bar.getHigh(),
                        bar.getLow(),
                        bar.getClose(),
                        bar.getVolume()
                );
            }
        }
        gzipOut.finish();
        return out.toByteArray();
    }
}