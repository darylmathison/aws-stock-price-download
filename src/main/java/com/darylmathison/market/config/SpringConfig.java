package com.darylmathison.market.config;

import com.darylmathison.market.model.ApiKeyPair;
import com.darylmathison.market.service.SecretsService;
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

  @Value("${aws.region}")
  private String awsRegion;

  @Value("${alpaca.secret-name}")
  private String alpacaSecretName;

  @Bean
  public AlpacaAPI alpacaAPI(SecretsService secretsService) {
    ApiKeyPair alpacaApiKeyPair = secretsService.getSecretApiKeyPair(alpacaSecretName);
    return new AlpacaAPI(alpacaApiKeyPair.getApiKey(), alpacaApiKeyPair.getSecretKey());
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
