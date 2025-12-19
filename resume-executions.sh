#!/bin/bash

# Resume script for paused executions
# This script listens to the SQS queue and auto-approves any tasks it finds.

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

    echo "Resuming execution for Request: $REQ_NUM"
    
    aws stepfunctions send-task-success \
      --task-token "$TASK_TOKEN" \
      --task-output "{}" \
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
