
data "aws_iam_policy_document" "extract_market_data_document" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

data "aws_iam_policy_document" "aws_iam_extract_market_data_aws_lambda_iam_policy_document" {
  statement {
    effect = "Allow"
    resources = ["*"]
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
  }

  statement {
    effect = "Allow"
    resources = [
      aws_s3_bucket.data_bucket.arn,
      "${aws_s3_bucket.data_bucket.arn}/*"
    ]
    actions = [
      "s3:GetObject",
      "s3:GetObjectVersion",
      "s3:GetObjectAcl",
      "s3:GetObjectTagging",
      "s3:PutObject",
      "s3:PutObjectAcl",
      "s3:DeleteObject",
      "s3:DeleteObjectVersion",
      "s3:PutObjectTagging"
    ]
  }
}

resource "aws_iam_role" "iam_for_lambda" {
  name = "iam_for_lambda"
  assume_role_policy = data.aws_iam_policy_document.extract_market_data_document.json
}

resource "aws_iam_role_policy" "aws_lambda_iam_policy" {
  policy = data.aws_iam_policy_document.aws_iam_extract_market_data_aws_lambda_iam_policy_document.json
  role = aws_iam_role.iam_for_lambda.id
}

resource "aws_s3_object" "s3_object_upload" {
  depends_on = [aws_s3_bucket.code_bucket]
  bucket = aws_s3_bucket.code_bucket.bucket
  key    = var.lambda_filename
  source = var.file_location
  etag = filemd5(var.file_location)
}

resource "aws_lambda_function" "extract_market_data_aws_lambda" {
  depends_on = [aws_s3_object.s3_object_upload]
  function_name = var.lambda_function
  role          = aws_iam_role.iam_for_lambda.arn
  handler       = var.lambda_handler
  source_code_hash = filebase64sha256(var.file_location)
  s3_bucket     = aws_s3_bucket.code_bucket.bucket
  s3_key        = var.lambda_filename
  runtime       = var.runtime
  timeout       = var.timeout
  memory_size = 512
  environment {
    variables = {
      TZ = var.timezone
      DATA_BUCKET = aws_s3_bucket.data_bucket.bucket
      SYMBOLS = var.symbols_filename
      HISTORY_DAYS = var.history_days
      ALPACA_API_KEY = var.alpacaApiKey
      ALPACA_SECRET_KEY = var.alpacaSecretKey
    }
  }
}

resource "aws_cloudwatch_event_rule" "event_rule" {
  name = "event_rule"
  schedule_expression = var.cron_friday_after_market
}

resource "aws_cloudwatch_event_target" "event_target" {
  arn  = aws_lambda_function.extract_market_data_aws_lambda.arn
  rule = aws_cloudwatch_event_rule.event_rule.name
  target_id = aws_lambda_function.extract_market_data_aws_lambda.function_name
}

resource "aws_lambda_permission" "lambda_permission" {
  statement_id = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.extract_market_data_aws_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn = aws_cloudwatch_event_rule.event_rule.arn
}