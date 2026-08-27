variable "project_id" {
  description = "GCP project identifier for the DiabeCareKids backend."
  type        = string
}

variable "region" {
  description = "GCP region for resources."
  type        = string
  default     = "us-central1"
}
