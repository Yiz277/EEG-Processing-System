CREATE DATABASE IF NOT EXISTS public_generation CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE public_generation;

-- Create Warehouses
CREATE TABLE IF NOT EXISTS warehouses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    source_path VARCHAR(255) NOT NULL,
    status ENUM('PENDING', 'LOADED', 'IN_PROGRESS', 'COMPLETED', 'FAILED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create Task Files
CREATE TABLE IF NOT EXISTS task_files (
    id INT AUTO_INCREMENT PRIMARY KEY,
    original_file_id INT NOT NULL,
    warehouse_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    status ENUM('PENDING', 'Archive', 'NoArEDF', 'Process', 'ArEDF', 'NoArCSV', 'ArCSV', 'Compression', 'COMPLETED', 'FAILED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_file_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

-- Create Tasks
CREATE TABLE IF NOT EXISTS tasks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    file_id INT NOT NULL ,
    name VARCHAR(255) NOT NULL,
    task_name ENUM('Archive', 'NoArEDF', 'Process', 'ArEDF', 'NoArCSV', 'ArCSV', 'Compression') NOT NULL,
    consumer_id INT,
    source_path VARCHAR(255) NOT NULL,
    status ENUM('PENDING', 'LOADED', 'IN_PROGRESS', 'COMPLETED', 'FAILED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_task_file FOREIGN KEY (file_id) REFERENCES task_files(id)
);