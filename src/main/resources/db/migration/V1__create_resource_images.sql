CREATE TABLE IF NOT EXISTS resource_images (
    resource_id  BIGINT PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL,
    image_data   BYTEA NOT NULL
);
