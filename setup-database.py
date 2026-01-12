#!/usr/bin/env python3
"""
PostgreSQL Database Setup Script
Creates the schema for the LDC Loan Review Workflow
"""

import sys
import os
import subprocess

# Try to import psycopg2
try:
    import psycopg2
    from psycopg2 import sql
except ImportError:
    print("psycopg2 not found. Installing...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "psycopg2-binary"])
    import psycopg2
    from psycopg2 import sql

def get_db_credentials():
    """Get database credentials from environment or Terraform"""
    db_host = os.getenv('DB_HOST', 'ldc-loan-review-db-dev.c5ce0uw0qqgm.us-east-1.rds.amazonaws.com')
    db_port = os.getenv('DB_PORT', '5432')
    db_name = os.getenv('DB_NAME', 'ldc_loan_review')
    db_user = os.getenv('DB_USER', 'postgres')
    db_password = os.getenv('DB_PASSWORD')
    
    if not db_password:
        print("Error: DB_PASSWORD environment variable not set")
        print("Please set DB_PASSWORD before running this script")
        sys.exit(1)
    
    return {
        'host': db_host,
        'port': db_port,
        'database': db_name,
        'user': db_user,
        'password': db_password
    }

def read_schema_file():
    """Read the schema.sql file"""
    schema_path = os.path.join(os.path.dirname(__file__), 'schema.sql')
    if not os.path.exists(schema_path):
        print(f"Error: schema.sql not found at {schema_path}")
        sys.exit(1)
    
    with open(schema_path, 'r') as f:
        return f.read()

def execute_schema(conn, schema_sql):
    """Execute the schema SQL"""
    cursor = conn.cursor()
    try:
        # Execute the entire schema as one statement to handle PL/pgSQL functions
        print(f"Executing schema...")
        cursor.execute(schema_sql)
        
        conn.commit()
        print("✓ Schema created successfully")
        
        # Verify tables were created
        cursor.execute("""
            SELECT table_name FROM information_schema.tables 
            WHERE table_schema = 'public' 
            AND table_name IN ('workflow_state', 'audit_trail')
            ORDER BY table_name
        """)
        
        tables = cursor.fetchall()
        print(f"\n✓ Tables created: {len(tables)}")
        for table in tables:
            print(f"  - {table[0]}")
        
        # Get table row counts
        cursor.execute("SELECT COUNT(*) FROM workflow_state")
        ws_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM audit_trail")
        at_count = cursor.fetchone()[0]
        
        print(f"\nTable row counts:")
        print(f"  - workflow_state: {ws_count} rows")
        print(f"  - audit_trail: {at_count} rows")
        
    except Exception as e:
        print(f"Error executing schema: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def main():
    print("=" * 60)
    print("LDC Loan Review Workflow - Database Setup")
    print("=" * 60)
    print()
    
    # Get credentials
    print("Getting database credentials...")
    creds = get_db_credentials()
    print(f"  Host: {creds['host']}")
    print(f"  Port: {creds['port']}")
    print(f"  Database: {creds['database']}")
    print(f"  User: {creds['user']}")
    print()
    
    # Read schema
    print("Reading schema.sql...")
    schema_sql = read_schema_file()
    print(f"  Schema size: {len(schema_sql)} bytes")
    print()
    
    # Connect to database
    print("Connecting to database...")
    try:
        conn = psycopg2.connect(
            host=creds['host'],
            port=creds['port'],
            database=creds['database'],
            user=creds['user'],
            password=creds['password']
        )
        print("✓ Connected successfully")
        print()
    except Exception as e:
        print(f"✗ Connection failed: {e}")
        sys.exit(1)
    
    # Execute schema
    print("Creating schema...")
    try:
        execute_schema(conn, schema_sql)
    finally:
        conn.close()
    
    print()
    print("=" * 60)
    print("Database setup complete!")
    print("=" * 60)

if __name__ == '__main__':
    main()
