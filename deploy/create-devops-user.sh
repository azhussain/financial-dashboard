#!/usr/bin/env bash
# Creates a DevOps IAM user for Stocks Explorer provisioning.
# Usage:  bash deploy/create-devops-user.sh [username] [region]
# Prereq: AWS CLI configured with admin-level credentials.
set -euo pipefail

# Git Bash/MSYS arg mangling guard — scoped to aws calls only, so other
# tools (npm etc.) keep normal path conversion.
aws() { MSYS_NO_PATHCONV=1 command aws "$@"; }

USER_NAME="${1:-stocks-devops}"
REGION="${2:-us-east-1}"
POLICY_NAME="${USER_NAME}-policy"
POLICY_FILE="devops-policy.json"

echo "==> Creating IAM user: ${USER_NAME}"
aws iam create-user --user-name "${USER_NAME}" \
  --tags Key=App,Value=stocks-explorer Key=Role,Value=devops

# Scoped policy for what deploy/aws-setup.md actually provisions:
# IAM (roles/OIDC), EC2, ECR, S3, CloudFront, SSM params + send-command.
cat > "${POLICY_FILE}" <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "IamProvisioning",
      "Effect": "Allow",
      "Action": [
        "iam:CreateRole", "iam:DeleteRole", "iam:GetRole", "iam:TagRole",
        "iam:AttachRolePolicy", "iam:DetachRolePolicy",
        "iam:PutRolePolicy", "iam:GetRolePolicy", "iam:DeleteRolePolicy",
        "iam:CreateInstanceProfile", "iam:DeleteInstanceProfile", "iam:GetInstanceProfile",
        "iam:AddRoleToInstanceProfile", "iam:RemoveRoleFromInstanceProfile",
        "iam:CreateOpenIDConnectProvider", "iam:GetOpenIDConnectProvider",
        "iam:DeleteOpenIDConnectProvider", "iam:ListOpenIDConnectProviders",
        "iam:ListRoles", "iam:PassRole"
      ],
      "Resource": "*"
    },
    {
      "Sid": "InfraProvisioning",
      "Effect": "Allow",
      "Action": [
        "ec2:*",
        "ecr:*",
        "s3:*",
        "cloudfront:*",
        "ssm:PutParameter", "ssm:GetParameter", "ssm:GetParameters",
        "ssm:DeleteParameter", "ssm:SendCommand", "ssm:ListCommandInvocations",
        "ssm:GetCommandInvocation", "ssm:StartSession", "ssm:TerminateSession",
        "ssm:DescribeInstanceInformation"
      ],
      "Resource": "*"
    }
  ]
}
EOF

echo "==> Attaching scoped policy: ${POLICY_NAME}"
aws iam put-user-policy --user-name "${USER_NAME}" \
  --policy-name "${POLICY_NAME}" --policy-document "file://${POLICY_FILE}"
rm -f "${POLICY_FILE}"

echo "==> Creating access key"
KEYS=$(aws iam create-access-key --user-name "${USER_NAME}" \
  --query 'AccessKey.[AccessKeyId,SecretAccessKey]' --output text)

ACCESS_KEY=$(echo "${KEYS}" | awk '{print $1}')
SECRET_KEY=$(echo "${KEYS}" | awk '{print $2}')

cat <<EOF

==> Done. Credentials (shown once — save them now):

    Access key:     ${ACCESS_KEY}
    Secret key:     ${SECRET_KEY}

Configure a named profile with:

    aws configure set aws_access_key_id ${ACCESS_KEY} --profile ${USER_NAME}
    aws configure set aws_secret_access_key ${SECRET_KEY} --profile ${USER_NAME}
    aws configure set region ${REGION} --profile ${USER_NAME}

Then use it with:  aws --profile ${USER_NAME} <command>
   or:             export AWS_PROFILE=${USER_NAME}

EOF
