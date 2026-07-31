ALTER TABLE gpt_accounts RENAME COLUMN access_token_ciphertext TO access_token;

COMMENT ON COLUMN gpt_accounts.access_token IS 'Plaintext ChatGPT access token';
