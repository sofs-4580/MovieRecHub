output "public_ips" {
  description = "Public IPs for SSH, kubectl and the frontend; refresh after a stop/start."
  value = {
    control_plane = google_compute_instance.control_plane.network_interface[0].access_config[0].nat_ip
    workers = {
      for worker in google_compute_instance.worker : worker.name => worker.network_interface[0].access_config[0].nat_ip
    }
  }
}

output "ansible_inventory" {
  description = "JSON/YAML inventory for Ansible, with public SSH and private cluster IPs."
  value = {
    all = {
      vars = {
        ansible_user                 = local.ssh_user
        ansible_ssh_private_key_file = trimsuffix(pathexpand(var.ssh_public_key_path), ".pub")
        ansible_python_interpreter   = "/usr/bin/python3"
        kubernetes_pod_cidr          = local.pod_cidr
        kubernetes_service_cidr      = local.service_cidr
        calico_ipip_mode             = "Always"
        gcp_project_id               = var.project_id
        registry_image_prefix        = local.registry_image_prefix
        registry_service_account     = google_service_account.nodes.email
      }
      children = {
        control_plane = {
          hosts = {
            (google_compute_instance.control_plane.name) = {
              ansible_host       = google_compute_instance.control_plane.network_interface[0].access_config[0].nat_ip
              kubernetes_node_ip = google_compute_instance.control_plane.network_interface[0].network_ip
            }
          }
        }
        workers = {
          hosts = {
            for index, worker in google_compute_instance.worker : worker.name => merge(
              {
                ansible_host       = worker.network_interface[0].access_config[0].nat_ip
                kubernetes_node_ip = worker.network_interface[0].network_ip
              },
              index == 0 ? {
                kubernetes_data_device = "/dev/disk/by-id/google-${local.data_device_name}"
                kubernetes_data_path   = local.data_mount_path
              } : {}
            )
          }
        }
      }
    }
  }
}
