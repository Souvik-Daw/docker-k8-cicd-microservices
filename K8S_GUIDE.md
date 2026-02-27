# Kubernetes Deployment Guide (EC2 Self-Managed)

Welcome to your Kubernetes deployment guide! This document explains the core Kubernetes concepts you need to know and provides a step-by-step walkthrough to deploy your microservices on an AWS EC2 instance.

## 1. Core Kubernetes Concepts

### Pods & ReplicaSets
- **Pod**: The smallest deployable computing unit in Kubernetes. Think of it as a wrapper around one or more Docker containers that share the same network and storage.
- **ReplicaSet**: Ensures that a specified number of Pod replicas are running at any given time. If a Pod crashes, the ReplicaSet starts a new one to replace it.

### Deployments
A **Deployment** provides declarative updates for Pods and ReplicaSets. You describe the *desired state* in a Deployment, and the Deployment Controller changes the actual state to the desired state at a controlled rate. This is what we use in our YAML files (`04-postgres-deployment.yml`, etc.) to run your Docker images.

### Services (ClusterIP, NodePort, LoadBalancer)
Pods are ephemeral (they can die and be recreated with new IP addresses). A **Service** provides a stable, permanent IP address and DNS name.
- **ClusterIP** *(Default)*: Exposes the Service on a cluster-internal IP. This is what we use for your microservices so they can talk to each other securely (e.g., `http://microservice-2:8082`).
- **NodePort**: Exposes the Service on each Node's IP at a static port. Useful for development but generally not recommended for production.
- **LoadBalancer**: Exposes the Service externally using a cloud provider's load balancer (like an AWS ALB).

### Ingress Controllers (NGINX)
An **Ingress** acts as an API Gateway and smart router. Instead of exposing every single service via a `NodePort` or `LoadBalancer`, you expose *only* the Ingress Controller to the public. Ingress rules (defined in `09-ingress.yml`) then intelligently route HTTP traffic like `your-domain.com/ms1/` to the internal `microservice-1` ClusterIP service.

### ConfigMaps & Secrets
- **ConfigMap**: Used to store non-confidential data in key-value pairs (like your `EUREKA_CLIENT_ENABLED=false` flag). Pods consume these as environment variables.
- **Secret**: Used to store sensitive data like passwords, OAuth tokens, and ssh keys. We use this for your Postgres credentials, keeping them safely base-64 encoded and encrypted at rest in the cluster.

### Scaling: Horizontal (HPA) vs Vertical (VPA)
- **Horizontal Pod Autoscaler (HPA)**: Scales out the *number of Pods* in a deployment based on CPU utilization or other metrics. We defined HPAs for your microservices to scale from 1 to 3 replicas automatically under heavy load.
- **Vertical Pod Autoscaler (VPA)**: Scales up the *resources (CPU/RAM)* allocated to an existing single Pod. Generally, HPA is preferred for stateless microservices like yours.

### Persistent Volumes (PV) & Claims (PVC)
- **PersistentVolume (PV)**: A physical piece of storage in the cluster that has been provisioned by an administrator.
- **PersistentVolumeClaim (PVC)**: A structural request for storage by a Deployment. Our Postgres deployment uses a PVC to bind itself to the PV, ensuring its database files persist permanently even if the Postgres pod crashes or restarts!

---

## 2. Step-by-Step Deployment on EC2

### Prerequisites
1. An AWS EC2 instance (Ubuntu 22.04 LTS is recommended, e.g., `t3.medium`).
2. SSH access to the instance.
3. Security Group rules allowing inbound traffic on ports `80` (HTTP) and `443` (HTTPS) from the internet, and `22` (SSH) for your IP.

### Step 1: Install k3s (Lightweight Kubernetes)
`k3s` is a highly available, certified Kubernetes distribution designed for production workloads in unattended, resource-constrained, remote locations or inside IoT appliances. It's perfect for a single EC2 node.
Connect to your EC2 instance and run:
```bash
# We use '--disable traefik' because K3s comes with Traefik out of the box, 
# and it would clash with the NGINX Ingress controller we want to use!
curl -sfL https://get.k3s.io | sh -s - --disable traefik
```
Verify it's running:
```bash
sudo k3s kubectl get nodes
```

### Step 2: Install NGINX Ingress Controller
Now we can install NGINX unobstructed:
```bash
sudo k3s kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml
```

### Step 3: Copy Your Manifests
Copy the `kubernetes/` directory from your local machine to your EC2 instance. You can use tools like `scp` from your local terminal:
```bash
scp -i your-key.pem -r kubernetes/ ubuntu@your-ec2-ip:~/kubernetes
```

### Step 4: Apply the Manifests
On your EC2 instance, navigate to where you copied the files and apply them in exactly this order:
```bash
cd kubernetes

# 1. Apply Configuration and State
sudo k3s kubectl apply -f 01-postgres-secret.yml
sudo k3s kubectl apply -f 02-configmap.yml
sudo k3s kubectl apply -f 03-postgres-storage.yml

# 2. Apply Database
sudo k3s kubectl apply -f 04-postgres-deployment.yml

# 3. Apply Microservices
sudo k3s kubectl apply -f 05-microservice-1.yml
sudo k3s kubectl apply -f 06-microservice-2.yml
sudo k3s kubectl apply -f 07-microservice-3.yml

# 4. Apply Autoscaling and Ingress
sudo k3s kubectl apply -f 08-hpa.yml
sudo k3s kubectl apply -f 09-ingress.yml
```

### Step 5: Verify Deployment
Check that everything is running smoothly:
```bash
# Check Pods
sudo k3s kubectl get pods -w

# Check Services
sudo k3s kubectl get svc

# Check Ingress
sudo k3s kubectl get ingress
```

Because of our specific Ingress configuration, you can now hit your microservices from the public IP of your EC2 instance through the paths `/ms1/`, `/ms2/`, and `/ms3/`!

Delete them in case of any issues:

```bash
sudo k3s kubectl delete pvc postgres-pvc
sudo k3s kubectl delete pv postgres-pv

sudo k3s kubectl delete ingress microservices-ingress

sudo k3s kubectl delete hpa microservice-1-hpa
sudo k3s kubectl delete hpa microservice-2-hpa
sudo k3s kubectl delete hpa microservice-3-hpa

sudo k3s kubectl delete deployment microservice-1
sudo k3s kubectl delete deployment microservice-2
sudo k3s kubectl delete deployment microservice-3

sudo k3s kubectl delete service microservice-1
sudo k3s kubectl delete service microservice-2
sudo k3s kubectl delete service microservice-3

sudo k3s kubectl delete deployment postgres
sudo k3s kubectl delete service postgres

sudo k3s kubectl delete configmap microservice-config
sudo k3s kubectl delete secret postgres-secret
```

### Helpful Debugging & Manipulation Commands
If you run into issues, you can investigate individual Pods or forcefully delete them using these commands:
```bash
# Get the exact names of all running pods
sudo k3s kubectl get pods

# Read the live application logs of a specific pod (like Docker logs)
sudo k3s kubectl logs <pod-name-from-previous-command>

# Stream the logs continuously
sudo k3s kubectl logs -f <pod-name-from-previous-command>

# Get detailed diagnostic events and status for a crashing pod
sudo k3s kubectl describe pod <pod-name-from-previous-command>

# Delete a specific, individual pod (Kubernetes will automatically spawn a new one to replace it!)
sudo k3s kubectl delete pod <pod-name-from-previous-command>
```
