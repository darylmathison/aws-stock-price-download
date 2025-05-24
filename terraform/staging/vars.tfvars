lambda_function = "stock-download"
lambda_filename = "cloud-native-stock-download-1.0-SNAPSHOT.jar"
file_location = "../target/cloud-native-stock-download-1.0-SNAPSHOT.jar"
symbols_location = "../data/symbols.txt"
lambda_handler = "com.darylmathison.market.handler.StockPriceLambdaHandler::handleRequest"
runtime = "java21"
timeout = 300