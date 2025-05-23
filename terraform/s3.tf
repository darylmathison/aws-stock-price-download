resource "random_uuid" "bucket_suffix" {}


resource "aws_s3_bucket" "code_bucket" {
  bucket = "${var.code_bucket}-${random_uuid.bucket_suffix.result}"
}

resource "aws_s3_bucket" "data_bucket" {
  bucket = "${var.data_bucket_name}-${random_uuid.bucket_suffix.result}"
}