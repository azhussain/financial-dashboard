#!/usr/bin/env bash
# Stocks Explorer — full AWS provisioning (Phases 1-3 of aws-setup.md).
#
#   bash deploy/aws-setup.sh            # uses default AWS profile
#   AWS_PROFILE=stocks-devops bash deploy/aws-setup.sh
#
# Reads OPENAI_API_KEY from the environment; prompts if unset.
# Idempotent where AWS allows it — re-running skips existing resources.
# All outputs are written to deploy/.aws-output.env.
set -euo pipefail
cd "$(dirname "$0")/.."

export APP=stocks-explorer
export REGION="${AWS_REGION:-us-east-1}"
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export BUCKET="${APP}-frontend-${ACCOUNT_ID}"
OUT=deploy/.aws-output.env

echo "==> Account ${ACCOUNT_ID} · region ${REGION} · profile ${AWS_PROFILE:-default}"

# ---------- 1 · ECR repository ----------
aws ecr describe-repositories --repository-names "${APP}-backend" --region "$REGION" >/dev/null 2>&1 \
  || aws ecr create-repository --repository-name "${APP}-backend" \
       --image-scanning-configuration scanOnPush=true --region "$REGION" >/dev/null
echo "==> ECR repo ${APP}-backend ready"

# ---------- 2 · Secret in SSM ----------
if [ -z "${OPENAI_API_KEY:-}" ]; then
  read -rsp "OPENAI_API_KEY (input hidden): " OPENAI_API_KEY; echo
fi
aws ssm put-parameter --name "/${APP}/OPENAI_API_KEY" --type SecureString \
  --value "${OPENAI_API_KEY}" --overwrite --region "$REGION" >/dev/null
echo "==> SSM SecureString /${APP}/OPENAI_API_KEY stored"

# ---------- 3 · EC2 instance role ----------
TRUST=/tmp/ec2-trust.json
cat > "$TRUST" <<'EOF'
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}
EOF
aws iam get-role --role-name "${APP}-ec2-role" >/dev/null 2>&1 \
  || aws iam create-role --role-name "${APP}-ec2-role" --assume-role-policy-document "file://${TRUST}" >/dev/null
for P in AmazonSSMManagedInstanceCore AmazonEC2ContainerRegistryReadOnly; do
  aws iam attach-role-policy --role-name "${APP}-ec2-role" \
    --policy-arn "arn:aws:iam::aws:policy/${P}" || true
done
cat > /tmp/ssm-read.json <<EOF
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":"ssm:GetParameter","Resource":"arn:aws:ssm:${REGION}:${ACCOUNT_ID}:parameter/${APP}/*"}]}
EOF
aws iam put-role-policy --role-name "${APP}-ec2-role" \
  --policy-name read-app-params --policy-document file:///tmp/ssm-read.json
aws iam get-instance-profile --instance-profile-name "${APP}-ec2-profile" >/dev/null 2>&1 \
  || aws iam create-instance-profile --instance-profile-name "${APP}-ec2-profile" >/dev/null
aws iam add-role-to-instance-profile --instance-profile-name "${APP}-ec2-profile" \
  --role-name "${APP}-ec2-role" 2>/dev/null || true
echo "==> IAM role ${APP}-ec2-role + instance profile ready"

# ---------- 4 · Security group ----------
SG_ID=$(aws ec2 describe-security-groups --filters "Name=group-name,Values=${APP}-sg" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)
if [ -z "$SG_ID" ] || [ "$SG_ID" = "None" ]; then
  SG_ID=$(aws ec2 create-security-group --group-name "${APP}-sg" \
    --description "Stocks Explorer backend" --query GroupId --output text)
fi
PL=$(aws ec2 describe-managed-prefix-lists --region "$REGION" \
  --query "PrefixLists[?PrefixListName=='com.amazonaws.global.cloudfront.origin-facing'].PrefixListId" \
  --output text | head -1)
aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --region "$REGION" \
  --ip-permissions "IpProtocol=tcp,FromPort=8080,ToPort=8080,PrefixListIds=[{PrefixListId=${PL},Description=CloudFront}]" 2>/dev/null || true
MYIP=$(curl -s https://checkip.amazonaws.com)
aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --region "$REGION" \
  --ip-permissions "IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=${MYIP}/32,Description=my-ip}]" 2>/dev/null || true
echo "==> SG ${SG_ID}: 8080←CloudFront prefix list ${PL}, 22←${MYIP}"

# ---------- 5 · EC2 instance ----------
AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --region "$REGION" --query Parameter.Value --output text)
INSTANCE_ID=$(aws ec2 describe-instances --region "$REGION" \
  --filters "Name=tag:App,Values=${APP}" "Name=instance-state-name,Values=running,pending" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || true)
if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "None" ]; then
  INSTANCE_ID=$(aws ec2 run-instances --image-id "$AMI" --instance-type t3.small \
    --iam-instance-profile "Name=${APP}-ec2-profile" --security-group-ids "$SG_ID" \
    --user-data "file://deploy/ec2-userdata.sh" --region "$REGION" \
    --tag-specifications "ResourceType=instance,Tags=[{Key=App,Value=${APP}},{Key=Name,Value=${APP}-api}]" \
    --query 'Instances[0].InstanceId' --output text)
fi
aws ec2 wait instance-running --instance-ids "$INSTANCE_ID" --region "$REGION"
ALLOC=$(aws ec2 describe-addresses --region "$REGION" \
  --filters "Name=tag:App,Values=${APP}" --query 'Addresses[0].AllocationId' --output text 2>/dev/null || true)
if [ -z "$ALLOC" ] || [ "$ALLOC" = "None" ]; then
  ALLOC=$(aws ec2 allocate-address --region "$REGION" --query AllocationId --output text)
  aws ec2 create-tags --resources "$ALLOC" --tags "Key=App,Value=${APP}" --region "$REGION"
fi
aws ec2 associate-address --instance-id "$INSTANCE_ID" --allocation-id "$ALLOC" --region "$REGION" \
  --allow-reassociation >/dev/null
EC2_DNS=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --region "$REGION" \
  --query 'Reservations[0].Instances[0].PublicDnsName' --output text)
echo "==> EC2 ${INSTANCE_ID} @ ${EC2_DNS} (EIP ${ALLOC})"

# ---------- 6 · S3 bucket (private, versioned) ----------
aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null \
  || aws s3 mb "s3://${BUCKET}" --region "$REGION"
aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled
echo "==> S3 bucket ${BUCKET} (private, versioned)"

# ---------- 7 · Frontend build + upload ----------
if [ -d frontend ]; then
  (cd frontend && npm ci --silent && npm run build --silent)
  aws s3 sync frontend/dist/ "s3://${BUCKET}" --delete
  echo "==> frontend/dist synced to s3://${BUCKET}"
fi

# ---------- 8 · CloudFront OAC + distribution ----------
OAC_ID=$(aws cloudfront list-origin-access-controls \
  --query "OriginAccessControlList.Items[?Name=='${APP}-oac'].Id" --output text 2>/dev/null || true)
if [ -z "$OAC_ID" ] || [ "$OAC_ID" = "None" ]; then
  OAC_ID=$(aws cloudfront create-origin-access-control --origin-access-control-config \
    "Name=${APP}-oac,SigningProtocol=sigv4,SigningBehavior=always,OriginAccessControlOriginType=s3" \
    --query 'OriginAccessControl.Id' --output text)
fi

sed -e "s|BUCKET_PLACEHOLDER|${BUCKET}|g" \
    -e "s|OAC_ID_PLACEHOLDER|${OAC_ID}|g" \
    -e "s|EC2_DNS_PLACEHOLDER|${EC2_DNS}|g" \
    -e "s|us-east-1|${REGION}|g" \
    deploy/dist-config.json > /tmp/dist-config-resolved.json

DIST_ID=$(aws cloudfront list-distributions \
  --query "DistributionList.Items[?Comment=='Stocks Explorer: S3 SPA + EC2 /api origin'].Id" \
  --output text 2>/dev/null || true)
if [ -z "$DIST_ID" ] || [ "$DIST_ID" = "None" ]; then
  DIST_ID=$(aws cloudfront create-distribution \
    --distribution-config file:///tmp/dist-config-resolved.json \
    --query 'Distribution.Id' --output text)
fi
DIST_DOMAIN=$(aws cloudfront get-distribution --id "$DIST_ID" \
  --query 'Distribution.DomainName' --output text)
echo "==> CloudFront ${DIST_ID} @ https://${DIST_DOMAIN}  (provisioning takes ~5 min)"

# ---------- 9 · Bucket policy (OAC access) ----------
cat > /tmp/bucket-policy.json <<EOF
{"Version":"2012-10-17","Statement":[{"Sid":"AllowCloudFront","Effect":"Allow",
"Principal":{"Service":"cloudfront.amazonaws.com"},"Action":"s3:GetObject",
"Resource":"arn:aws:s3:::${BUCKET}/*",
"Condition":{"StringEquals":{"AWS:SourceArn":"arn:aws:cloudfront::${ACCOUNT_ID}:distribution/${DIST_ID}"}}}]}
EOF
aws s3api put-bucket-policy --bucket "$BUCKET" --policy file:///tmp/bucket-policy.json
echo "==> Bucket policy attached (OAC-only access)"

# ---------- 10 · GitHub OIDC + deploy role ----------
aws iam list-open-id-connect-providers \
  --query "OpenIDConnectProviderList[?ends_with(Arn,'token.actions.githubusercontent.com')]" \
  --output text | grep -q githubusercontent \
  || aws iam create-open-id-connect-provider \
       --url https://token.actions.githubusercontent.com \
       --client-id-list sts.amazonaws.com \
       --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 >/dev/null

sed -e "s|ACCOUNT_ID_PLACEHOLDER|${ACCOUNT_ID}|g" deploy/github-oidc-trust.json > /tmp/oidc-trust.json
aws iam get-role --role-name stocks-github-deploy >/dev/null 2>&1 \
  || aws iam create-role --role-name stocks-github-deploy \
       --assume-role-policy-document file:///tmp/oidc-trust.json >/dev/null
sed -e "s|ACCOUNT_ID_PLACEHOLDER|${ACCOUNT_ID}|g" -e "s|REGION_PLACEHOLDER|${REGION}|g" \
    deploy/github-deploy-policy.json > /tmp/deploy-policy.json
aws iam put-role-policy --role-name stocks-github-deploy \
  --policy-name deploy --policy-document file:///tmp/deploy-policy.json
echo "==> GitHub OIDC provider + stocks-github-deploy role ready"

# ---------- Outputs ----------
cat > "$OUT" <<EOF
ACCOUNT_ID=${ACCOUNT_ID}
REGION=${REGION}
ECR_REPO=${APP}-backend
EC2_INSTANCE=${INSTANCE_ID}
EC2_DNS=${EC2_DNS}
S3_BUCKET=${BUCKET}
DISTRIBUTION_ID=${DIST_ID}
DIST_DOMAIN=${DIST_DOMAIN}
DEPLOY_ROLE_ARN=arn:aws:iam::${ACCOUNT_ID}:role/stocks-github-deploy
EOF

cat <<EOF

============================================================
  Stocks Explorer — provisioning complete
============================================================
  URL:            https://${DIST_DOMAIN}
  Backend:        ${INSTANCE_ID} (${EC2_DNS})
  Bucket:         ${BUCKET}
  Distribution:   ${DIST_ID}
  Outputs saved:  ${OUT}

  NEXT STEPS
  1. Push a backend image to ECR (once, or push to GitHub and let CI):
       cd backend
       aws ecr get-login-password --region ${REGION} | docker login \\
         --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com
       docker build -t ${APP}-backend .
       docker tag ${APP}-backend:latest \\
         ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${APP}-backend:latest
       docker push ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${APP}-backend:latest
     If the instance booted before the image existed:
       aws ssm send-command --instance-ids ${INSTANCE_ID} \\
         --document-name AWS-RunShellScript \\
         --parameters 'commands=["bash /var/lib/cloud/instance/user-data.txt"]'

  2. GitHub repo → Settings → Secrets and variables → Actions:
       Secret:    AWS_DEPLOY_ROLE_ARN = arn:aws:iam::${ACCOUNT_ID}:role/stocks-github-deploy
       Variables: AWS_ACCOUNT_ID=${ACCOUNT_ID}  AWS_REGION=${REGION}
                  ECR_REPO=${APP}-backend  S3_BUCKET=${BUCKET}
                  DISTRIBUTION_ID=${DIST_ID}

  3. Smoke test once the distribution deploys:
       curl https://${DIST_DOMAIN}/api/health
       curl -N --max-time 70 https://${DIST_DOMAIN}/api/markets/stream
============================================================
EOF
