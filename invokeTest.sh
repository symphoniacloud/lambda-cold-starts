#!/bin/bash

set -euo pipefail

QUERY_LAMBDA=$(aws cloudformation describe-stacks --stack-name LambdaColdStartsRegional --query "Stacks[0].Outputs[?OutputKey=='QueryLambda'].OutputValue" --output text)

echo "Invoking Lambda $QUERY_LAMBDA"

aws lambda invoke --function-name "$QUERY_LAMBDA" --cli-binary-format raw-in-base64-out --invocation-type RequestResponse --payload '{ "invocationClass":"test", "trigger":"CLI" }' response.json
