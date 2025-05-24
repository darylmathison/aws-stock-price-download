package com.darylmathison.market.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.darylmathison.market.SpringConfig;
import com.darylmathison.market.model.StorageStockBar;
import com.darylmathison.market.service.PriceDataService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

public class StockPriceLambdaHandler implements RequestHandler<Object, String> {

  private final String DATA_BUCKET = System.getenv("DATA_BUCKET");

  private final PriceDataService priceDataService;

  private final ApplicationContext context;

  public StockPriceLambdaHandler() {
    // Initialize Spring application context to instantiate the service
    context = new AnnotationConfigApplicationContext(SpringConfig.class);
    this.priceDataService = context.getBean(PriceDataService.class);
  }

  /**
   * Handle the Lambda request.
   *
   * @param input   The input for the Lambda function (can be passed as JSON).
   * @param context Lambda execution context.
   * @return A JSON string representing a week's worth of stock price data.
   */
  @Override
  public String handleRequest(Object input, Context context) {
    try {
      // Fetch price data using PriceDataService
      List<StorageStockBar> priceData = priceDataService.getPriceData();
      byte[] csvData = generateCompressedCSV(priceData);
      // Upload the CSV file to S3
      LocalDate today = LocalDate.now();
      String key = String.format("stock_prices/stock-price-data-%s.csv.gz", today);
      uploadFileToS3(DATA_BUCKET, key, csvData);
      // return the bucket and filename the data was recorded at.
      context.getLogger().log("Successfully uploaded price data to S3: " + key);
      // Return a JSON string with the S3 URL of the file.
      // This is the format expected by the frontend.
      return String.format("{\"bucket\": %s, \"key\": %s}", DATA_BUCKET, key);

    } catch (Exception e) {
      // Lambda error handling
      context.getLogger().log("Error fetching price data: " + e.getMessage());
      return "{\"error\": \"Unable to fetch price data\"}";
    }
  }

  private byte[] generateCompressedCSV(List<StorageStockBar> priceData) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    GZIPOutputStream gzipOut = new GZIPOutputStream(out);
    OutputStreamWriter writer = new OutputStreamWriter(gzipOut);

    try (CSVPrinter printer = new CSVPrinter(writer,
        CSVFormat.DEFAULT.builder().setHeader("symbol", "date", "open", "high", "low", "close", "volume").get())) {
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

    return out.toByteArray();
  }

  /**
   * Upload a file to S3 under the specified bucket and key.
   *
   * @param key      The key (file path) in the bucket.
   * @param fileData The byte array representing the file data.
   */
  private void uploadFileToS3(String bucket, String key, byte[] fileData) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("Content-Type", "text/csv");

    try(S3Client s3Client = context.getBean(S3Client.class);) {
      RequestBody requestBody = RequestBody.fromBytes(fileData);
      s3Client.putObject(request -> request.bucket(bucket).key(key).metadata(metadata),
          requestBody);
    }
  }

}