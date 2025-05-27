package com.darylmathison.market;

import net.jacobpeterson.alpaca.AlpacaAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@ComponentScan(basePackages = "com.darylmathison.market") // Scans components in this package
public class SpringConfig {

  @Value("${alpaca.apikey}")
  private String alpacaApiKey;

  @Value("${alpaca.secret}")
  private String alpacaSecretKey;

  @Bean
  public AlpacaAPI alpacaAPI() {
    return new AlpacaAPI(alpacaApiKey, alpacaSecretKey);
  }
}
