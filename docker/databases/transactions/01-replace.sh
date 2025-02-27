#!/bin/bash

# Navigate to docker entrypoint directory.
cd docker-entrypoint-initdb.d || exit

# Loop through all .sql files in the current directory.
for sql_file in *.sql; do
  # Check if the glob gets expanded to existing files.
  if [ -e "$sql_file" ]; then
    # Use sed to replace parts with environment variables.
    sed -i "s|DB_NAME|$DB_NAME|g; s|DB_USER|$DB_USER|g; s|DB_PASS|$DB_PASS|g" "$sql_file"

    echo "$sql_file has been processed."
  else
    echo "No .sql files found."
    break
  fi
done

echo "All sql files have been processed."