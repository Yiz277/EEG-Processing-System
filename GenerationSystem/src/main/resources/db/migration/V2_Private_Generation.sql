CREATE DATABASE IF NOT EXISTS private_generation CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE private_generation;

-- Create local_warehouses
CREATE TABLE IF NOT EXISTS local_warehouses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id INT NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    source_path VARCHAR(255) NOT NULL,
    local_path VARCHAR(255) NOT NULL,
    download_path VARCHAR(255) NOT NULL,
    status ENUM('PENDING', 'LOADED', 'IN_PROGRESS', 'COMPLETED', 'FAILED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create original_files
CREATE TABLE IF NOT EXISTS original_files (
    id INT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id INT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    source_path VARCHAR(255) NOT NULL,
    local_path VARCHAR(255) NOT NULL,
    download_path VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (warehouse_id) REFERENCES local_warehouses(warehouse_id) ON DELETE CASCADE
);
