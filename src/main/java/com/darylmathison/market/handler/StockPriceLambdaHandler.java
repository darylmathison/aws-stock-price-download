package com.darylmathison.market.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.darylmathison.market.config.SpringConfig;
import com.darylmathison.market.service.StockPriceService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.logging.Logger;
import java.util.logging.Level;


@SpringBootApplication
@EnableConfigurationProperties
public class StockPriceLambdaHandler implements RequestHandler<Object, String> {

  private static final Logger logger = Logger.getLogger(StockPriceLambdaHandler.class.getName());

  /**
   * Handle the Lambda request.
   *
   * @param input   The input for the Lambda function (can be passed as JSON).
   * @param context Lambda execution context.
   * @return A JSON string representing the result of the price data download.
   */
  @Override
  public String handleRequest(Object input, Context context) {
    try {
      SpringApplication app = new SpringApplication(SpringConfig.class);
      app.setWebApplicationType(WebApplicationType.NONE);

      if (context != null) {
        context.getLogger().log("Starting application context");
      }

      ConfigurableApplicationContext applicationContext = app.run();
      StockPriceService stockPriceService = applicationContext.getBean(StockPriceService.class);

      int recordCount = stockPriceService.getPriceData();
      logger.info("Downloaded " + recordCount + " stock price records");
      return String.format("{\"success\": true, \"recordsProcessed\": %d}", recordCount);
    } catch (Exception e) {
      // Lambda error handling
      logger.log(Level.SEVERE, "Failed to process request", e);
      return String.format("{\"success\": false, \"error\": \"%s\"}", e.getMessage());
    }
  }
}