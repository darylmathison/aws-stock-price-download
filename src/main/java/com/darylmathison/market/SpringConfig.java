package com.darylmathison.market;

import net.jacobpeterson.alpaca.AlpacaAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.darylmathison.market") // Scans components in this package
public class SpringConfig {

  @Bean
  public AlpacaAPI alpacaAPI() {
    String alpacaApiKey = System.getenv("ALPACA_API_KEY");
    String alpacaSecretKey = System.getenv("ALPACA_SECRET_KEY");
    return new AlpacaAPI(alpacaApiKey, alpacaSecretKey);
  }
}
