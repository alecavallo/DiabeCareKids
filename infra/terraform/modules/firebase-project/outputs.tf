output "firebase_service" {
  description = "Firebase service identifier enabled on the project."
  value       = google_project_service.firebase.service
}
