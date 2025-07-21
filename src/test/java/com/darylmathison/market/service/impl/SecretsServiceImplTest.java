package com.darylmathison.market.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darylmathison.market.model.ApiKeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@ExtendWith(MockitoExtension.class)
class SecretsServiceImplTest {

  // Create a testable subclass that makes the private method public for testing
  static class TestableSecretsServiceImpl extends SecretsServiceImpl {

    private final SecretsManagerClient mockClient;

    public TestableSecretsServiceImpl(SecretsManagerClient mockClient) {
      this.mockClient = mockClient;
    }

    // Make the private method public for testing
    @Override
    public SecretsManagerClient buildSecretsManagerClient() {
      return mockClient;
    }
  }

  @Test
  void getSecret_shouldReturnSecretString_whenSecretExists() {
    // Given
    String secretName = "test-secret";
    String expectedSecret = "test-secret-value";

    // Mock the GetSecretValueResponse
    GetSecretValueResponse mockResponse = mock(GetSecretValueResponse.class);
    when(mockResponse.secretString()).thenReturn(expectedSecret);

    // Mock the SecretsManagerClient behavior
    SecretsManagerClient mockClient = mock(SecretsManagerClient.class);
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(mockResponse);

    // Create our testable service implementation
    TestableSecretsServiceImpl secretsService = new TestableSecretsServiceImpl(mockClient);

    // When
    String result = secretsService.getSecret(secretName);

    // Then
    assertEquals(expectedSecret, result);
    verify(mockClient).getSecretValue(any(GetSecretValueRequest.class));
  }

  @Test
  void getSecret_shouldReturnNull_whenExceptionOccurs() {
    // Given
    String secretName = "test-secret";

    // Mock the SecretsManagerClient to throw an exception
    AwsErrorDetails errorDetails = AwsErrorDetails.builder().errorMessage("error-message").build();
    SecretsManagerException exception = (SecretsManagerException) SecretsManagerException.builder()
        .awsErrorDetails(errorDetails).build();

    SecretsManagerClient mockClient = mock(SecretsManagerClient.class);
    when(mockClient.getSecretValue(any(GetSecretValueRequest.class))).thenThrow(exception);

    // Create our testable service implementation
    TestableSecretsServiceImpl secretsService = new TestableSecretsServiceImpl(mockClient);

    // When
    assertThrows(RuntimeException.class, () ->secretsService.getSecret(secretName));
  }

  @Test
  void getSecretApiKeyPair_shouldReturnApiKeyPair_whenSecretExists() {
    // Given
    String secretName = "test-secret";
    String secretJson = "{\"apiKey\":\"test-api-key\",\"secretKey\":\"test-secret-key\"}";

    // Create expected ApiKeyPair
    ApiKeyPair expectedApiKeyPair = new ApiKeyPair();
    expectedApiKeyPair.setApiKey("test-api-key");
    expectedApiKeyPair.setSecretKey("test-secret-key");

    // Create a spy of our service to mock getSecret
    TestableSecretsServiceImpl secretsService = spy(
        new TestableSecretsServiceImpl(mock(SecretsManagerClient.class)));
    doReturn(secretJson).when(secretsService).getSecret(secretName);

    // When
    ApiKeyPair result = secretsService.getSecretApiKeyPair(secretName);

    // Then
    assertNotNull(result);
    assertEquals(expectedApiKeyPair.getApiKey(), result.getApiKey());
    assertEquals(expectedApiKeyPair.getSecretKey(), result.getSecretKey());
    verify(secretsService).getSecret(secretName);
  }

  @Test
  void getSecretApiKeyPair_shouldThrowRuntimeException_whenJsonParsingFails() {
    // Given
    String secretName = "test-secret";
    String invalidJson = "{invalid-json}";

    // Create a spy of our service to mock getSecret
    TestableSecretsServiceImpl secretsService = spy(
        new TestableSecretsServiceImpl(mock(SecretsManagerClient.class)));
    doReturn(invalidJson).when(secretsService).getSecret(secretName);

    // When & Then
    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> secretsService.getSecretApiKeyPair(secretName));
    assertTrue(exception.getMessage().contains("Failed to parse secret JSON"));
    verify(secretsService).getSecret(secretName);
  }
}
