-- Migration script to add new fields to submissions table
-- Run this if you have existing database

-- Add new columns if they don't exist
ALTER TABLE submissions
ADD COLUMN IF NOT EXISTS language_id INTEGER,
ADD COLUMN IF NOT EXISTS passed_tests INTEGER,
ADD COLUMN IF NOT EXISTS total_tests INTEGER,
ADD COLUMN IF NOT EXISTS execution_details VARCHAR(4000);

-- Update language_id to NOT NULL after setting default values
UPDATE submissions SET language_id = 62 WHERE language_id IS NULL; -- Default to Java
ALTER TABLE submissions ALTER COLUMN language_id SET NOT NULL;

-- Add indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_submissions_user_id ON submissions(user_id);
CREATE INDEX IF NOT EXISTS idx_submissions_task_id ON submissions(task_id);
CREATE INDEX IF NOT EXISTS idx_submissions_created_at ON submissions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_submissions_status ON submissions(status);
CREATE INDEX IF NOT EXISTS idx_submissions_user_task ON submissions(user_id, task_id);
