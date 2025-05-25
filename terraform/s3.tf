resource "random_id" "bucket_suffix" {
  byte_length = 10
}


resource "aws_s3_bucket" "code_bucket" {
  bucket = "${var.code_bucket}-${random_id.bucket_suffix.hex}"
}

resource "aws_s3_bucket" "data_bucket" {
  bucket = "${var.data_bucket_name}-${random_id.bucket_suffix.hex}"
}