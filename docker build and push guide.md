# Guide: Building and Pushing Docker Images

This guide provides the terminal commands to manually build your Docker images for `microservice-1`, `microservice-2`, and `microservice-3` and push them to both **Docker Hub** and **Amazon ECR**.

---

## 1. Building the Docker Images

Navigate to the root directory of your project where your microservice folders are located, and run the following commands to build the images.

```bash
# Build microservice-1
docker build -t microservice-1:latest ./microservice-1

# Build microservice-2
docker build -t microservice-2:latest ./microservice-2

# Build microservice-3
docker build -t microservice-3:latest ./microservice-3
```

---

## 2. Pushing to Docker Hub

To push your images to Docker Hub, you need to tag them with your Docker Hub username.

**Prerequisites:**
*   You must have a Docker Hub account.
*   You need to authenticate your Docker CLI with Docker Hub. Run the following command and enter your credentials:
    ```bash
    docker login
    ```

**Commands:**

```bash
# NOTE: Replace <your-dockerhub-username> with your actual Docker Hub username

# 1. Tag the local images for Docker Hub
docker tag microservice-1:latest <your-dockerhub-username>/microservice-1:latest
docker tag microservice-2:latest <your-dockerhub-username>/microservice-2:latest
docker tag microservice-3:latest <your-dockerhub-username>/microservice-3:latest

# 2. Push the tagged images to Docker Hub
docker push <your-dockerhub-username>/microservice-1:latest
docker push <your-dockerhub-username>/microservice-2:latest
docker push <your-dockerhub-username>/microservice-3:latest
```

---

## 3. Pushing to Amazon ECR

To push to Amazon ECR, you need to tag your images with your ECR registry URI. This assumes you have already created the completely empty private repositories in ECR named `microservice-1`, `microservice-2`, and `microservice-3`.

**Prerequisites:**
*   The AWS CLI must be installed and configured with your credentials using `aws configure`.

**Variables to replace:**
*   `<aws_account_id>`: Your 12-digit AWS account ID.
*   `<aws_region>`: The AWS region of your ECR (e.g., `us-east-1` or `ap-south-1`).

**Commands:**

```bash
# 1. Authenticate your local Docker client to your Amazon ECR registry
aws ecr get-login-password --region <aws_region> | docker login --username AWS --password-stdin <aws_account_id>.dkr.ecr.<aws_region>.amazonaws.com

# 2. Tag the local images with the remote ECR repository URI
docker tag microservice-1:latest <aws_account_id>.dkr.ecr.<aws_region>.amazonaws.com/microservice-1:latest
docker tag microservice-2:latest <aws_account_id>.dkr.ecr.<aws_region>.amazonaws.com/microservice-2:latest
docker tag microservice-3:latest <aws_account_id>.dkr.ecr.<aws_region>.amazonaws.com/microservice-3:latest

# 3. Push the tagged images to Amazon ECR
docker push <aws_account_id>.dkr.ecr.<aws_region>.amazonaws.com/microservice-1:latest
docker push <aws_account_id>.dkr.ecr.<aws_region>.amazonaws.com/microservice-2:latest
docker push <aws_account_id>.dkr.ecr.<aws_region>.amazonaws.com/microservice-3:latest
```
