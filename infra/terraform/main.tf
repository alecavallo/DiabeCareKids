provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}

module "firebase_project" {
  source     = "./modules/firebase-project"
  project_id = var.project_id
}
