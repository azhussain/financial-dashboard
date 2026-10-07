# Stocks Explorer — AWS deployment runbook

Copy-paste CLI steps for the split deployment: **S3 + CloudFront** serving the React SPA
with **`/api/*` proxied to a Dockerized Spring Boot backend on EC2**. One CloudFront
domain fronts both, so the browser talks same-origin — no CORS, no `VITE_API_BASE`.

```
Browser ──HTTPS──▶ CloudFront (dxxxxxxxxxxx.cloudfront.net)
                     ├─ /        → S3 bucket (private, OAC)   — static SPA
                     └─ /api/*   → EC2 (HTTP:8080, no cache)  — Spring Boot
                                       ▲ pulls image from ECR, secrets from SSM
```

Prerequisites: AWS CLI v2 configured (`aws configure`), an account with admin or
equivalent rights, and Docker only if building images locally (CI builds them — see
`.github/workflows/deploy.yml`, so local Docker is optional).

Set once per shell:

```bash
export REGION=us-east-1
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export APP=stocks-explorer
```

---

## 1 · ECR repository

```bash
aws ecr create-repository --repository-name ${APP}-backend \
  --image-scanning-configuration scanOnPush=true --region $REGION
```

## 2 · Secret in SSM Parameter Store

The key is already in your Windows env (`setx` earlier). Store it as a SecureString —
it never enters the repo or the image:

```bash
# PowerShell:  aws ssm put-parameter --name /stocks-explorer/OPENAI_API_KEY --type SecureString --value "$env:OPENAI_API_KEY"
aws ssm put-parameter --name /${APP}/OPENAI_API_KEY --type SecureString \
  --value "<your-openai-key>" --region $REGION
```

## 3 · IAM — EC2 instance role

```bash
cat > /tmp/ec2-trust.json <<'EOF'
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}
EOF

aws iam create-role --role-name ${APP}-ec2-role \
  --assume-role-policy-document file:///tmp/ec2-trust.json
aws iam attach-role-policy --role-name ${APP}-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam attach-role-policy --role-name ${APP}-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly

cat > /tmp/ssm-read.json <<EOF
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":"ssm:GetParameter","Resource":"arn:aws:ssm:${REGION}:${ACCOUNT_ID}:parameter/${APP}/*"}]}
EOF
aws iam put-role-policy --role-name ${APP}-ec2-role \
  --policy-name read-app-params --policy-document file:///tmp/ssm-read.json

aws iam create-instance-profile --instance-profile-name ${APP}-ec2-profile
aws iam add-role-to-instance-profile --instance-profile-name ${APP}-ec2-profile \
  --role-name ${APP}-ec2-role
```

## 4 · EC2 instance

Security group — **8080 open only to CloudFront's origin-facing prefix list**, SSH to
your IP only (or omit SSH entirely and use SSM Session Manager):

```bash
SG_ID=$(aws ec2 create-security-group --group-name ${APP}-sg \
  --description "Stocks Explorer backend" --query GroupId --output text)

aws ec2 authorize-security-group-ingress --group-id $SG_ID \
  --ip-permissions 'IpProtocol=tcp,FromPort=8080,ToPort=8080,PrefixListIds=[{PrefixListId=pl-3b927c52,Description=CloudFront}]'
# pl-3b927c52 = com.amazonaws.global.cloudfront.origin-facing (us-east-1 managed list;
# verify with: aws ec2 describe-managed-prefix-lists --query "PrefixLists[?PrefixListName=='com.amazonaws.global.cloudfront.origin-facing']")

aws ec2 authorize-security-group-ingress --group-id $SG_ID \
  --ip-permissions "IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=$(curl -s https://checkip.amazonaws.com)/32,Description=my-ip}]"
```

Launch (AL2023 AMI resolved from SSM public params):

```bash
AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)

INSTANCE_ID=$(aws ec2 run-instances --image-id $AMI --instance-type t3.small \
  --iam-instance-profile Name=${APP}-ec2-profile --security-group-ids $SG_ID \
  --user-data file://deploy/ec2-userdata.sh \
  --tag-specifications "ResourceType=instance,Tags=[{Key=App,Value=${APP}},{Key=Name,Value=${APP}-api}]" \
  --query Instances[0].InstanceId --output text)

aws ec2 allocate-address --query AllocationId --output text   # → ALLOCATION_ID
aws ec2 associate-address --instance-id $INSTANCE_ID --allocation-id <ALLOCATION_ID>
aws ec2 describe-instances --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].PublicDnsName' --output text   # → EC2_DNS
```

The user-data script installs Docker, logs into ECR, pulls `OPENAI_API_KEY` from SSM,
and starts `stocks-api` with `--restart unless-stopped`. First boot needs an image in
ECR — push one now from your machine or let CI do it:

```bash
cd backend
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com
docker build -t ${APP}-backend .
docker tag ${APP}-backend:latest ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${APP}-backend:latest
docker push ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${APP}-backend:latest
# If the instance booted before the image existed, re-run the bootstrap:
aws ssm send-command --instance-ids $INSTANCE_ID --document-name AWS-RunShellScript \
  --parameters 'commands=["bash /var/lib/cloud/instance/user-data.txt"]'   # or re-run manually
```

Verify (from an allowed path — or just trust CloudFront; direct EIP access is SG-blocked
by design, so use SSM Session Manager: `aws ssm start-session --target $INSTANCE_ID`, then
`curl localhost:8080/api/health` → `{"status":"ok"}`).

## 5 · S3 bucket (private)

```bash
BUCKET=${APP}-frontend-${ACCOUNT_ID}
aws s3 mb s3://${BUCKET} --region $REGION
aws s3api put-public-access-block --bucket $BUCKET --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-versioning --bucket $BUCKET --versioning-configuration Status=Enabled

cd frontend && npm ci && npm run build          # VITE_API_BASE stays EMPTY (same-origin)
aws s3 sync dist/ s3://${BUCKET} --delete
```

## 6 · CloudFront distribution

```bash
OAC_ID=$(aws cloudfront create-origin-access-control --origin-access-control-config \
  "Name=${APP}-oac,SigningProtocol=sigv4,SigningBehavior=always,OriginAccessControlOriginType=s3" \
  --query 'OriginAccessControl.Id' --output text)
```

Create `dist-config.json` (template in this directory — substitute `${BUCKET}`,
`${OAC_ID}`, `${EC2_DNS}`), then:

```bash
DIST_ID=$(aws cloudfront create-distribution --distribution-config file://deploy/dist-config.json \
  --query 'Distribution.Id' --output text)
aws cloudfront get-distribution --id $DIST_ID \
  --query 'Distribution.DomainName' --output text                  # → dxxxx.cloudfront.net
```

Attach the OAC bucket policy:

```bash
cat > /tmp/bucket-policy.json <<EOF
{"Version":"2012-10-17","Statement":[{"Sid":"AllowCloudFront","Effect":"Allow",
"Principal":{"Service":"cloudfront.amazonaws.com"},"Action":"s3:GetObject",
"Resource":"arn:aws:s3:::${BUCKET}/*",
"Condition":{"StringEquals":{"AWS:SourceArn":"arn:aws:cloudfront::${ACCOUNT_ID}:distribution/${DIST_ID}"}}}]}
EOF
aws s3api put-bucket-policy --bucket $BUCKET --policy file:///tmp/bucket-policy.json
```

## 7 · Smoke tests

```bash
curl https://${DIST_DOMAIN}/api/health                          # {"status":"ok"}
curl -N --max-time 70 https://${DIST_DOMAIN}/api/markets/stream # snapshot event + heartbeat <60s
curl -X POST https://${DIST_DOMAIN}/api/markets/AMERICAS/refresh  # 202 (open hours)
```

Then open `https://${DIST_DOMAIN}` — watchlists should populate over SSE, refresh
buttons disable on closed markets, and the Virtual Agent should answer (SSM key check).

## 8 · CI/CD

`.github/workflows/deploy.yml` builds/tests/pushes on every push. One-time setup:

1. GitHub OIDC provider (skip if it already exists in the account):
   ```bash
   aws iam create-open-id-connect-provider --url https://token.actions.githubusercontent.com \
     --client-id-list sts.amazonaws.com --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
   ```
2. Deploy role `stocks-github-deploy` — trust policy in `deploy/github-oidc-trust.json`
   (substitute your repo + branch), permissions policy in `deploy/github-deploy-policy.json`:
   ```bash
   aws iam create-role --role-name stocks-github-deploy \
     --assume-role-policy-document file://deploy/github-oidc-trust.json
   aws iam put-role-policy --role-name stocks-github-deploy \
     --policy-name deploy --policy-document file://deploy/github-deploy-policy.json
   ```
3. GitHub repo → Settings → Secrets and variables → Actions:
   - Secret `AWS_DEPLOY_ROLE_ARN` = `arn:aws:iam::${ACCOUNT_ID}:role/stocks-github-deploy`
   - Variables `AWS_ACCOUNT_ID`, `AWS_REGION`, `ECR_REPO`, `S3_BUCKET`, `DISTRIBUTION_ID`

## 9 · Rollback

- **Backend**: `aws ssm send-command` re-running the docker run with the previous ECR
  `:<git-sha>` tag (CI tags every build).
- **Frontend**: S3 versioning is on — restore previous object versions, or `git revert` + push.
- New deploy takes effect in ~1 min (backend) / ~2 min (frontend + invalidation).

## Notes

- **SSE**: compression is OFF on `/api/*`; the app's 25s heartbeat sits under
  CloudFront's 60s origin read timeout. Do not enable caching on `/api/*`.
- **Cost**: ~$15–20/mo (t3.small + 30GB EBS; ECR/S3/CloudFront/EIP pennies at this scale).
- **Scaling up**: EC2→ASG behind an ALB is the documented next step; single instance
  is a known SPOF for v1.
- **Custom domain later**: request an ACM cert in `us-east-1`, add alternate domain name
  to the distribution, Route53 alias record — no architecture change.
