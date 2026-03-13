terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }
}

provider "oci" {
  tenancy_ocid = var.tenancy_ocid
  user_ocid    = var.user_ocid
  fingerprint  = var.fingerprint
  private_key_path = var.private_key_path
  region       = var.region
}

# --- Availability Domain ---
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

# --- Network ---
resource "oci_core_vcn" "pantri" {
  compartment_id = var.compartment_ocid
  display_name   = "pantri-vcn"
  cidr_blocks    = ["10.0.0.0/16"]
  dns_label      = "pantri"
}

resource "oci_core_internet_gateway" "pantri" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.pantri.id
  display_name   = "pantri-igw"
}

resource "oci_core_route_table" "pantri" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.pantri.id
  display_name   = "pantri-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.pantri.id
  }
}

resource "oci_core_security_list" "pantri" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.pantri.id
  display_name   = "pantri-sl"

  # Allow all egress
  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
  }

  # SSH
  ingress_security_rules {
    protocol = "6" # TCP
    source   = "0.0.0.0/0"
    tcp_options {
      min = 22
      max = 22
    }
  }

  # HTTP
  ingress_security_rules {
    protocol = "6"
    source   = "0.0.0.0/0"
    tcp_options {
      min = 80
      max = 80
    }
  }

  # HTTPS
  ingress_security_rules {
    protocol = "6"
    source   = "0.0.0.0/0"
    tcp_options {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_subnet" "pantri" {
  compartment_id    = var.compartment_ocid
  vcn_id            = oci_core_vcn.pantri.id
  display_name      = "pantri-subnet"
  cidr_block        = "10.0.1.0/24"
  route_table_id    = oci_core_route_table.pantri.id
  security_list_ids = [oci_core_security_list.pantri.id]
  dns_label         = "pantrisub"
}

# --- Compute (Always Free ARM) ---

# Get the latest Ubuntu 22.04 aarch64 image
data "oci_core_images" "ubuntu" {
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "22.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

resource "oci_core_instance" "pantri" {
  compartment_id      = var.compartment_ocid
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  display_name        = "pantri"
  shape               = "VM.Standard.A1.Flex"

  shape_config {
    ocpus         = 1
    memory_in_gbs = 6
  }

  source_details {
    source_type = "image"
    source_id   = data.oci_core_images.ubuntu.images[0].id
    boot_volume_size_in_gbs = 50
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.pantri.id
    assign_public_ip = true
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data           = base64encode(file("${path.module}/cloud-init.yaml"))
  }
}

output "public_ip" {
  value = oci_core_instance.pantri.public_ip
}

output "ssh_command" {
  value = "ssh ubuntu@${oci_core_instance.pantri.public_ip}"
}
