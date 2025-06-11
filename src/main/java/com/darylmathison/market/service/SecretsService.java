package com.darylmathison.market.service;


import com.darylmathison.market.model.ApiKeyPair;

public interface SecretsService {
  ApiKeyPair getSecretApiKeyPair(String secretName);

  String getSecret(String secretName);
}
