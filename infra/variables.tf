variable "region" {
  type    = string
  default = "us-east-1"
}

variable "image_tag" {
  description = "Tag of the image pushed to the ECR repository"
  type        = string
  default     = "latest"
}

variable "desired_count" {
  type    = number
  default = 1
}
