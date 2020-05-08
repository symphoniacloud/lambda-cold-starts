# Lambda Cold Starts

This is a tool to benchmark cold starts on AWS Lambda, primarily for Java runtimes. The basic idea is as follows:

* Every 6 hours call a set of Lambda functions of various configurations
* Do this by performing a Lambda-to-Lambda invocation in the same region
* Have 3 different Lambda functions for each configuration.
* Capture latency and meta data to a file in S3
* Call the functions again twice more in fairly quick succession to get "warm start" comparison
* Wait 4 minutes, and call the functions again to see if there is a "cool start" difference
* Do all of this across multiple regions
* Capture the S3 data in a Glue table for analysis from Athena
* Use Step Functions to orchestrate everything

Why 6 hours in between runs? At current time if you don't call a Lambda function for 6 hours it will require a cold start on its next invocation.

Right now I'm capturing data to smooth out any day-specific performance quirks. I plan to make this data public later.

## Deployment

**CAREFUL! Deploying this application as is will deploy hundreds of Lambda functions across 15 regions!**

Deployment is via the `deploy.sh` shell script. You almost certainly don't want to deploy this app "as is" because of the warning above. :) 
The `deploy.sh` scripts assumes you have a CloudFormation artifact buckets available in every deployment region, each with the name "cloudformation-artifacts-${ACCOUNT_ID}-${REGION}"
