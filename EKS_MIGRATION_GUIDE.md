# Amazon EKS Migration Guide

Once you are comfortable with running your self-managed Kubernetes cluster on a single EC2 instance using K3s, you might want to upgrade to a production-grade, highly available managed cluster using Amazon EKS (Elastic Kubernetes Service).

This guide outlines the architectural concepts of EKS and provides step-by-step instructions on migrating your microservices to it.

## 1. How EKS Architecture Works

When moving from a single EC2 instance to EKS, you are moving from a single server to a distributed cluster of servers managed by AWS. 

### The Control Plane (Master Nodes)
In a self-managed cluster, you have to maintain the "brain" of Kubernetes (the Master Nodes) yourself. In EKS, AWS takes over this entirely. AWS invisibly spins up multiple highly-available Master Nodes across different Availability Zones (AZs). You never see these EC2 instances in your AWS Console, and you don't have to patch or upgrade them. AWS manages the scaling and health of the Control Plane completely.

### The Data Plane (Worker Nodes)
Instead of running everything on one machine, EKS spins up separate EC2 instances called **Worker Nodes**. These *do* appear in your AWS Console. 
- Your microservices (Pods) live on these Worker Nodes. 
- If we configure EKS to run 2 Worker Nodes, Kubernetes will automatically distribute your `microservice-1`, `microservice-2`, and `microservice-3` pods across both of those EC2 instances. 
- If one EC2 instance crashes, AWS Auto Scaling instantly provisions a new one, and Kubernetes reschedules the lost pods onto healthy nodes.

### AWS Application Load Balancer (ALB)
In your single-node setup, you used an NGINX Ingress Controller. In EKS, the best practice is to use an **AWS Application Load Balancer (ALB)**.
1. When you define an Ingress resource in EKS (using the AWS Load Balancer Controller), AWS physically provisions a real ALB outside of your EC2 worker nodes.
2. The ALB acts as the front door for all internet traffic (e.g., `http://your-domain.com/ms1/hello`).
3. The ALB knows exactly which EC2 Worker Nodes currently hold healthy microservice pods.
4. It automatically and evenly load-balances incoming traffic across all the EC2 Worker Nodes running your application!

---

## 2. Manifest Updates Required for EKS

### Storage: EBS instead of Local Disk
In your single EC2 setup (`03-postgres-storage.yml`), you used a `hostPath` volume. This means database files were saved directly to that specific EC2's hard drive. In EKS, Pods move between different worker nodes, so you must use a network-attached AWS Elastic Block Store (EBS) volume.

**What you change:** Delete your existing `03-postgres-storage.yml` and replace it with a dynamically provisioned EBS volume using a StorageClass.
```yaml
# Updated 03-postgres-storage.yml for EKS
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: ebs-sc # Uses AWS EBS integration
  resources:
    requests:
      storage: 5Gi
```
*(Notice: You don't need a PersistentVolume (PV) file anymore. AWS dynamically provisions the EBS volume when the PVC is created).*

### Ingress: ALB instead of NGINX
You must update your `09-ingress.yml` file to tell AWS to provision an ALB instead of using NGINX.

**What you change:** Update the annotations and `ingressClassName` in `09-ingress.yml`:
```yaml
# Updated 09-ingress.yml for EKS ALB
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: microservices-ingress
  annotations:
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
spec:
  ingressClassName: alb # Triggers the AWS ALB Controller
  rules:
    - http:
        paths:
          - path: /ms1
            pathType: Prefix
            backend:
              service:
                name: microservice-1 
                port:
                  number: 8081
          # ... (repeat for ms2 and ms3)
```

---

## 3. Step-by-Step EKS Deployment

### Step 1: Install `eksctl` and `aws-cli`
Install `eksctl` (the official CLI tool for EKS) on your local machine and ensure your AWS CLI is authenticated with Admin credentials.

### Step 2: Create the EKS Cluster and Worker Nodes
Run this command to spin up the managed Master Nodes and 2 EC2 Worker Nodes (takes ~15-20 minutes):
```bash
eksctl create cluster \
  --name microservices-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed
```

### Step 3: Install the AWS EBS CSI Driver (not use here)
This allows EKS to provision EBS volumes for your Postgres database:
```bash
eksctl create iamserviceaccount \
  --name ebs-csi-controller-sa \
  --namespace kube-system \
  --cluster microservices-cluster \
  --attach-policy-arn arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy \
  --approve \
  --role-only \
  --role-name AmazonEKS_EBS_CSI_DriverRole

eksctl create addon \
  --name aws-ebs-csi-driver \
  --cluster microservices-cluster \
  --service-account-role-arn arn:aws:iam::<aws_account_id>:role/AmazonEKS_EBS_CSI_DriverRole \
  --force
```

### Step 4: Install the AWS Load Balancer Controller
This controller watches for your `ingressClassName: alb` and provisions the actual AWS ALB. Run the following commands to install it using Helm:

**1. Associate IAM OIDC provider** (Allows Kubernetes Pods to authenticate with AWS):
```bash
eksctl utils associate-iam-oidc-provider \
  --region us-east-1 \
  --cluster microservices-cluster \
  --approve
```

**2. Create the IAM Service Account** (Downloads the AWS policy and binds it to a Kubernetes service account):
```bash
curl -o iam-policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/main/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam-policy.json

# NOTE: Replace <YOUR_AWS_ACCOUNT_ID> with your actual 12-digit AWS account ID below!
eksctl create iamserviceaccount \
  --cluster microservices-cluster \
  --namespace kube-system \
  --name aws-load-balancer-controller \
  --attach-policy-arn arn:aws:iam::<YOUR_AWS_ACCOUNT_ID>:policy/AWSLoadBalancerControllerIAMPolicy \
  --override-existing-serviceaccounts \
  --approve
```

**3. Install the controller using Helm:**
```bash
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=microservices-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 5: Deploy Your Updated Manifests
1. Ensure `03-postgres-storage.yml` and `09-ingress.yml` are updated for EKS as shown above.
2. Apply your manifests!
   ```bash
   kubectl apply -f EKS/
   ```

### Step 6: Access Your Application
Run the following command to get the DNS name of the ALB that AWS just provisioned for you:
```bash
kubectl get ingress microservices-ingress
```
Look under the `ADDRESS` column to find your load balancer URL (e.g., `k8s-default-microser...elb.amazonaws.com`). You can now access your balanced microservices via this URL!

---

## 4. 🚨 Cleanup & Teardown (Stop Billing) 🚨

When you are done testing your EKS cluster, **you must delete it properly to avoid ongoing AWS charges** for the Control Plane (~$73/month), the EC2 Worker Nodes, and the Load Balancers.

Do **NOT** just delete the EC2 instances from the AWS Console. If you do, the EKS Autoscaler will immediately spin up new ones! You must let `eksctl` handle the deletion.

### Step 1: Delete Kubernetes Resources First
First, you need to tell Kubernetes to delete the Ingress resource, which gracefully prompts AWS to destroy the Application Load Balancer:
```bash
kubectl delete -f EKS/
```
*Wait a few minutes to ensure the Load Balancer disappears from your AWS EC2 Console under the "Load Balancers" tab before proceeding.*

### Step 2: Delete the Entire EKS Cluster
Run the official command to safely dismantle the entire EKS architecture. This command will systematically destroy the EC2 Worker Nodes, the Node Groups, the vast networking infrastructure (VPCs, Subnets), and finally the EKS Control Plane itself.
```bash
eksctl delete cluster --name microservices-cluster --region us-east-1
```
*(This process can take 10-15 minutes. Wait for it to confirm successful deletion.)*

### Step 3: Verify in AWS Console
To be 100% sure you will not be billed:
1. Go to **AWS EKS Console**: Ensure `microservices-cluster` is gone.
2. Go to **AWS EC2 Console**: Ensure no `t3.medium` instances are running.
3. Go to **EC2 -> Load Balancers**: Ensure the ALB is gone.
4. Go to **EC2 -> Volumes**: Ensure your 5Gi Postgres EBS volume is deleted (sometimes persistent volumes stick around depending on Reclaim Policy). Delete it manually if it is still "Available".
