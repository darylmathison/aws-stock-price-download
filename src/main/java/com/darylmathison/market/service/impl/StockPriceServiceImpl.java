package com.darylmathison.market.service.impl;

import com.darylmathison.market.service.StockPriceService;
import com.darylmathison.market.model.StorageStockBar;
import com.darylmathison.market.dto.PriceDataDTO;
import com.darylmathison.market.service.S3Service;
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
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation of StockPriceService for downloading and storing stock price data.
 */
@Service
public class StockPriceServiceImpl implements StockPriceService {

    private static final Logger logger = Logger.getLogger(StockPriceServiceImpl.class.getName());

    @Value("${data.bucket.name}")
    private String dataBucketName;

    @Value("${data.symbols.file}")
    private String symbolsFileKey;

    @Value("${history.days:5}")
    private int historyDays;

    @Value("${data.symbols.batch-size:3000}")
    private int symbolsBatchSize;

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
            logger.info("Starting price data download process");

            // Get symbols from S3
            List<String> symbols = s3Service.fetchList(dataBucketName, symbolsFileKey);
            logger.info("Fetched " + symbols.size() + " symbols from S3");

            // Calculate date range
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(historyDays);
            logger.info("Downloading price data from " + startDate + " to " + endDate);

            // Process symbols in batches
            int totalRecords = 0;
            for (int i = 0; i < symbols.size(); i += symbolsBatchSize) {
                int endIndex = Math.min(i + symbolsBatchSize, symbols.size());
                List<String> symbolsBatch = symbols.subList(i, endIndex);
                int batchNumber = i / symbolsBatchSize + 1;
                logger.info("Processing batch " + batchNumber + 
                           " with " + symbolsBatch.size() + " symbols (from index " + i + " to " + (endIndex - 1) + ")");

                // Get price data for this batch
                List<StorageStockBar> batchPriceData = priceDataDTO.getPriceData(symbolsBatch, startDate, endDate);
                logger.info("Retrieved " + batchPriceData.size() + " price records for current batch");

                // Convert batch data to compressed CSV
                byte[] compressedData = generateCompressedCSV(batchPriceData);
                logger.info("Generated compressed CSV data for batch " + batchNumber + ": " + compressedData.length + " bytes");

                // Create S3 key with today's date and batch number
                String key = generateS3Key(endDate, batchNumber);

                // Upload batch to S3
                s3Service.putObject(dataBucketName, key, compressedData);
                logger.info("Successfully uploaded batch " + batchNumber + " price data to S3: " + key);

                // Add to the total count
                totalRecords += batchPriceData.size();
            }

            logger.info("Total retrieved price records: " + totalRecords);

            return totalRecords;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to process price data: " + e.getMessage(), e);
            throw new RuntimeException("Failed to process price data: " + e.getMessage(), e);
        }
    }

    /**
     * Generates an S3 key in the format "stock_prices_YYYY-MM-DD_{batch number}.csv.gz"
     */
    private String generateS3Key(LocalDate date, int batchNumber) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return String.format("stock_prices_%s_%d.csv.gz", date.format(formatter), batchNumber);
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
