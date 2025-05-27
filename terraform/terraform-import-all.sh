#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default AWS region
AWS_REGION="us-east-2"

# Parse command line arguments
TFVARS_FILE="terraform.tfvars"
if [ "$#" -ge 1 ]; then
  TFVARS_FILE="$1"
fi

# Validate the tfvars file exists
if [ ! -f "$TFVARS_FILE" ]; then
  echo -e "${RED}Error: The specified tfvars file '$TFVARS_FILE' does not exist.${NC}"
  echo -e "${YELLOW}Usage: $0 [path/to/tfvars-file]${NC}"
  echo -e "${YELLOW}Example: $0 staging/vars.tfvars${NC}"
  exit 1
fi

echo -e "${BLUE}Using variables from $TFVARS_FILE${NC}"

# Function to safely import a resource with tfvars file
safe_import() {
  echo -e "${BLUE}Attempting to import $1 as $2...${NC}"
  terraform import -var-file="$TFVARS_FILE" $1 $2 2>/dev/null && echo -e "${GREEN}Successfully imported $1${NC}" || echo -e "${YELLOW}Import of $1 failed, continuing...${NC}"
}

# Read variable from tfvars file
get_var_from_tfvars() {
  local VAR_NAME=$1
  if [ -r "$TFVARS_FILE" ]; then
    grep -E "^$VAR_NAME[[:space:]]*=" "$TFVARS_FILE" | sed -E "s/^$VAR_NAME[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" | head -1 || echo ""
  else
    echo ""
  fi
}

# Function to get variable value from Terraform
get_terraform_var_value() {
  local VAR_NAME=$1
  local VAR_VALUE=""

  # Check specified tfvars file first
  VAR_VALUE=$(get_var_from_tfvars "$VAR_NAME")
  if [ -n "$VAR_VALUE" ]; then
    echo "$VAR_VALUE"
    return 0
  fi

  # If not found, check in variables.tf for default values
  VAR_VALUE=$(grep -A 5 "variable \"$VAR_NAME\"" --include="*.tf" . | grep "default" | head -1 | sed -E "s/.*default[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")

  echo "$VAR_VALUE"
}

# Function to resolve references in the form of aws_type.name.attribute
resolve_resource_reference() {
  local REF=$1
  local ATTRIBUTE=$2

  # Extract resource type and name
  local RES_TYPE=$(echo "$REF" | sed -E 's/([^.]+)\.[^.]+\..*/\1/')
  local RES_NAME=$(echo "$REF" | sed -E 's/[^.]+\.([^.]+)\..*/\1/')

  # If we're looking for an ID but the reference has .arn, adapt
  if [[ "$ATTRIBUTE" == "id" && "$REF" == *".arn" ]]; then
    ATTRIBUTE="arn"
  fi

  # Look up the resource definition to get the requested attribute
  local RESULT=$(grep -A 20 "resource \"$RES_TYPE\" \"$RES_NAME\"" --include="*.tf" . | grep -E "$ATTRIBUTE[[:space:]]*=" | head -1 | sed -E "s/.*$ATTRIBUTE[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")

  # If we found something, return it (might need further resolution)
  if [ -n "$RESULT" ]; then
    echo "$RESULT"
    return 0
  fi

  # Special case for certain types
  case "$RES_TYPE" in
    aws_s3_bucket)
      if [[ "$ATTRIBUTE" == "bucket" || "$ATTRIBUTE" == "id" ]]; then
        local BUCKET_NAME=$(grep -A 20 "resource \"$RES_TYPE\" \"$RES_NAME\"" --include="*.tf" . | grep -E "bucket[[:space:]]*=" | head -1 | sed -E "s/.*bucket[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
        echo "$BUCKET_NAME"
        return 0
      fi
      ;;
    aws_lambda_function)
      if [[ "$ATTRIBUTE" == "function_name" || "$ATTRIBUTE" == "id" ]]; then
        local FUNC_NAME=$(grep -A 20 "resource \"$RES_TYPE\" \"$RES_NAME\"" --include="*.tf" . | grep -E "function_name[[:space:]]*=" | head -1 | sed -E "s/.*function_name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
        echo "$FUNC_NAME"
        return 0
      fi
      ;;
    aws_cloudwatch_event_rule)
      if [[ "$ATTRIBUTE" == "name" || "$ATTRIBUTE" == "id" ]]; then
        local RULE_NAME=$(grep -A 20 "resource \"$RES_TYPE\" \"$RES_NAME\"" --include="*.tf" . | grep -E "name[[:space:]]*=" | head -1 | sed -E "s/.*name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
        echo "$RULE_NAME"
        return 0
      fi
      ;;
  esac

  # Default: return empty if we couldn't resolve
  echo ""
}

# Function to resolve full value, including variables and references
resolve_value() {
  local VALUE="$1"
  local RESULT="$VALUE"

  # Handle empty values
  if [ -z "$VALUE" ]; then
    echo ""
    return
  fi

  # Handle terraform resource references (aws_type.name.attribute)
  if [[ "$VALUE" == *"."*"."* ]]; then
    # Special case for interpolation with ID
    if [[ "$VALUE" == *".id" ]]; then
      local REF="${VALUE%%.id}"
      RESULT=$(resolve_resource_reference "$REF" "id")
    elif [[ "$VALUE" == *".arn" ]]; then
      local REF="${VALUE%%.arn}"
      RESULT=$(resolve_resource_reference "$REF" "arn")
    elif [[ "$VALUE" == *".name" ]]; then
      local REF="${VALUE%%.name}"
      RESULT=$(resolve_resource_reference "$REF" "name")
    elif [[ "$VALUE" == *".bucket" ]]; then
      local REF="${VALUE%%.bucket}"
      RESULT=$(resolve_resource_reference "$REF" "bucket")
    elif [[ "$VALUE" == *".function_name" ]]; then
      local REF="${VALUE%%.function_name}"
      RESULT=$(resolve_resource_reference "$REF" "function_name")
    fi
  fi

  # Handle variable references
  if [[ "$RESULT" == *"var."* ]]; then
    local VAR_NAME=$(echo "$RESULT" | sed -E "s/.*var\.([a-zA-Z0-9_-]*).*/\1/g")
    local VAR_VALUE=$(get_terraform_var_value "$VAR_NAME")

    if [ -n "$VAR_VALUE" ]; then
      # Replace the variable reference with its value
      RESULT=$(echo "$RESULT" | sed -E "s/var\.$VAR_NAME/$VAR_VALUE/g")
    fi
  fi

  echo "$RESULT"
}

# Function to get current deployed resources from AWS
get_aws_resources() {
  echo -e "${BLUE}Retrieving existing resources from AWS in region ${AWS_REGION}...${NC}"

  # Check if AWS CLI is available
  if ! command -v aws &> /dev/null; then
    echo -e "${YELLOW}AWS CLI not available, skipping resource retrieval${NC}"
    return
  fi

  # Get Lambda functions
  echo -e "${BLUE}Retrieving Lambda functions...${NC}"
  LAMBDA_FUNCTIONS=$(aws lambda list-functions --region "$AWS_REGION" --query "Functions[].FunctionName" --output text 2>/dev/null || echo "")
  echo -e "Found Lambda functions: $LAMBDA_FUNCTIONS"

  # Get IAM roles (IAM is global, no region needed)
  echo -e "${BLUE}Retrieving IAM roles...${NC}"
  IAM_ROLES=$(aws iam list-roles --query "Roles[].RoleName" --output text 2>/dev/null || echo "")
  echo -e "Found IAM roles: $IAM_ROLES"

  # Get S3 buckets (S3 is global, no region needed)
  echo -e "${BLUE}Retrieving S3 buckets...${NC}"
  S3_BUCKETS=$(aws s3api list-buckets --query "Buckets[].Name" --output text 2>/dev/null || echo "")
  echo -e "Found S3 buckets: $S3_BUCKETS"

  # Get CloudWatch event rules
  echo -e "${BLUE}Retrieving CloudWatch event rules...${NC}"
  EVENT_RULES=$(aws events list-rules --region "$AWS_REGION" --query "Rules[].Name" --output text 2>/dev/null || echo "")
  echo -e "Found CloudWatch event rules: $EVENT_RULES"

  # Get DynamoDB tables
  echo -e "${BLUE}Retrieving DynamoDB tables...${NC}"
  DYNAMODB_TABLES=$(aws dynamodb list-tables --region "$AWS_REGION" --query "TableNames" --output text 2>/dev/null || echo "")

  # Get SQS queues
  echo -e "${BLUE}Retrieving SQS queues...${NC}"
  SQS_QUEUES=$(aws sqs list-queues --region "$AWS_REGION" --query "QueueUrls[]" --output text 2>/dev/null || echo "")

  # Get SNS topics
  echo -e "${BLUE}Retrieving SNS topics...${NC}"
  SNS_TOPICS=$(aws sns list-topics --region "$AWS_REGION" --query "Topics[].TopicArn" --output text 2>/dev/null || echo "")

  # Store the AWS resources
  export AWS_LAMBDA_FUNCTIONS="$LAMBDA_FUNCTIONS"
  export AWS_IAM_ROLES="$IAM_ROLES"
  export AWS_S3_BUCKETS="$S3_BUCKETS"
  export AWS_EVENT_RULES="$EVENT_RULES"
  export AWS_DYNAMODB_TABLES="$DYNAMODB_TABLES"
  export AWS_SQS_QUEUES="$SQS_QUEUES"
  export AWS_SNS_TOPICS="$SNS_TOPICS"

  echo -e "${GREEN}Resource retrieval complete${NC}"
}

# Function to extract resource type, name, and ID from terraform files
extract_resources() {
  echo -e "${BLUE}Extracting resources from Terraform files...${NC}"

  # Define resources types to extract - easily expandable
  RESOURCE_TYPES=(
    "aws_lambda_function:function_name"
    "aws_lambda_permission:function_name"
    "aws_iam_role:name"
    "aws_iam_role_policy:role"
    "aws_s3_bucket:bucket"
    "aws_s3_object:key"
    "aws_cloudwatch_event_rule:name"
    "aws_cloudwatch_event_target:rule"
    "aws_dynamodb_table:name"
    "aws_sqs_queue:name"
    "aws_sns_topic:name"
  )

  # Process each resource type
  for TYPE_DEF in "${RESOURCE_TYPES[@]}"; do
    # Split type and property name
    TYPE=$(echo $TYPE_DEF | cut -d':' -f1)
    PROP=$(echo $TYPE_DEF | cut -d':' -f2)

    echo -e "${BLUE}Looking for ${TYPE} resources...${NC}"

    # Find all resources of this type
    RESOURCES_OF_TYPE=$(grep -r "resource \"${TYPE}\"" --include="*.tf" . | sed -E "s/.*resource \"${TYPE}\" \"([^\"]+)\".*/\1/g" 2>/dev/null | sort -u || echo "")

    for RESOURCE_NAME in $RESOURCES_OF_TYPE; do
      # Skip if empty
      if [ -z "$RESOURCE_NAME" ]; then
        continue
      fi

      echo -e "${YELLOW}Found resource: ${TYPE}.${RESOURCE_NAME}${NC}"

      # Handle resource identification based on type
      case "$TYPE" in
        aws_lambda_function)
          # For Lambda functions, try to get the function_name or use the resource name
          FUNCTION_NAME=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "function_name[[:space:]]*=" | head -1 | sed -E "s/.*function_name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
          FUNCTION_NAME=$(resolve_value "$FUNCTION_NAME")

          if [ -z "$FUNCTION_NAME" ]; then
            FUNCTION_NAME=$(get_terraform_var_value "lambda_function")
          fi

          if [ -n "$FUNCTION_NAME" ]; then
            echo -e "${BLUE}Attempting to import Lambda function: ${FUNCTION_NAME}${NC}"
            # Try exact match first
            if [[ "$AWS_LAMBDA_FUNCTIONS" == *"$FUNCTION_NAME"* ]]; then
              safe_import "${TYPE}.${RESOURCE_NAME}" "$FUNCTION_NAME"
            else
              # Try partial match as fallback
              for LAMBDA in $AWS_LAMBDA_FUNCTIONS; do
                if [[ "$LAMBDA" == *"$FUNCTION_NAME"* ]]; then
                  echo -e "${BLUE}Found partial match: $LAMBDA for $FUNCTION_NAME${NC}"
                  safe_import "${TYPE}.${RESOURCE_NAME}" "$LAMBDA"
                  break
                fi
              done
            fi
          else
            echo -e "${YELLOW}Could not determine function name for ${TYPE}.${RESOURCE_NAME}${NC}"
          fi
          ;;

        aws_lambda_permission)
          FUNCTION_NAME=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "function_name[[:space:]]*=" | head -1 | sed -E "s/.*function_name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
          STATEMENT_ID=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "statement_id[[:space:]]*=" | head -1 | sed -E "s/.*statement_id[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")

          # Handle the case where function_name is a reference to another resource
          if [[ "$FUNCTION_NAME" == *"."*"."* ]]; then
            FUNCTION_NAME=$(resolve_value "$FUNCTION_NAME")
          else
            FUNCTION_NAME=$(resolve_value "$FUNCTION_NAME")
          fi

          if [ -z "$FUNCTION_NAME" ]; then
            FUNCTION_NAME=$(get_terraform_var_value "lambda_function")
          fi

          STATEMENT_ID=$(resolve_value "$STATEMENT_ID")

          if [ -n "$FUNCTION_NAME" ] && [ -n "$STATEMENT_ID" ]; then
            # For Lambda permissions, we need both the function name and statement ID
            # The import ID format is: function-name/statement-id
            IMPORT_ID="${FUNCTION_NAME}/${STATEMENT_ID}"

            # Check if a Lambda function with this name exists
            if [[ "$AWS_LAMBDA_FUNCTIONS" == *"$FUNCTION_NAME"* ]]; then
              echo -e "${BLUE}Importing Lambda permission for function ${FUNCTION_NAME} with statement ID ${STATEMENT_ID}${NC}"
              safe_import "${TYPE}.${RESOURCE_NAME}" "$IMPORT_ID"
            else
              # Try partial match
              for LAMBDA in $AWS_LAMBDA_FUNCTIONS; do
                if [[ "$LAMBDA" == *"$FUNCTION_NAME"* ]]; then
                  IMPORT_ID="${LAMBDA}/${STATEMENT_ID}"
                  echo -e "${BLUE}Importing Lambda permission with partial match: ${LAMBDA}/${STATEMENT_ID}${NC}"
                  safe_import "${TYPE}.${RESOURCE_NAME}" "$IMPORT_ID"
                  break
                fi
              done
            fi
          else
            echo -e "${YELLOW}Missing function_name or statement_id for ${TYPE}.${RESOURCE_NAME}${NC}"
          fi
          ;;

        aws_iam_role)
          ROLE_NAME=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "name[[:space:]]*=" | head -1 | sed -E "s/.*name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")

          # Handle role names with random suffixes
          if [[ "$ROLE_NAME" == *"\${random"* || "$ROLE_NAME" == *"-\${random"* ]]; then
            BASE_ROLE_NAME=$(echo "$ROLE_NAME" | sed -E 's/(.*)-\$\{.*/\1/' 2>/dev/null || echo "")
            if [ -n "$BASE_ROLE_NAME" ]; then
              echo -e "${BLUE}Searching for IAM role with base name: ${BASE_ROLE_NAME}${NC}"
              for ROLE in $AWS_IAM_ROLES; do
                if [[ "$ROLE" == "$BASE_ROLE_NAME"* ]]; then
                  echo -e "${BLUE}Found matching IAM role: ${ROLE}${NC}"
                  safe_import "${TYPE}.${RESOURCE_NAME}" "$ROLE"
                  break
                fi
              done
            else
              echo -e "${YELLOW}Could not determine base part of role name for ${TYPE}.${RESOURCE_NAME}${NC}"
            fi
          else
            ROLE_NAME=$(resolve_value "$ROLE_NAME")
            if [ -n "$ROLE_NAME" ] && [[ "$AWS_IAM_ROLES" == *"$ROLE_NAME"* ]]; then
              safe_import "${TYPE}.${RESOURCE_NAME}" "$ROLE_NAME"
            else
              echo -e "${YELLOW}Could not find matching IAM role for ${TYPE}.${RESOURCE_NAME}${NC}"
            fi
          fi
          ;;

        aws_iam_role_policy)
          ROLE=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "role[[:space:]]*=" | head -1 | sed -E "s/.*role[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
          POLICY_NAME=$(echo "$RESOURCE_NAME" | sed -E 's/.*_([^_]+)$/\1/' 2>/dev/null || echo "$RESOURCE_NAME")

          # Handle reference to another resource
          if [[ "$ROLE" == *"."*"."* ]]; then
            # Extract the resource type and name from the reference
            REF_RESOURCE_TYPE=$(echo "$ROLE" | sed -E 's/([^.]+)\.[^.]+\..*/\1/' 2>/dev/null || echo "")
            REF_RESOURCE_NAME=$(echo "$ROLE" | sed -E 's/[^.]+\.([^.]+)\..*/\1/' 2>/dev/null || echo "")

            # Try to find the actual role name
            if [ -n "$REF_RESOURCE_TYPE" ] && [ -n "$REF_RESOURCE_NAME" ]; then
              ROLE_NAME=$(grep -A 20 "resource \"${REF_RESOURCE_TYPE}\" \"${REF_RESOURCE_NAME}\"" --include="*.tf" . | grep -E "name[[:space:]]*=" | head -1 | sed -E "s/.*name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")

              # Handle role names with random suffixes
              if [[ "$ROLE_NAME" == *"\${random"* || "$ROLE_NAME" == *"-\${random"* ]]; then
                BASE_ROLE_NAME=$(echo "$ROLE_NAME" | sed -E 's/(.*)-\$\{.*/\1/' 2>/dev/null || echo "")
                if [ -n "$BASE_ROLE_NAME" ]; then
                  echo -e "${BLUE}Searching for IAM role with base name: ${BASE_ROLE_NAME}${NC}"
                  for ROLE in $AWS_IAM_ROLES; do
                    if [[ "$ROLE" == "$BASE_ROLE_NAME"* ]]; then
                      ROLE_NAME="$ROLE"
                      echo -e "${BLUE}Found matching IAM role: ${ROLE_NAME}${NC}"
                      break
                    fi
                  done
                fi
              else
                ROLE_NAME=$(resolve_value "$ROLE_NAME")
              fi
            fi
          else
            ROLE_NAME=$(resolve_value "$ROLE")
          fi

          if [ -n "$ROLE_NAME" ] && [ -n "$POLICY_NAME" ]; then
            # For IAM role policies, the import ID format is: role-name:policy-name
            IMPORT_ID="${ROLE_NAME}:${POLICY_NAME}"
            echo -e "${BLUE}Attempting to import IAM role policy ${POLICY_NAME} for role ${ROLE_NAME}${NC}"
            safe_import "${TYPE}.${RESOURCE_NAME}" "$IMPORT_ID"
          else
            echo -e "${YELLOW}Missing role name or policy name for ${TYPE}.${RESOURCE_NAME}${NC}"
          fi
          ;;

        aws_s3_bucket)
          BUCKET_NAME=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "bucket[[:space:]]*=" | head -1 | sed -E "s/.*bucket[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")

          # Check if it's a variable reference
          if [[ "$BUCKET_NAME" == *"var."* ]]; then
            BUCKET_NAME=$(resolve_value "$BUCKET_NAME")
          fi

          # Handle interpolation for S3 buckets with random suffix
          if [[ "$BUCKET_NAME" == *"\${" || "$BUCKET_NAME" == *"-\${" ]]; then
            # Extract base part before interpolation
            BASE_PART=$(echo "$BUCKET_NAME" | sed -E 's/(.*)-\$\{.*/\1/' 2>/dev/null || echo "")
            if [ -n "$BASE_PART" ]; then
              echo -e "${BLUE}Searching for S3 bucket with base name: ${BASE_PART}${NC}"
              for BUCKET in $AWS_S3_BUCKETS; do
                if [[ "$BUCKET" == "$BASE_PART"* ]]; then
                  echo -e "${BLUE}Found matching S3 bucket: ${BUCKET}${NC}"
                  safe_import "${TYPE}.${RESOURCE_NAME}" "$BUCKET"
                  break
                fi
              done
            else
              echo -e "${YELLOW}Could not determine base part of bucket name for ${TYPE}.${RESOURCE_NAME}${NC}"
            fi
          elif [ -n "$BUCKET_NAME" ]; then
            # Direct bucket name check
            if [[ "$AWS_S3_BUCKETS" == *"$BUCKET_NAME"* ]]; then
              safe_import "${TYPE}.${RESOURCE_NAME}" "$BUCKET_NAME"
            else
              echo -e "${YELLOW}Could not find matching S3 bucket for ${TYPE}.${RESOURCE_NAME}${NC}"
            fi
          fi
          ;;

        aws_s3_object)
          BUCKET_REF=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "bucket[[:space:]]*=" | head -1 | sed -E "s/.*bucket[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
          KEY_REF=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "key[[:space:]]*=" | head -1 | sed -E "s/.*key[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")

          # Resolve bucket reference
          if [[ "$BUCKET_REF" == *"."*"."* ]]; then
            # It's a resource reference
            REF_RESOURCE_TYPE=$(echo "$BUCKET_REF" | sed -E 's/([^.]+)\.[^.]+\..*/\1/' 2>/dev/null || echo "")
            REF_RESOURCE_NAME=$(echo "$BUCKET_REF" | sed -E 's/[^.]+\.([^.]+)\..*/\1/' 2>/dev/null || echo "")

            if [ "$REF_RESOURCE_TYPE" == "aws_s3_bucket" ]; then
              # Find the actual bucket name from the bucket resource
              BUCKET_DEF=$(grep -A 20 "resource \"${REF_RESOURCE_TYPE}\" \"${REF_RESOURCE_NAME}\"" --include="*.tf" . | grep -E "bucket[[:space:]]*=" | head -1 | sed -E "s/.*bucket[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")

              # Handle random suffix in bucket name
              if [[ "$BUCKET_DEF" == *"\${" || "$BUCKET_DEF" == *"-\${" ]]; then
                BASE_BUCKET=$(echo "$BUCKET_DEF" | sed -E 's/(.*)-\$\{.*/\1/' 2>/dev/null || echo "")
                if [ -n "$BASE_BUCKET" ]; then
                  echo -e "${BLUE}Searching for S3 bucket with base name: ${BASE_BUCKET}${NC}"
                  for BUCKET in $AWS_S3_BUCKETS; do
                    if [[ "$BUCKET" == "$BASE_BUCKET"* ]]; then
                      BUCKET_NAME="$BUCKET"
                      echo -e "${BLUE}Found matching S3 bucket for object: ${BUCKET_NAME}${NC}"
                      break
                    fi
                  done
                fi
              else
                BUCKET_NAME=$(resolve_value "$BUCKET_DEF")
              fi
            fi
          else
            BUCKET_NAME=$(resolve_value "$BUCKET_REF")
          fi

          # Resolve object key - could be a variable
          OBJECT_KEY=$(resolve_value "$KEY_REF")

          if [ -n "$BUCKET_NAME" ] && [ -n "$OBJECT_KEY" ]; then
            # For S3 objects, the import ID format is: bucket-name/object-key
            IMPORT_ID="${BUCKET_NAME}/${OBJECT_KEY}"
            echo -e "${BLUE}Attempting to import S3 object ${OBJECT_KEY} from bucket ${BUCKET_NAME}${NC}"
            safe_import "${TYPE}.${RESOURCE_NAME}" "$IMPORT_ID"
          else
            echo -e "${YELLOW}Missing bucket name or object key for ${TYPE}.${RESOURCE_NAME}${NC}"
          fi
          ;;

        aws_cloudwatch_event_rule)
          RULE_NAME=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "name[[:space:]]*=" | head -1 | sed -E "s/.*name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
          RULE_NAME=$(resolve_value "$RULE_NAME")

          if [ -n "$RULE_NAME" ]; then
            if [[ "$AWS_EVENT_RULES" == *"$RULE_NAME"* ]]; then
              safe_import "${TYPE}.${RESOURCE_NAME}" "$RULE_NAME"
            else
              # Try partial match
              for RULE in $AWS_EVENT_RULES; do
                if [[ "$RULE" == *"$RULE_NAME"* ]]; then
                  echo -e "${BLUE}Found partial match for event rule: ${RULE}${NC}"
                  safe_import "${TYPE}.${RESOURCE_NAME}" "$RULE"
                  break
                fi
              done
            fi
          else
            echo -e "${YELLOW}Could not determine rule name for ${TYPE}.${RESOURCE_NAME}${NC}"
          fi
          ;;

        aws_cloudwatch_event_target)
          RULE_REF=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "rule[[:space:]]*=" | head -1 | sed -E "s/.*rule[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
          TARGET_ID=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "target_id[[:space:]]*=" | head -1 | sed -E "s/.*target_id[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")

          # Resolve rule reference
          if [[ "$RULE_REF" == *"."*"."* ]]; then
            # It's a resource reference
            REF_RESOURCE_TYPE=$(echo "$RULE_REF" | sed -E 's/([^.]+)\.[^.]+\..*/\1/' 2>/dev/null || echo "")
            REF_RESOURCE_NAME=$(echo "$RULE_REF" | sed -E 's/[^.]+\.([^.]+)\..*/\1/' 2>/dev/null || echo "")

            if [ "$REF_RESOURCE_TYPE" == "aws_cloudwatch_event_rule" ]; then
              # Find the actual rule name from the rule resource
              RULE_NAME=$(grep -A 20 "resource \"${REF_RESOURCE_TYPE}\" \"${REF_RESOURCE_NAME}\"" --include="*.tf" . | grep -E "name[[:space:]]*=" | head -1 | sed -E "s/.*name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
              RULE_NAME=$(resolve_value "$RULE_NAME")

              if [ -z "$RULE_NAME" ]; then
                # If no explicit name, use the resource name
                RULE_NAME="$REF_RESOURCE_NAME"
              fi
            fi
          else
            RULE_NAME=$(resolve_value "$RULE_REF")
          fi

          # Resolve target ID - could be a reference
          if [[ "$TARGET_ID" == *"."*"."* ]]; then
            TARGET_ID=$(resolve_value "$TARGET_ID")
          fi

          if [ -n "$RULE_NAME" ]; then
            # For CloudWatch event targets, the import ID format is: rule-name/target-id
            if [ -n "$TARGET_ID" ]; then
              IMPORT_ID="${RULE_NAME}/${TARGET_ID}"
            else
              # If no target ID is specified, use the resource name as a fallback
              IMPORT_ID="${RULE_NAME}/${RESOURCE_NAME}"
            fi

            echo -e "${BLUE}Attempting to import CloudWatch event target for rule ${RULE_NAME}${NC}"
            if [[ "$AWS_EVENT_RULES" == *"$RULE_NAME"* ]]; then
              safe_import "${TYPE}.${RESOURCE_NAME}" "$IMPORT_ID"
            else
              # Try partial match
              for RULE in $AWS_EVENT_RULES; do
                if [[ "$RULE" == *"$RULE_NAME"* ]]; then
                  IMPORT_ID="${RULE}/${TARGET_ID:-$RESOURCE_NAME}"
                  echo -e "${BLUE}Found partial match for event rule: ${RULE}${NC}"
                  safe_import "${TYPE}.${RESOURCE_NAME}" "$IMPORT_ID"
                  break
                fi
              done
            fi
          else
            echo -e "${YELLOW}Missing rule name for ${TYPE}.${RESOURCE_NAME}${NC}"
          fi
          ;;

        aws_dynamodb_table)
          TABLE_NAME=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "name[[:space:]]*=" | head -1 | sed -E "s/.*name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
          TABLE_NAME=$(resolve_value "$TABLE_NAME")

          if [ -n "$TABLE_NAME" ] && [[ "$AWS_DYNAMODB_TABLES" == *"$TABLE_NAME"* ]]; then
            safe_import "${TYPE}.${RESOURCE_NAME}" "$TABLE_NAME"
          else
            echo -e "${YELLOW}Could not find matching DynamoDB table for ${TYPE}.${RESOURCE_NAME}${NC}"
          fi
          ;;

        aws_sqs_queue)
          QUEUE_NAME=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "name[[:space:]]*=" | head -1 | sed -E "s/.*name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
          QUEUE_NAME=$(resolve_value "$QUEUE_NAME")

          if [ -n "$QUEUE_NAME" ]; then
            for QUEUE_URL in $AWS_SQS_QUEUES; do
              if [[ "$QUEUE_URL" == *"$QUEUE_NAME"* ]]; then
                safe_import "${TYPE}.${RESOURCE_NAME}" "$QUEUE_URL"
                break
              fi
            done
          else
            echo -e "${YELLOW}Could not find matching SQS queue for ${TYPE}.${RESOURCE_NAME}${NC}"
          fi
          ;;

        aws_sns_topic)
          TOPIC_NAME=$(grep -A 20 "resource \"${TYPE}\" \"${RESOURCE_NAME}\"" --include="*.tf" . | grep -E "name[[:space:]]*=" | head -1 | sed -E "s/.*name[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/" 2>/dev/null || echo "")
          TOPIC_NAME=$(resolve_value "$TOPIC_NAME")

          if [ -n "$TOPIC_NAME" ]; then
            for TOPIC_ARN in $AWS_SNS_TOPICS; do
              if [[ "$TOPIC_ARN" == *"$TOPIC_NAME"* ]]; then
                safe_import "${TYPE}.${RESOURCE_NAME}" "$TOPIC_ARN"
                break
              fi
            done
          else
            echo -e "${YELLOW}Could not find matching SNS topic for ${TYPE}.${RESOURCE_NAME}${NC}"
          fi
          ;;
      esac
    done
  done
}

# Function to check if Terraform is installed
check_terraform() {
  if ! command -v terraform &> /dev/null; then
    echo -e "${RED}Terraform is not installed. Please install it to continue.${NC}"
    exit 1
  fi
}

# Main script
echo -e "${BLUE}Starting resource import script...${NC}"
echo -e "${BLUE}Using AWS region: ${AWS_REGION}${NC}"
echo -e "${BLUE}Using variables file: ${TFVARS_FILE}${NC}"

# Check prerequisites
check_terraform

# Initialize Terraform
echo -e "${BLUE}Initializing Terraform...${NC}"
terraform init

# Get existing AWS resources first
get_aws_resources

# Extract resources and import them
extract_resources

echo -e "${GREEN}Import process completed.${NC}"