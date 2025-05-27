package com.darylmathison.market;

import net.jacobpeterson.alpaca.AlpacaAPI;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ComponentScan(basePackages = "com.darylmathison.market") // Scans components in this package
public class SpringConfig {

  @Value("${alpaca.apikey}")
  private String alpacaApiKey;

  @Value("${alpaca.secret}")
  private String alpacaSecretKey;

  @Value("${aws.region}")
  private String awsRegion;

  @Bean
  public AlpacaAPI alpacaAPI() {
    return new AlpacaAPI(alpacaApiKey, alpacaSecretKey);
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public S3Client s3Client() {
    return S3Client
        .builder()
        .region(Region.of(awsRegion))
        .build();
  }
}
