terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Read the Step Functions definition
locals {
  state_machine_definition = templatefile("${path.module}/definition.asl.json", {
    reclass_timer_seconds = var.reclass_timer_seconds
    lambda_function_name  = var.lambda_function_name
  })
}

# Step Functions State Machine
resource "aws_sfn_state_machine" "loan_review_workflow" {
  name       = var.state_machine_name
  role_arn   = var.state_machine_role_arn
  definition = local.state_machine_definition

  tags = {
    Name        = var.state_machine_name
    Environment = var.environment
  }

  depends_on = [
    var.lambda_functions_ready
  ]
}

# Outputs
output "state_machine_arn" {
  value       = aws_sfn_state_machine.loan_review_workflow.arn
  description = "ARN of the Step Functions state machine"
}

output "state_machine_name" {
  value       = aws_sfn_state_machine.loan_review_workflow.name
  description = "Name of the Step Functions state machine"
}


