# Business Workflow & Requirements
This document outlines the business process flow for the Loan Review Workflow, detailing the interactions between the system, available APIs, and required human inputs.
## Workflow Overview
The workflow orchestrates the lifecycle of a loan review, from initiation to final system updates. It relies on the `startPPAreview` API to begin and the `getNextStep` API to process human inputs and move the workflow forward.
## Process Flow
1.  **Initiate Review**
    *   **Trigger**: The workflow is started via the **`startPPAreview` API**.
    *   **System Action**: Validates the incoming review request data.
2.  **Assign Default Review Type**
    *   **System Action**: Automatically assigns the default review type received in the initiation payload.
    *   **Requirement**: Data persistence of the initial review type.
3.  **User Assignment**
    *   **Status**: *Waiting for Assignment*
    *   **Action**: Assign task to user via **`assignToUser` API** *[Logic performed outside workflow]*.
    *   **Logic**: Assign if the user is not already assigned to this newly triggered task.
    *   **System Action**: The workflow waits until the assignment is confirmed/completed externally.
4.  **Assign To Type**
    *   **Action**: Assign the workflow to the requested review type via **`assignToType` API** (invoked from MFE).
    *   **Workflow State**: The workflow **suspends** after this step, waiting for the loan decision process.
5.  **Loan Decision & Completion Check**
    *   **Trigger**: The workflow is resumed by the **`getNextStep` API** (invoked from MFE).
    *   **Prerequisite**: The user updates loan attributes *outside* the workflow (directly in the database).
    *   **System Action**:
        1.  Fetches details of all loan attributes.
        2.  CHECK: Are any attributes in "Pending Review" status?
            *   **Yes**: No decision is made. The workflow loops back and remains in the current suspended stage.
            *   **No**: Proceed to determine Loan Status.
    *   **Loan Status Determination**:
        *   **Approved**: All attributes approved.
        *   **Rejected**: All attributes rejected (or sufficient rejection criteria).
        *   **Partially Approved**: At least one attribute is **Approved** AND at least one is **Rejected**.
        *   **Repurchase**: Loan requires repurchase.
        *   **Reclass Approved**: Loan requires reclassification.
6.  **Status Routing**
    The system routes the workflow based on the determined status:
    *   **Approved** -> Update External Systems.
    *   **Rejected** -> Update External Systems.
    *   **Partially Approved** -> Update External Systems.
    *   **Repurchase** -> Update External Systems.
    *   **Reclass Approved** -> Reclass Confirmation.
7.  **Reclass Confirmation (Conditional)**
    *   *This step only occurs if the status is "Reclass Approved".*
    *   **Status**: *Waiting for Confirmation*
    *   **Action**: Wait for user confirmation.
    *   **Trigger**: User invokes **`getNextStep` API** from MFE.
    *   **System Action**: `getNextStep` updates the confirmation state and resumes the workflow to the next stage.
7.  **Update External Systems (Vend/PPA)**
    *   **System Action**: The system automatically calls internal integrations to update the Vend/PPA platforms with the final decision and status.
    *   **Outcome**: Review is finalized in the core records.
8.  **Completion**
    *   **System Action**: Logs an audit trail of the completed workflow.
    *   **Status**: Workflow successfully finished.
## Summary of Inputs & APIs
| Stage | Input Required | API Used to Resume |
| :--- | :--- | :--- |
| **Start** | N/A (System Trigger) | `startPPAreview` |
| **User Assignment** | Assigned User ID | `getNextStep` |
| **Review Type** | Review Scope/Type | `getNextStep` |
| **Loan Decision** | Decision & Attributes | `getNextStep` |
| **Reclass Confirmation** | Confirmation Signal | `getNextStep` |