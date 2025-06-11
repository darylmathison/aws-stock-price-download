package com.darylmathison.market.service.impl;

import com.darylmathison.market.model.ApiKeyPair;
import com.darylmathison.market.service.SecretsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SecretsServiceImpl implements SecretsService {

  @Value("${aws.region}")
  private String awsRegion;

  @Override
  public ApiKeyPair getSecretApiKeyPair(String secretName) {
    String jsonString = getSecret(secretName);
      // If your secret is JSON, you can parse it
     ObjectMapper mapper = new ObjectMapper();
     ApiKeyPair apiKey;
     try {
         apiKey = mapper.readValue(jsonString, ApiKeyPair.class);
     } catch (JsonProcessingException e) {
       System.err.println("Error parsing secret JSON: " + e.getMessage());
       throw new RuntimeException("Failed to parse secret JSON: " + e.getMessage(), e);
     }
     return apiKey;
  }

  @Override
  public String getSecret(String secretName) {
    GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

    try(SecretsManagerClient secretsClient = buildSecretsManagerClient()) {
        GetSecretValueResponse getSecretValueResponse = secretsClient.getSecretValue(getSecretValueRequest);
        return getSecretValueResponse.secretString();
    } catch (SecretsManagerException e) {
        System.err.println("Error retrieving secret: " + e.awsErrorDetails().errorMessage());
        return null;
    }
  }

  protected SecretsManagerClient buildSecretsManagerClient() {
    return SecretsManagerClient.builder().region(Region.of( awsRegion)).build();
  }

}
