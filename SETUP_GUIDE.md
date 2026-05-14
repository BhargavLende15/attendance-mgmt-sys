# Attendance Management System — Complete CI/CD Setup Guide

> **Port used by the app: 8086** (not 8080-8085 or 8090)
> Jenkins runs on **8080** as standard.

---

## TABLE OF CONTENTS

1. [Project Structure](#1-project-structure)
2. [Prerequisites](#2-prerequisites)
3. [Local Development & Testing](#3-local-development--testing)
4. [GitHub Repository Setup](#4-github-repository-setup)
5. [AWS Setup (ECR + EC2 + ALB)](#5-aws-setup)
6. [Jenkins Installation & Configuration](#6-jenkins-installation--configuration)
7. [Phase 1 — Build Pipeline (CI)](#7-phase-1--build-pipeline-ci)
8. [Phase 2 — Deploy Pipeline (CD)](#8-phase-2--deploy-pipeline-cd)
9. [Phase 3 — High Availability with ALB](#9-phase-3--high-availability-with-alb)
10. [Frontend UI Setup](#10-frontend-ui-setup)
11. [Testing the Complete Flow](#11-testing-the-complete-flow)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. PROJECT STRUCTURE

```
attendance-management-system/
├── src/
│   ├── main/
│   │   ├── java/com/attendance/
│   │   │   ├── AttendanceManagementApplication.java
│   │   │   ├── controller/AttendanceController.java
│   │   │   ├── model/AttendanceRecord.java
│   │   │   ├── model/CheckInRequest.java
│   │   │   └── service/
│   │   │       ├── AttendanceService.java
│   │   │       └── AttendanceRepository.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/java/com/attendance/
│       └── AttendanceManagementApplicationTests.java
├── jenkins/
│   ├── Jenkinsfile.build     ← CI pipeline
│   └── Jenkinsfile.deploy    ← CD pipeline
├── scripts/
│   ├── deploy.sh             ← runs on EC2
│   └── ec2-setup.sh          ← EC2 first-time setup
├── docker/
│   └── nginx.conf
├── frontend/
│   └── index.html            ← UI
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## 2. PREREQUISITES

### On your local machine:
- Java 17 (JDK): https://adoptium.net/
- Maven 3.9+: https://maven.apache.org/download.cgi
- Docker Desktop: https://www.docker.com/products/docker-desktop
- Git: https://git-scm.com/
- AWS CLI v2: https://aws.amazon.com/cli/
- A GitHub account

### AWS:
- AWS account with IAM access
- Region: e.g. `us-east-1` (pick one and be consistent)

---

## 3. LOCAL DEVELOPMENT & TESTING

### Step 3.1 — Run locally with Maven

```bash
cd attendance-management-system
mvn clean test                          # run tests
mvn spring-boot:run                     # start on port 8086
```

### Step 3.2 — Test the endpoints

```bash
# Health check
curl http://localhost:8086/attendance/status

# Check in an employee
curl -X POST http://localhost:8086/attendance/checkin \
  -H "Content-Type: application/json" \
  -d '{"employeeId":"EMP001","employeeName":"Alice Johnson","department":"Engineering"}'

# Check out
curl -X POST http://localhost:8086/attendance/checkout/EMP001

# All records
curl http://localhost:8086/attendance/records

# Dashboard stats
curl http://localhost:8086/attendance/dashboard
```

### Step 3.3 — Run with Docker locally

```bash
docker build -t attendance-app .
docker run -p 8086:8086 attendance-app
```

### Step 3.4 — Open the UI

Open `frontend/index.html` in your browser.
Click **Configure** (top right) → set API URL to `http://localhost:8086` → Save.

---

## 4. GITHUB REPOSITORY SETUP

### Step 4.1 — Create repository

1. Go to https://github.com/new
2. Repository name: `attendance-management-system`
3. Set to **Private** or Public
4. Click **Create repository**

### Step 4.2 — Push your code

```bash
cd attendance-management-system
git init
git add .
git commit -m "feat: initial attendance management system"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/attendance-management-system.git
git push -u origin main
```

### Step 4.3 — Generate GitHub Personal Access Token (for webhook)

1. GitHub → top-right avatar → **Settings**
2. Left sidebar → **Developer settings** → **Personal access tokens** → **Tokens (classic)**
3. Click **Generate new token (classic)**
4. Name: `jenkins-webhook`
5. Expiration: 90 days
6. Scopes: check ✅ `repo`, ✅ `admin:repo_hook`
7. Click **Generate token**
8. **COPY THE TOKEN** (shown only once!) — save it securely

---

## 5. AWS SETUP

### Step 5.1 — Create IAM User for Jenkins

1. Go to **AWS Console** → search "IAM" → click **IAM**
2. Left sidebar → **Users** → **Create user**
3. User name: `jenkins-cicd`
4. Click **Next**
5. Select **Attach policies directly**
6. Search and add these policies:
   - `AmazonEC2ContainerRegistryFullAccess`
   - `AmazonEC2FullAccess` (or a restricted version)
7. Click **Next** → **Create user**
8. Click on the user → **Security credentials** tab
9. **Create access key** → Use case: **Application running outside AWS**
10. Click **Next** → **Create access key**
11. **SAVE both Access Key ID and Secret Access Key**

### Step 5.2 — Create ECR Repository

1. AWS Console → search "ECR" → **Elastic Container Registry**
2. Click **Create repository**
3. Visibility: **Private**
4. Repository name: `attendance-management-system`
5. Image scan settings: ✅ **Scan on push**
6. Click **Create repository**
7. **Note the URI**: `123456789.dkr.ecr.us-east-1.amazonaws.com/attendance-management-system`
   (your Account ID and region will differ)

### Step 5.3 — Configure AWS CLI locally

```bash
aws configure
# AWS Access Key ID: [paste your key]
# AWS Secret Access Key: [paste your secret]
# Default region name: us-east-1   (or your chosen region)
# Default output format: json
```

### Step 5.4 — Create EC2 Key Pair

1. AWS Console → **EC2** → Left sidebar → **Network & Security** → **Key Pairs**
2. Click **Create key pair**
3. Name: `attendance-ec2-key`
4. Key pair type: **RSA**
5. Private key file format: **.pem**
6. Click **Create key pair**
7. The `.pem` file downloads automatically — **save it securely**
8. Set permissions: `chmod 400 attendance-ec2-key.pem`

### Step 5.5 — Create Security Group for EC2

1. EC2 → Left sidebar → **Network & Security** → **Security Groups**
2. Click **Create security group**
3. Name: `attendance-app-sg`
4. Description: `Security group for Attendance Management System`
5. VPC: select your default VPC
6. **Inbound rules** — click **Add rule** for each:
   | Type       | Protocol | Port | Source     |
   |------------|----------|------|------------|
   | SSH        | TCP      | 22   | My IP      |
   | Custom TCP | TCP      | 8086 | 0.0.0.0/0  |
   | HTTP       | TCP      | 80   | 0.0.0.0/0  |
7. Click **Create security group**

### Step 5.6 — Launch Two EC2 Instances

**Repeat this process TWICE** (for instance-1 and instance-2):

1. EC2 → **Instances** → **Launch instances**
2. Name: `attendance-instance-1` (or `attendance-instance-2`)
3. **Application and OS Images**: Amazon Linux 2023
4. **Instance type**: `t2.micro` (free tier) or `t3.small`
5. **Key pair**: `attendance-ec2-key`
6. **Network settings**: Edit → select your VPC
7. **Firewall**: Select existing security group → `attendance-app-sg`
8. **Advanced details** → **IAM instance profile**:
   - Create a new role with `AmazonEC2ContainerRegistryReadOnly` if you want instances to pull from ECR via IAM role (optional, our deploy script uses credentials)
9. Click **Launch instance**
10. **Note the Public IPv4 address** of each instance

### Step 5.7 — Set Up EC2 Instances

SSH into **each** instance and run the setup script:

```bash
# Instance 1
ssh -i attendance-ec2-key.pem ec2-user@<INSTANCE_1_PUBLIC_IP>

# Once inside, run:
sudo yum update -y
sudo yum install -y docker git
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user

# Install AWS CLI
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
rm -rf aws awscliv2.zip

# Verify
docker --version
aws --version

# Exit and reconnect (so docker group takes effect)
exit
ssh -i attendance-ec2-key.pem ec2-user@<INSTANCE_1_PUBLIC_IP>
```

Repeat for Instance 2.

---

## 6. JENKINS INSTALLATION & CONFIGURATION

### Step 6.1 — Install Jenkins

**Option A: On a separate EC2 instance (recommended for production)**

Launch a t2.medium EC2 instance, then:

```bash
ssh -i attendance-ec2-key.pem ec2-user@<JENKINS_IP>

# Install Java 17
sudo yum install -y java-17-amazon-corretto

# Add Jenkins repo
sudo wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key

# Install Jenkins
sudo yum upgrade -y
sudo yum install -y jenkins

# Start Jenkins
sudo systemctl start jenkins
sudo systemctl enable jenkins

# Open port 8080 in EC2 security group, then:
echo "Jenkins initial password:"
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

**Option B: On your local machine (for learning/development)**

Download Jenkins WAR: https://www.jenkins.io/download/

```bash
java -jar jenkins.war --httpPort=8080
```

### Step 6.2 — Initial Jenkins Setup

1. Open browser → `http://localhost:8080` (or `http://<JENKINS_IP>:8080`)
2. Enter the initial admin password (from the output above)
3. Click **Install suggested plugins**
4. Create admin user:
   - Username: `admin`
   - Password: (choose a strong password)
   - Full name: Your Name
   - Email: your@email.com
5. Click **Save and Continue** → **Save and Finish** → **Start using Jenkins**

### Step 6.3 — Install Required Jenkins Plugins

1. Jenkins dashboard → **Manage Jenkins** (left sidebar)
2. Click **Plugins**
3. Click **Available plugins** tab
4. Search and install each (check the checkbox then click **Install**):
   - `GitHub Integration Plugin`
   - `GitHub plugin`
   - `Pipeline`
   - `Pipeline: GitHub Groovy Libraries`
   - `Amazon ECR plugin`
   - `Amazon Web Services SDK`
   - `Email Extension Plugin` (Email Ext)
   - `Build User Vars Plugin`
   - `SSH Agent Plugin`
   - `Workspace Cleanup Plugin`
   - `JaCoCo plugin`
5. After installing, click **Restart Jenkins when no jobs are running**

### Step 6.4 — Configure Global Tools

1. **Manage Jenkins** → **Tools**
2. **JDK installations** → **Add JDK**:
   - Name: `JDK-17`
   - Uncheck "Install automatically"
   - JAVA_HOME: `/usr/lib/jvm/java-17-amazon-corretto` (or your JDK path)
   - On Mac: `/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`
   - On Linux: run `dirname $(dirname $(readlink -f $(which java)))`
3. **Maven installations** → **Add Maven**:
   - Name: `Maven-3.9`
   - Check ✅ **Install automatically**
   - Version: `3.9.5`
4. Click **Save**

### Step 6.5 — Add Jenkins Credentials

1. **Manage Jenkins** → **Credentials** → **System** → **Global credentials** → **Add Credentials**

**Credential 1: AWS Credentials**
- Kind: `AWS Credentials`
- ID: `aws-credentials`
- Description: `AWS Access for ECR and EC2`
- Access Key ID: (paste from Step 5.1)
- Secret Access Key: (paste from Step 5.1)
- Click **Create**

**Credential 2: EC2 SSH Key**
- Kind: `SSH Username with private key`
- ID: `ec2-ssh-key`
- Description: `EC2 SSH Private Key`
- Username: `ec2-user`
- Private Key: select **Enter directly** → click **Add** → paste contents of `attendance-ec2-key.pem`
- Click **Create**

**Credential 3: GitHub Token**
- Kind: `Secret text`
- ID: `github-token`
- Description: `GitHub Personal Access Token`
- Secret: (paste token from Step 4.3)
- Click **Create**

### Step 6.6 — Configure Jenkins System Settings

1. **Manage Jenkins** → **System**
2. **Jenkins URL**: Set to `http://<YOUR_JENKINS_IP>:8080/`
3. **GitHub** section → **Add GitHub Server**:
   - Name: `GitHub`
   - API URL: `https://api.github.com`
   - Credentials: select `github-token`
   - Click **Test connection** — should say "Credentials verified"
4. **Extended E-mail Notification** section:
   - SMTP server: `smtp.gmail.com`
   - SMTP Port: `465`
   - Click **Advanced** → check **Use SSL**
   - Username: `your.email@gmail.com`
   - Password: (Gmail App Password — see below)
5. **E-mail Notification** (basic):
   - SMTP server: `smtp.gmail.com`
   - Same settings as above
6. Click **Save**

**To create Gmail App Password:**
1. Gmail → Google Account → Security
2. Enable 2-Step Verification first
3. Security → **App passwords** → Select app: Mail, device: Other (Jenkins) → Generate
4. Copy the 16-character password

### Step 6.7 — Configure Jenkins Environment Variables

1. **Manage Jenkins** → **System**
2. Scroll to **Global properties** → check **Environment variables**
3. Click **Add** for each:

| Name | Value |
|------|-------|
| `AWS_ACCOUNT_ID` | `123456789012` (your 12-digit account ID) |
| `AWS_REGION` | `us-east-1` |
| `EC2_INSTANCE_1_IP` | `<Instance 1 public IP>` |
| `EC2_INSTANCE_2_IP` | `<Instance 2 public IP>` |
| `ALB_DNS_NAME` | `(fill in after Step 9)` |

4. Click **Save**

---

## 7. PHASE 1 — BUILD PIPELINE (CI)

### Step 7.1 — Create the CI Job

1. Jenkins dashboard → **New Item**
2. Enter name: `attendance-ci-build`
3. Select **Pipeline**
4. Click **OK**

### Step 7.2 — Configure CI Job

Under the job configuration page:

**General:**
- Description: `CI Pipeline – Build and push Docker image to ECR`
- Check ✅ **GitHub project**
- Project URL: `https://github.com/YOUR_USERNAME/attendance-management-system`

**Build Triggers:**
- Check ✅ **GitHub hook trigger for GITScm polling**

**Pipeline:**
- Definition: `Pipeline script from SCM`
- SCM: `Git`
- Repository URL: `https://github.com/YOUR_USERNAME/attendance-management-system.git`
- Credentials: `github-token`
- Branch Specifier: `*/main`
- Script Path: `jenkins/Jenkinsfile.build`

Click **Save**

### Step 7.3 — Set Up GitHub Webhook

1. Go to your GitHub repo → **Settings** (tab at top)
2. Left sidebar → **Webhooks** → **Add webhook**
3. Payload URL: `http://<JENKINS_IP>:8080/github-webhook/`
   - ⚠️ Must be publicly accessible. If Jenkins is local, use ngrok:
   ```bash
   # Install ngrok: https://ngrok.com/download
   ngrok http 8080
   # Use the https://xxxxx.ngrok.io URL as your Payload URL
   ```
4. Content type: `application/json`
5. Secret: (leave blank or set a secret and add to Jenkins)
6. Which events: select **Just the push event**
7. Check ✅ **Active**
8. Click **Add webhook**
9. GitHub will show a green tick ✅ when it successfully pings Jenkins

### Step 7.4 — Run First CI Build

1. Go to `attendance-ci-build` job
2. Click **Build Now** to test manually
3. Click the build number → **Console Output**
4. Watch for each stage to pass ✅

**Expected output:**
```
[Checkout] Checking out source code from GitHub...
[Test] Running unit tests with Maven...
  Tests run: 8, Failures: 0, Errors: 0
[Build] Building Spring Boot JAR...
[Docker Build] Building Docker image...
[Push to ECR] Authenticating with AWS ECR...
  Successfully pushed image: 123456789.dkr.ecr.us-east-1.amazonaws.com/attendance-management-system:1
[Cleanup] Cleaning up local Docker images...
BUILD SUCCESS
Email sent to: your@email.com
```

### Step 7.5 — Verify in ECR

1. AWS Console → **ECR**
2. Click `attendance-management-system` repository
3. You should see image with tag `1` (or whichever build number ran)
4. ✅ Screenshot this for your submission

---

## 8. PHASE 2 — DEPLOY PIPELINE (CD)

### Step 8.1 — Create the CD Job

1. Jenkins dashboard → **New Item**
2. Name: `attendance-cd-deploy`
3. Select **Pipeline**
4. Click **OK**

### Step 8.2 — Configure CD Job

**General:**
- Description: `CD Pipeline – Deploy to EC2 instances`
- Check ✅ **This project is parameterized**

The parameters are already defined in `Jenkinsfile.deploy` so Jenkins will auto-detect them when first run.

**Pipeline:**
- Definition: `Pipeline script from SCM`
- SCM: `Git`
- Repository URL: `https://github.com/YOUR_USERNAME/attendance-management-system.git`
- Credentials: `github-token`
- Branch Specifier: `*/main`
- Script Path: `jenkins/Jenkinsfile.deploy`

Click **Save**

### Step 8.3 — Run First Deploy

1. Click **Build with Parameters**
2. ENVIRONMENT: `Staging`
3. BUILD_NUMBER_TO_DEPLOY: `1` (or whichever build number you pushed in Phase 1)
4. ROLLING_UPDATE: ✅ checked
5. Click **Build**

Watch Console Output. Successful output looks like:
```
Verifying image exists in ECR... OK
Deploying to EC2 Instance 1 (x.x.x.x)...
  Pulling image... Done
  Container started successfully
  Instance 1 is HEALTHY (attempt 3)
Deploying to EC2 Instance 2 (x.x.x.x)...
  Instance 2 is HEALTHY (attempt 2)
Email sent: Deployment successful
```

### Step 8.4 — Verify Deployment

```bash
# Test instance 1 directly
curl http://<INSTANCE_1_IP>:8086/attendance/status

# Test instance 2 directly
curl http://<INSTANCE_2_IP>:8086/attendance/status

# Both should return:
# {"service":"Attendance Management System","status":"UP",...}
```

---

## 9. PHASE 3 — HIGH AVAILABILITY WITH ALB

### Step 9.1 — Create Target Group

1. AWS Console → **EC2** → Left sidebar → **Load Balancing** → **Target Groups**
2. Click **Create target group**
3. Target type: **Instances**
4. Target group name: `attendance-tg`
5. Protocol: **HTTP**
6. Port: **8086**
7. VPC: select your VPC
8. Health check settings:
   - Protocol: HTTP
   - Path: `/attendance/status`
   - Healthy threshold: `2`
   - Unhealthy threshold: `3`
   - Timeout: `5`
   - Interval: `30`
9. Click **Next**
10. Select both EC2 instances → click **Include as pending below**
11. Click **Create target group**

### Step 9.2 — Create Application Load Balancer

1. EC2 → **Load Balancers** → **Create load balancer**
2. Select **Application Load Balancer** → **Create**
3. Name: `attendance-alb`
4. Scheme: **Internet-facing**
5. IP address type: **IPv4**
6. Network mapping: select your VPC → check **all availability zones**
7. **Security groups**: Create new or use existing
   - Create new: `attendance-alb-sg`
   - Inbound: HTTP port 80 from 0.0.0.0/0
8. **Listeners and routing**:
   - Protocol: HTTP, Port: 80
   - Default action: Forward to `attendance-tg`
9. Click **Create load balancer**
10. Wait ~2 minutes for it to become **Active**
11. **Note the DNS name**: `attendance-alb-123456.us-east-1.elb.amazonaws.com`

### Step 9.3 — Update Jenkins Environment

1. **Manage Jenkins** → **System** → Environment variables
2. Update `ALB_DNS_NAME` to your actual ALB DNS name
3. Save

### Step 9.4 — Test Load Balancer

```bash
# Test via ALB (routes to either instance)
curl http://attendance-alb-XXXXX.us-east-1.elb.amazonaws.com/attendance/status

# Run multiple times to see different instanceId values
for i in {1..6}; do
  curl -s http://attendance-alb-XXXXX.us-east-1.elb.amazonaws.com/attendance/status | python3 -m json.tool | grep instanceId
done
```

You should alternately see `instance-1` and `instance-2`.

---

## 10. FRONTEND UI SETUP

### Step 10.1 — Serve frontend on EC2 (both instances)

```bash
# SSH into instance 1
ssh -i attendance-ec2-key.pem ec2-user@<INSTANCE_1_IP>

# Install nginx
sudo yum install -y nginx

# Create web directory
sudo mkdir -p /var/www/attendance

# Copy the frontend file (from your local machine in a NEW terminal):
# scp -i attendance-ec2-key.pem frontend/index.html ec2-user@<INSTANCE_1_IP>:/tmp/

# Back in EC2 terminal:
sudo cp /tmp/index.html /var/www/attendance/index.html

# Copy nginx config
# scp -i attendance-ec2-key.pem docker/nginx.conf ec2-user@<INSTANCE_1_IP>:/tmp/
sudo cp /tmp/nginx.conf /etc/nginx/nginx.conf

# Start nginx
sudo systemctl start nginx
sudo systemctl enable nginx
```

Repeat for Instance 2.

### Step 10.2 — Access the UI

- Via Instance 1: `http://<INSTANCE_1_IP>/`
- Via Instance 2: `http://<INSTANCE_2_IP>/`
- Via ALB (load balanced): `http://<ALB_DNS_NAME>/`

The UI auto-configures to use `/attendance/*` endpoints via nginx proxy.

### Step 10.3 — Local UI (Development)

Open `frontend/index.html` in your browser directly.
Click **Configure** button → set API URL to `http://localhost:8086` → Save.

---

## 11. TESTING THE COMPLETE FLOW

### End-to-End Test

```bash
# 1. Make a code change
echo "// trigger build $(date)" >> src/main/resources/application.properties
git add . && git commit -m "test: trigger CI/CD pipeline"
git push origin main

# 2. Watch Jenkins CI build trigger automatically (within seconds)
# → Jenkins receives webhook → runs tests → builds Docker → pushes to ECR

# 3. Run CD deploy
# → Jenkins → attendance-cd-deploy → Build with Parameters
# → ENVIRONMENT: Production, BUILD_NUMBER_TO_DEPLOY: <latest build>

# 4. Verify via ALB
curl http://<ALB_DNS>/attendance/status
```

### API Smoke Tests

```bash
BASE=http://<ALB_DNS>

# Health
curl $BASE/attendance/status

# Check in 3 employees
for emp in "EMP001:Alice:Engineering" "EMP002:Bob:Product" "EMP003:Carol:Design"; do
  IFS=: read id name dept <<< "$emp"
  curl -s -X POST $BASE/attendance/checkin \
    -H "Content-Type: application/json" \
    -d "{\"employeeId\":\"$id\",\"employeeName\":\"$name\",\"department\":\"$dept\"}"
  echo ""
done

# Dashboard
curl $BASE/attendance/dashboard

# Active employees
curl $BASE/attendance/active

# Check out
curl -X POST $BASE/attendance/checkout/EMP001
```

---

## 12. TROUBLESHOOTING

### Jenkins can't connect to GitHub
- Verify webhook URL is publicly accessible
- Check Manage Jenkins → System → GitHub → Test connection

### Docker build fails: "Cannot connect to Docker daemon"
- Jenkins user needs to be in the docker group:
  ```bash
  sudo usermod -aG docker jenkins
  sudo systemctl restart jenkins
  ```

### ECR push fails: "no basic auth credentials"
- Verify AWS credentials in Jenkins credentials store
- Ensure IAM user has `AmazonEC2ContainerRegistryFullAccess`

### SSH to EC2 fails in deploy pipeline
- Check the SSH key in Jenkins credentials (must be the .pem file content)
- Verify EC2 security group allows SSH (port 22) from Jenkins IP

### App not starting on EC2
```bash
# Check container logs
docker logs attendance-app

# Check if port is in use
sudo netstat -tlnp | grep 8086

# Restart container
docker restart attendance-app
```

### ALB health checks failing
- Ensure EC2 security group allows traffic from ALB security group on port 8086
- Check that the app is running: `curl http://localhost:8086/attendance/status`
- ALB health check path must be `/attendance/status`

### Email not sending
- Verify Gmail App Password is correct (not your regular password)
- Build User Vars Plugin must be installed for `BUILD_USER_EMAIL` to work
- Test with: Manage Jenkins → System → E-mail Notification → Test configuration

---

## SUBMISSION CHECKLIST

- [ ] GitHub repository with Spring Boot code + both Jenkinsfiles
- [ ] Screenshot: ECR repository showing pushed images with build number tags
- [ ] Screenshot: Both EC2 instances running (`docker ps` output)
- [ ] Screenshot: ALB Target Group showing both instances as **Healthy**
- [ ] Screenshot: Jenkins CI build console output (webhook trigger + stages)
- [ ] Screenshot: Jenkins CD build console output (deploy stages)
- [ ] Screenshot: Email notification received
- [ ] Screenshot: Frontend UI showing check-in/check-out working
- [ ] Screenshot: ALB DNS responding to `/attendance/status`

---

## QUICK REFERENCE — PORTS

| Service | Port | Notes |
|---------|------|-------|
| Spring Boot App | **8086** | Runs inside Docker on EC2 |
| Nginx (frontend proxy) | **80** | On EC2, proxies to 8086 |
| ALB | **80** | Internet-facing load balancer |
| Jenkins | **8080** | Standard Jenkins port |
| H2 Console | **8086/h2-console** | Dev only |

---

*Generated for Attendance Management System CI/CD Project*
*Spring Boot 3.2 · Java 17 · Jenkins · AWS ECR · EC2 · ALB*
