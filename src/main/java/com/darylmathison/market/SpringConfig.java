package com.darylmathison.market;

import net.jacobpeterson.alpaca.AlpacaAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@ComponentScan(basePackages = "com.darylmathison.market") // Scans components in this package
public class SpringConfig {

  @Value("${env.ALPACA_API_KEY}")
  private String alpacaApiKey;

  @Value("${env.ALPACA_SECRET_KEY}")
  private String alpacaSecretKey;

  @Bean
  public AlpacaAPI alpacaAPI() {
    return new AlpacaAPI(alpacaApiKey, alpacaSecretKey);
  }
}
