variable "region" {
  default = "us-east-2"
}

variable "code_bucket" {
  default = "code"
}
variable "data_bucket_name" {
  default = "data"
}
variable "lambda_function" {}
variable "lambda_filename" {}
variable "file_location" {}
variable "lambda_handler" {}
variable "runtime" {
  default = "java21"
}
variable "cron_friday_after_market" {
  default = "0 17 * * 5"
}
variable "timeout" {}

variable "timezone" {
  default = "US/Eastern"
}
