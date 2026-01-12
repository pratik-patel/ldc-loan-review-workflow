#!/usr/bin/env python3
"""
Lambda function to set up the PostgreSQL database schema
This can be invoked to create the schema in the RDS instance
"""

import json
import os
import sys
import subprocess

# Install psycopg2 if needed
try:
    import psycopg2
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "psycopg2-binary", "-q"])
    import psycopg2

def lambda_handler(event, context):
    """Lambda handler to set up database schema"""
    
    # Get database credentials from environment
    db_host = os.getenv('DB_HOST')
    db_port = os.getenv('DB_PORT', '5432')
    db_name = os.getenv('DB_NAME', 'ldc_loan_review')
    db_user = os.getenv('DB_USER', 'postgres')
    db_password = os.getenv('DB_PASSWORD')
    
    if not all([db_host, db_password]):
        return {
            'statusCode': 400,
            'body': json.dumps('Missing required environment variables: DB_HOST, DB_PASSWORD')
        }
    
    # Read schema from environment or file
    schema_sql = os.getenv('SCHEMA_SQL')
    if not schema_sql:
        try:
            with open('/tmp/schema.sql', 'r') as f:
                schema_sql = f.read()
        except:
            return {
                'statusCode': 400,
                'body': json.dumps('Schema SQL not provided')
            }
    
    try:
        # Connect to database
        conn = psycopg2.connect(
            host=db_host,
            port=db_port,
            database=db_name,
            user=db_user,
            password=db_password
        )
        
        cursor = conn.cursor()
        
        # Execute schema
        statements = schema_sql.split(';')
        for statement in statements:
            statement = statement.strip()
            if statement:
                cursor.execute(statement)
        
        conn.commit()
        
        # Verify tables
        cursor.execute("""
            SELECT table_name FROM information_schema.tables 
            WHERE table_schema = 'public' 
            AND table_name IN ('workflow_state', 'audit_trail')
        """)
        
        tables = [row[0] for row in cursor.fetchall()]
        cursor.close()
        conn.close()
        
        return {
            'statusCode': 200,
            'body': json.dumps({
                'message': 'Database schema created successfully',
                'tables': tables
            })
        }
    
    except Exception as e:
        return {
            'statusCode': 500,
            'body': json.dumps(f'Error: {str(e)}')
        }

if __name__ == '__main__':
    # For local testing
    os.environ['DB_HOST'] = 'ldc-loan-review-db-dev.c5ce0uw0qqgm.us-east-1.rds.amazonaws.com'
    os.environ['DB_PASSWORD'] = 'postgres_password_123'
    
    with open('schema.sql', 'r') as f:
        os.environ['SCHEMA_SQL'] = f.read()
    
    result = lambda_handler({}, None)
    print(json.dumps(result, indent=2))
