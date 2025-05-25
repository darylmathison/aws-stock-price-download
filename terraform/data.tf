resource "aws_s3_object" "symbols_upload" {
  depends_on = [aws_s3_bucket.data_bucket]
  bucket = aws_s3_bucket.data_bucket.bucket
  key    = var.symbols_filename
  source = var.symbols_location
  etag = filemd5(var.symbols_location)
}
