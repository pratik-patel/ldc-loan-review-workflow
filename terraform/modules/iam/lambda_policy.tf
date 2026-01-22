
# Lambda permission to execute Step Functions
resource "aws_iam_role_policy" "lambda_step_functions" {
  name = "lambda-step-functions"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "states:StartExecution",
          "states:ListExecutions",
          "states:DescribeExecution",
          "states:SendTaskSuccess",
          "states:SendTaskFailure"
        ]
        Resource = "*"
      }
    ]
  })
}
