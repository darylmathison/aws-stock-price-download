lambda_function = "coffee_tips_aws_lambda"
lambda_filename = "aws-lambda-terraform-java-1.0.jar"
file_location = "../target/aws-lambda-terraform-java-1.0.jar"
lambda_handler = "com.example.alpaca.StockPriceLambdaHandler::handleRequest"
runtime = "java21"
timeout = 300