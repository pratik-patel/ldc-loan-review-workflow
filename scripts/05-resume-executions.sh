#!/bin/bash

# Resume script for paused executions
# Follows the logic:
# 1. Listen to SQS for task tokens
# 2. Check "stage" in message
# 3. If "ReviewTypeAssignment", send empty success (moves to LoanDecision)
# 4. If "LoanDecision", determine decision based on Request Number and send Decision + Attributes

QUEUE_URL="https://sqs.us-east-1.amazonaws.com/851725256415/ldc-loan-review-reclass-confirmations"
REGION="us-east-1"

echo "Listening for paused tasks on $QUEUE_URL..."

while true; do
  MESSAGES=$(aws sqs receive-message \
    --queue-url $QUEUE_URL \
    --max-number-of-messages 10 \
    --wait-time-seconds 5 \
    --region $REGION \
    --output json)

  COUNT=$(echo "$MESSAGES" | grep -c "MessageId")

  if [ "$COUNT" -eq 0 ]; then
    echo "No messages found. Waiting..."
    sleep 5
    continue
  fi

  echo "Found $COUNT messages. Processing..."

  # Loop through messages
  echo "$MESSAGES" | jq -c '.Messages[]' | while read -r message; do
    RECEIPT_HANDLE=$(echo "$message" | jq -r '.ReceiptHandle')
    BODY=$(echo "$message" | jq -r '.Body')
    TASK_TOKEN=$(echo "$BODY" | jq -r '.taskToken')
    REQ_NUM=$(echo "$BODY" | jq -r '.requestNumber')
    STAGE=$(echo "$BODY" | jq -r '.stage // "Unknown"')
    LOAN_NUM=$(echo "$BODY" | jq -r '.loanNumber // "Unknown"')

    echo "Processing Request: $REQ_NUM, Stage: $STAGE"

    PAYLOAD="{}"
    
    if [ "$STAGE" == "ReviewTypeAssignment" ]; then
        echo "  - Completing ReviewTypeAssignment..."
        PAYLOAD="{}"
    elif [ "$STAGE" == "LoanDecision" ]; then
        echo "  - Making Loan Decision..."
        
        # Default Logic
        DECISION="Approved"
        ATTR_DECISION="Approved"
        
        if [[ "$REQ_NUM" == *"REPURCHASE"* ]]; then
            DECISION="Repurchase"
            ATTR_DECISION="Repurchase"
        elif [[ "$REQ_NUM" == *"RECLASS"* ]]; then
            DECISION="Reclass Approved"
            ATTR_DECISION="Reclass"
        fi
        
        # Construct Payload with Decision AND Attributes
        PAYLOAD=$(cat <<EOF
{
  "loanDecision": "$DECISION",
  "requestNumber": "$REQ_NUM",
  "loanNumber": "$LOAN_NUM",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "$ATTR_DECISION"},
    {"attributeName": "DebtRatio", "attributeDecision": "Approved"}
  ]
}
EOF
)
    fi

    aws stepfunctions send-task-success \
      --task-token "$TASK_TOKEN" \
      --task-output "$PAYLOAD" \
      --region $REGION

    if [ $? -eq 0 ]; then
      echo "  Success! Deleting message..."
      aws sqs delete-message \
        --queue-url $QUEUE_URL \
        --receipt-handle "$RECEIPT_HANDLE" \
        --region $REGION
    else
      echo "  Failed to send task success."
    fi
  done
done
