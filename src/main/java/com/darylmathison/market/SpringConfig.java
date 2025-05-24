package com.darylmathison.market;

import net.jacobpeterson.alpaca.AlpacaAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Scope;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ComponentScan(basePackages = "com.darylmathison.market") // Scans components in this package
public class SpringConfig {

  @Value("${aws.s3.access-key}")
  private String awsAccessKey;

  @Value("${aws.s3.secret-key}")
  private String awsSecretKey;

  @Value("${aws.s3.region}")
  private String awsRegion;

  @Bean
  public AlpacaAPI alpacaAPI() {
    String alpacaApiKey = System.getenv("ALPACA_API_KEY");
    String alpacaSecretKey = System.getenv("ALPACA_SECRET_KEY");
    return new AlpacaAPI(alpacaApiKey, alpacaSecretKey);
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public S3Client s3Client() {
    AwsCredentials credentials = AwsBasicCredentials.create(awsAccessKey, awsSecretKey);
    return S3Client
        .builder()
        .region(Region.of(awsRegion))
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .build();
  }
}
