# GitHub Actions CI/CD Guide: Deploying to EKS via ECR

This guide provides step-by-step instructions on how to set up a GitHub Actions CI/CD pipeline to deploy your microservices (`microservice-1`, `microservice-2`, `microservice-3`) and a `postgresql` database to an Amazon Elastic Kubernetes Service (EKS) cluster running on EC2 instances. 

The pipeline flow is: **GitHub Actions -> Build Docker Images -> Push to Amazon ECR -> Deploy to Amazon EKS -> Runs on Amazon EC2**.

---

## Prerequisites

1.  **AWS Account:** You need an active AWS account.
2.  **ECR Repositories:** Create private ECR repositories for your microservices:
    *   `microservice-1`
    *   `microservice-2`
    *   `microservice-3`
3.  **EKS Cluster:** A running EKS cluster fully configured with nodes (EC2 instances).
4.  **IAM User/Role:** An IAM user with programmatic access that has policies attached for:
    *   `AmazonEC2ContainerRegistryPowerUser` (to push to ECR)
    *   `AmazonEKSClusterPolicy` (or sufficient RBAC permissions to deploy to the EKS cluster)

---

## 1. Configure GitHub Repository Secrets

To allow GitHub Actions to securely access your AWS environment, you must add the following **Secret** variables to your GitHub repository by navigating to **Settings > Secrets and variables > Actions**:

*   `AWS_ACCESS_KEY_ID`: Your IAM user access key.
*   `AWS_SECRET_ACCESS_KEY`: Your IAM user secret access key.
*   `AWS_REGION`: The AWS region where your ECR and EKS are located (e.g., `us-east-1`).
*   `EKS_CLUSTER_NAME`: The name of your EKS cluster.

---

## 2. Full Deployment Pipeline (m1, m2, m3, PostgreSQL)

To deploy the entire stack automatically when you push to the `main` branch, create a file in your repository at `.github/workflows/deploy-all.yml` with the following content:

```yaml
name: CI/CD Pipeline - ECR to EKS

on:
  push:
    branches:
      - main

jobs:
  build-and-deploy:
    name: Build, Push, and Deploy to EKS
    runs-on: ubuntu-latest

    steps:
    - name: Checkout Code
      uses: actions/checkout@v3

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ secrets.AWS_REGION }}

    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1

    - name: Build, tag, and push m1 image to ECR
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: microservice-1
        IMAGE_TAG: ${{ github.sha }}
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG ./microservice-1
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

    - name: Build, tag, and push m2 image to ECR
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: microservice-2
        IMAGE_TAG: ${{ github.sha }}
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG ./microservice-2
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

    - name: Build, tag, and push m3 image to ECR
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: microservice-3
        IMAGE_TAG: ${{ github.sha }}
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG ./microservice-3
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

    - name: Update KubeConfig for EKS
      run: aws eks update-kubeconfig --region ${{ secrets.AWS_REGION }} --name ${{ secrets.EKS_CLUSTER_NAME }}

    - name: Deploy PostgreSQL to EKS
      run: |
        kubectl apply -f kubernetes/postgres-deployment.yaml
        kubectl apply -f kubernetes/postgres-service.yaml

    - name: Deploy Microservices to EKS
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        IMAGE_TAG: ${{ github.sha }}
      run: |
        # Update image tags in Kubernetes standard manifests before deploying
        # Assumes you have standard k8s deployments in the 'kubernetes' folder
        
        kubectl set image deployment/microservice-1 microservice-1=$ECR_REGISTRY/microservice-1:$IMAGE_TAG
        kubectl set image deployment/microservice-2 microservice-2=$ECR_REGISTRY/microservice-2:$IMAGE_TAG
        kubectl set image deployment/microservice-3 microservice-3=$ECR_REGISTRY/microservice-3:$IMAGE_TAG
        
        # Apply the configurations
        kubectl apply -f kubernetes/
```

> **Note:** If `postgres` uses persistent volumes or needs initialization before services start, ensure your readiness probes in `m1`, `m2`, and `m3` deployments are configured so they wait for the database implicitly.

---

## 3. Deploying ONLY m1 and m2

If you want a pipeline that **exclusively** deploys `microservice-1` and `microservice-2`, you can create a dedicated workflow file e.g., `.github/workflows/deploy-m1-m2.yml` (triggered on a specific branch, manually, or on specific path changes). 

Here is how you adjust the workflow to target only `m1` and `m2`:

```yaml
name: Deploy m1 and m2 Only

on:
  workflow_dispatch: # Allows manual trigger from GitHub UI
  push:
    paths:
      - 'microservice-1/**'
      - 'microservice-2/**'
    branches:
      - main

jobs:
  deploy-m1-m2:
    runs-on: ubuntu-latest

    steps:
    - name: Checkout Code
      uses: actions/checkout@v3

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ secrets.AWS_REGION }}

    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1

    # --- Build and Push m1 ---
    - name: Build, tag, and push m1 image to ECR
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: microservice-1
        IMAGE_TAG: ${{ github.sha }}
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG ./microservice-1
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

    # --- Build and Push m2 ---
    - name: Build, tag, and push m2 image to ECR
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: microservice-2
        IMAGE_TAG: ${{ github.sha }}
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG ./microservice-2
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

    # --- Deploy to EKS ---
    - name: Update KubeConfig for EKS
      run: aws eks update-kubeconfig --region ${{ secrets.AWS_REGION }} --name ${{ secrets.EKS_CLUSTER_NAME }}

    - name: Deploy ONLY m1 and m2 to EKS
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        IMAGE_TAG: ${{ github.sha }}
      run: |
        # Dynamically set the new images for m1 and m2 deployments only
        kubectl set image deployment/microservice-1 microservice-1=$ECR_REGISTRY/microservice-1:$IMAGE_TAG
        kubectl set image deployment/microservice-2 microservice-2=$ECR_REGISTRY/microservice-2:$IMAGE_TAG
        
        # Apply specifically the m1 and m2 manifests
        kubectl apply -f kubernetes/m1-deployment.yaml
        kubectl apply -f kubernetes/m2-deployment.yaml
        
        # Or alternatively you can rely merely on "kubectl set image" if the deployments are already running, 
        # and it will automatically trigger a rolling update with the new images.
```

### Key Differences for "Only M1 & M2":
1.  **Triggers:** The `on` block uses `workflow_dispatch` for manual triggering, and `paths` so it automatically triggers only if files inside the `microservice-1` or `microservice-2` folders are modified.
2.  **Skipped Steps:** We omitted the build/push blocks for `m3` and omitted PostgreSQL deployment steps entirely.
3.  **Targeted kubectl Commands:** Only the `kubectl set image` commands and `kubectl apply` commands for `m1` and `m2` are executed, leaving PostgreSQL and `m3` completely untouched on the cluster.

---

## 4. Deploying m1 and m2 directly to EC2 (Without EKS)

If you are not using EKS and instead want to deploy `microservice-1` and `microservice-2` directly as Docker containers on a standalone EC2 instance, you need to use an SSH action in your GitHub Pipeline.

### Additional Prerequisites
Add the following secrets to **Settings > Secrets and variables > Actions**:
*   `EC2_HOST`: The public IP or DNS of your EC2 instance.
*   `EC2_USERNAME`: The SSH user (e.g., `ec2-user` for Amazon Linux, `ubuntu` for Ubuntu).
*   `EC2_SSH_KEY`: The private PEM key used to SSH into the instance. **(To get this: Open your `.pem` file in a text editor like Notepad or VS Code. Copy the ENTIRE content, including the `-----BEGIN RSA PRIVATE KEY-----` and `-----END RSA PRIVATE KEY-----` lines, and paste it exactly as is into the GitHub Secret value field.)**

> **Note on `steps.login-ecr.outputs.registry`:** You do **not** need to set this as a secret or configure it manually. This is an automatically generated output variable from the `aws-actions/amazon-ecr-login@v1` step. Once the action logs into AWS, it dynamically outputs your ECR Registry URL, which the subsequent steps use automatically.

### The Pipeline (`.github/workflows/deploy-m1-m2-ec2.yml`)

```yaml
name: Deploy m1 and m2 to EC2

on:
  workflow_dispatch:
  push:
    paths:
      - 'microservice-1/**'
      - 'microservice-2/**'
    branches:
      - main

jobs:
  deploy-to-ec2:
    runs-on: ubuntu-latest

    steps:
    - name: Checkout Code
      uses: actions/checkout@v3

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ secrets.AWS_REGION }}

    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1

    # --- Build and Push m1 ---
    - name: Build, tag, and push m1 image to ECR
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: microservice-1
        IMAGE_TAG: latest
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG ./microservice-1
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

    # --- Build and Push m2 ---
    - name: Build, tag, and push m2 image to ECR
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: microservice-2
        IMAGE_TAG: latest
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG ./microservice-2
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

    # --- Deploy to EC2 via SSH ---
    - name: Deploy to EC2
      uses: appleboy/ssh-action@v1.0.3
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        AWS_REGION: ${{ secrets.AWS_REGION }}
      with:
        host: ${{ secrets.EC2_HOST }}
        username: ${{ secrets.EC2_USERNAME }}
        key: ${{ secrets.EC2_SSH_KEY }}
        envs: ECR_REGISTRY,AWS_REGION
        script: |
          # Login to ECR on the EC2 instance
          aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REGISTRY
          
          # Pull latest images
          docker pull $ECR_REGISTRY/microservice-1:latest
          docker pull $ECR_REGISTRY/microservice-2:latest
          
          # Restart m1
          docker stop microservice-1 || true
          docker rm microservice-1 || true
          docker run -d --name microservice-1 -p 8081:8081 $ECR_REGISTRY/microservice-1:latest
          
          # Restart m2
          docker stop microservice-2 || true
          docker rm microservice-2 || true
          docker run -d --name microservice-2 -p 8082:8082 $ECR_REGISTRY/microservice-2:latest
```

### Key Differences for EC2 Direct Deployment:
1.  **Tagging:** We use `latest` (or `github.sha`) tag to easily identify the image on the EC2 instance.
2.  **SSH Action:** We use `appleboy/ssh-action` to remote into the EC2 instance securely.
3.  **Direct Docker Commands:** Instead of `kubectl apply`, we issue direct Docker commands (`stop`, `rm`, `run`) on the remote server over SSH.

---

## 5. Setting Up a Self-Hosted Runner (For Public Repos)

While GitHub provides free `ubuntu-latest` runners for public repositories, you might want to run the pipeline directly on your own EC2 instance (a "Self-Hosted Runner"). This allows the pipeline to execute commands directly on your server, without needing the SSH action or exposing SSH ports!

### WARNING for Public Repositories ⚠️
**Security Risk:** GitHub strongly advises against using self-hosted runners on public repositories. Because anyone can fork a public repo and open a Pull Request, a malicious user could submit a Pull Request containing malicious code that executes directly on your EC2 instance. 
*If you do this, ensure your workflow is configured to require approval for all outside contributors.* Used for private only then 

### Steps to Add a Self-Hosted Runner to Your EC2

1. **Go to GitHub runner settings:**
   - In your repository, go to **Settings** -> **Actions** -> **Runners**.
   - Click the green **New self-hosted runner** button.
   - Select **Linux** and your architecture (usually **x64** or **ARM64**).

2. **Run the installation commands on your EC2 instance:**
   SSH into your EC2 instance and run the exact commands GitHub displays on the screen. It usually looks like this:
   ```bash
   # Create a folder
   mkdir actions-runner && cd actions-runner
   
   # Download the latest runner package
   curl -o actions-runner-linux-x64-2.316.1.tar.gz -L https://github.com/actions/runner/releases/download/v2.316.1/actions-runner-linux-x64-2.316.1.tar.gz
   
   # Extract the installer
   tar xzf ./actions-runner-linux-x64-2.316.1.tar.gz
   
   # Configure it with your unique token (GitHub provides this exact command)
   ./config.sh --url https://github.com/YOUR_USERNAME/YOUR_REPO --token XXXXXXXXXXXXXXXXXXXXXX
   ```

3. **Install it as a background service:**
   To ensure the runner keeps listening even after you close your SSH session:
   ```bash
   sudo ./svc.sh install
   sudo ./svc.sh start
   ```

4. **Update your YAML Pipeline:**
   Now that your EC2 is listening for GitHub Actions, change your pipeline file (`.github/workflows/deploy-m1-m2.yml`).
   Find the `runs-on` line:
   ```yaml
   jobs:
     deploy-to-ec2:
       runs-on: ubuntu-latest  # <-- CHANGE THIS
   ```
   Modify it to use your self-hosted runner:
   ```yaml
   jobs:
     deploy-to-ec2:
       runs-on: self-hosted  # <-- NEW VALUE
   ```

Because the pipeline is now executing *directly* on the EC2 server, you no longer need the SSH Action step at the end of the pipeline. You can just run the `docker stop`, `docker rm`, and `docker run` commands directly in standard `run:` blocks!
