#!/bin/bash

set -euo pipefail

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
CLOUDFORMATION_ARTIFACTS_BUCKET="cloudformation-artifacts-${ACCOUNT_ID}-us-east-1"

cfn-lint template.yaml
cfn-lint regional-template.yaml
mvn package

echo "*******************************"
echo "*** DEPLOYING CENTRAL STACK ***"
echo "*******************************"

sam deploy \
  --s3-bucket "$CLOUDFORMATION_ARTIFACTS_BUCKET" \
  --stack-name LambdaColdStarts \
  --parameter-overrides ApplicationId='prod' \
  --capabilities CAPABILITY_IAM \
  --no-fail-on-empty-changeset

REPORTING_BUCKET=$(aws cloudformation describe-stacks --stack-name LambdaColdStarts --query "Stacks[0].Outputs[?OutputKey=='ReportingBucket'].OutputValue" --output text)
TEST_REPORTING_BUCKET=$(aws cloudformation describe-stacks --stack-name LambdaColdStarts --query "Stacks[0].Outputs[?OutputKey=='TestReportingBucket'].OutputValue" --output text)

REGIONS=("us-east-1" "us-east-2" "us-west-1" "us-west-2" "ap-south-1" "ap-northeast-1" "ap-northeast-2" "ap-southeast-1" "ap-southeast-2" "eu-central-1" "eu-west-1" "eu-west-2" "eu-west-3" "eu-north-1" "sa-east-1")

deploy_region() {
  REGION=$1
  REGIONAL_ARTIFACTS_BUCKET="cloudformation-artifacts-$ACCOUNT_ID-$REGION"

  echo "****************************************"
  echo "*** DEPLOYING $REGION REGIONAL STACK ***"
  echo "****************************************"

  sam deploy \
    --s3-bucket "$REGIONAL_ARTIFACTS_BUCKET" \
    --region "$REGION" \
    --template regional-template.yaml \
    --stack-name LambdaColdStartsRegional \
    --parameter-overrides ApplicationId='prod' ReportingBucket="${REPORTING_BUCKET}" TestReportingBucket="${TEST_REPORTING_BUCKET}" \
    --capabilities CAPABILITY_IAM \
    --no-fail-on-empty-changeset
}

for REGION in ${REGIONS[*]}; do
    deploy_region $REGION
done

