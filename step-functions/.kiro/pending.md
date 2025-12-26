# Pending Business Logic & Configuration

This document tracks open questions, TBDs (To Be Determined), and pending configuration items required for the LDC Loan Review Workflow.

## 1. Emails & Notifications

### Templates (TBD)
The following email templates need to be defined and stored in the Parameter Store (`/ldc-workflow/email-template/...`):
- **Repurchase Notification**: Content for the email sent when a loan is determined as "Repurchase".
- **Reclass Expiration Notification**: Content for the email sent when the 48-hour reclass timer expires without confirmation.

### Configuration
- **Sender Email**: The `SENDER_EMAIL` environment variable is currently set to `noreply@ldc.com`. *Confirm if this is the correct verified SES identity.*
- **Recipient Emails**: Logic currently fetches recipients from Parameter Store (`/ldc-workflow/notification-email`). *Need list of actual recipient email addresses or distribution lists for each notification type.*

## 2. Reclass Workflow Logic

### Timer Logic
- **Current Implementation**: The workflow uses a **Hard Wait** of 48 hours (172,800 seconds).
- **Open Question**: Should this wait be interruptible?
    - *Current*: Workflow waits full 48 hours regardless of whether confirmation happens early.
    - *Proposed*: Change to a "Wait for Callback" token pattern if the business wants to process the reclass immediately upon confirmation, rather than waiting for the timer to expire.

### SQS Integration
- **When to Add to Queue**: Currently, the workflow adds the request to the SQS queue (`ldc-loan-review-reclass-confirmations`) **only after the 48-hour timer expires** and if `reclassConfirmed` is true in DynamoDB.
- **Open Question**: Should the SQS message be sent immediately when "Reclass Approved" status is determined, or is the current post-wait logic correct?

## 3. Business Rules (Loan Status)

### Conflict Resolution (TBD)
- **Scenario**: A loan has multiple conflicting attributes (e.g., one attribute is "Repurchase" and another is "Reclass").
- **Current Logic**: Simple priority check (Repurchase > Reclass).
- **Pending Decision**: Confirm final priority rules. Does a single "Repurchase" flag always override a "Reclass" recommendation?

### Trigger Criteria
- **Repurchase**: Currently triggers if *count > 0*.
- **Reclass**: Currently triggers if *count > 0*.
- **Pending Decision**: Are there specific combinations or thresholds required, or is a single attribute flag sufficient?

## 4. External Integrations

### Vend PPA API
- **Status**: **MOCK Implementation**.
- **Pending Action**: Replace `VendPpaIntegrationHandler` mock logic with actual HTTP call to Vend PPA services.
    - Need API Endpoint.
    - Need Authentication/Authorization credentials.
    - Need Request/Response contract mapping.
