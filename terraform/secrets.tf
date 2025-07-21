resource "aws_secretsmanager_secret" "alpaca_api_key" {
  name        = "download_alpaca_api_key"
  description = "Secret for my Alpaca Api Key"

  tags = {
    Environment = "staging"
    Project     = "download prices"
  }
}

resource "aws_secretsmanager_secret_version" "alpaca_api_key_version" {
  secret_id = aws_secretsmanager_secret.alpaca_api_key.id
  secret_string = jsonencode({
    apiKey = var.alpacaApiKey
    secretKey = var.alpacaSecretKey
  })
}

resource "aws_iam_policy" "alpaca_secrets_read_policy" {
  name        = "alpaca_secrets_read_policy"
  description = "Allows reading of the alpaca api secrets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret",
        ]
        Resource = [
          aws_secretsmanager_secret.alpaca_api_key.arn
        ]
      },
    ]
  })
}