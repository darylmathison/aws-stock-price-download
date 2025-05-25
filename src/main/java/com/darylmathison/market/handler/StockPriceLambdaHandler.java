package com.darylmathison.market.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.darylmathison.market.SpringConfig;
import com.darylmathison.market.service.StockPriceService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class StockPriceLambdaHandler implements RequestHandler<Object, String> {

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
      ApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
      StockPriceService stockPriceService = applicationContext.getBean(StockPriceService.class);

      int recordCount = stockPriceService.getPriceData();

      return String.format("{\"success\": true, \"recordsProcessed\": %d}", recordCount);
    } catch (Exception e) {
      // Lambda error handling
      context.getLogger().log("Error: " + e.getMessage());
      return String.format("{\"success\": false, \"error\": \"%s\"}", e.getMessage());
    }
  }
}