-- Create inventory database user and database
DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'inventoryuser') THEN
      CREATE ROLE inventoryuser WITH LOGIN PASSWORD 'dev123';
   END IF;
END
$$;

DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'inventorydb') THEN
      CREATE DATABASE inventorydb WITH OWNER = inventoryuser;
   END IF;
END
$$;

-- Create order database user and database
DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'orderuser') THEN
      CREATE ROLE orderuser WITH LOGIN PASSWORD 'dev123';
   END IF;
END
$$;

DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'orderdb') THEN
      CREATE DATABASE orderdb WITH OWNER = orderuser;
   END IF;
END
$$;

-- Create schemas
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS orders;
